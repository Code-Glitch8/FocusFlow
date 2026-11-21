package com.example.domain

import com.example.data.local.ChapterEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.SessionChapterEntity
import com.example.data.local.SessionTaskEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.local.SubjectEntity
import com.example.model.ChapterProgressItem
import com.example.model.SessionStatus
import com.example.model.StudyStats
import com.example.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

object StudyStatsCalculator {

    fun compute(
        sessions: List<FocusSessionEntity>,
        sessionChapters: List<SessionChapterEntity>,
        sessionTasks: List<SessionTaskEntity>,
        standaloneTasks: List<StudyTaskEntity>,
        subjects: List<SubjectEntity>,
        chapters: List<ChapterEntity> = emptyList(),
        nowMs: Long
    ): StudyStats {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val weekStartDate = today.minusDays(6L)

        var totalFocusMs = 0L
        var todayFocusMs = 0L
        var last7DaysMs = 0L
        val minutesByDate = hashMapOf<LocalDate, Long>()
        val minutesBySubjectId = hashMapOf<Long, Long>()
        val totalChapterMs = hashMapOf<Long, Long>()
        val weekChapterMs = hashMapOf<Long, Long>()

        val completedOrActive = sessions.filter {
            it.status == SessionStatus.COMPLETED || it.status == SessionStatus.ACTIVE
        }

        for (session in completedOrActive) {
            val snap = SessionClock.computeSnapshot(session, nowMs)
            val focusMs = snap.focusElapsedMs
            if (focusMs <= 0L) continue
            totalFocusMs += focusMs

            val sessionDate = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
            minutesByDate[sessionDate] = (minutesByDate[sessionDate] ?: 0L) + focusMs
            if (sessionDate == today) {
                todayFocusMs += focusMs
            }
            val inLast7Days = !sessionDate.isBefore(weekStartDate)
            if (inLast7Days) {
                last7DaysMs += focusMs
            }

            val linkedRows = sessionChapters.filter { it.sessionId == session.id }
            val linkedSubjects = linkedRows.map { it.subjectId }.distinct()
            if (linkedSubjects.isNotEmpty()) {
                val share = focusMs / linkedSubjects.size
                for (subjId in linkedSubjects) {
                    minutesBySubjectId[subjId] = (minutesBySubjectId[subjId] ?: 0L) + share
                }
            }

            val linkedChapters = linkedRows.mapNotNull { it.chapterId }.distinct()
            if (linkedChapters.isNotEmpty()) {
                val chShare = focusMs / linkedChapters.size
                for (chId in linkedChapters) {
                    totalChapterMs[chId] = (totalChapterMs[chId] ?: 0L) + chShare
                    if (inLast7Days) {
                        weekChapterMs[chId] = (weekChapterMs[chId] ?: 0L) + chShare
                    }
                }
            }
        }

        val doneSessionTasks = sessionTasks.filter { it.completed }
        val doneStandaloneTasks = standaloneTasks.filter { it.status == TaskStatus.DONE }
        val openStandaloneTasks = standaloneTasks.filter { it.status == TaskStatus.PENDING }
        val openSessionTasks = sessionTasks.filter { !it.completed }
        val totalCompletedTasks = doneSessionTasks.size + doneStandaloneTasks.size
        val totalOpenTasks = openStandaloneTasks.size

        val todayCompletedSessionTasks = doneSessionTasks.count { task ->
            val session = sessions.find { it.id == task.sessionId }
            val ref = session?.startedAt ?: nowMs
            Instant.ofEpochMilli(ref).atZone(zone).toLocalDate() == today
        }
        val todayCompletedStandalone = doneStandaloneTasks.count { task ->
            val ref = task.completedAt ?: task.createdAt
            Instant.ofEpochMilli(ref).atZone(zone).toLocalDate() == today
        }
        val todayCompletedTasks = todayCompletedSessionTasks + todayCompletedStandalone

        var streak = 0
        var cursor = today
        if ((minutesByDate[today] ?: 0L) >= 60_000L) {
            while ((minutesByDate[cursor] ?: 0L) >= 60_000L) {
                streak++
                cursor = cursor.minusDays(1)
            }
        } else {
            var yesterday = today.minusDays(1)
            while ((minutesByDate[yesterday] ?: 0L) >= 60_000L) {
                streak++
                yesterday = yesterday.minusDays(1)
            }
        }

        val dailyMinutes = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val mins = ((minutesByDate[date] ?: 0L) / 60_000L).toInt()
            label to mins
        }

        val weeklyMinutes = (3 downTo 0).map { weekOffset ->
            val end = today.minusDays(weekOffset * 7L)
            val start = end.minusDays(6L)
            var sumMs = 0L
            var d = start
            while (!d.isAfter(end)) {
                sumMs += (minutesByDate[d] ?: 0L)
                d = d.plusDays(1)
            }
            val label = if (weekOffset == 0) "This wk" else "W-${weekOffset}"
            label to (sumMs / 60_000L).toInt()
        }

        val monthlyMinutes = (5 downTo 0).map { bucket ->
            val end = today.minusDays(bucket * 5L)
            val start = end.minusDays(4L)
            var sumMs = 0L
            var d = start
            while (!d.isAfter(end)) {
                sumMs += (minutesByDate[d] ?: 0L)
                d = d.plusDays(1)
            }
            val label = "${start.dayOfMonth}/${start.monthValue}"
            label to (sumMs / 60_000L).toInt()
        }

        val subjectBreakdown = minutesBySubjectId.entries
            .mapNotNull { (subjId, ms) ->
                val name = subjects.find { it.id == subjId }?.name ?: return@mapNotNull null
                val mins = max(1, (ms / 60_000L).toInt())
                name to mins
            }
            .sortedByDescending { it.second }

        val subjectMap = subjects.associateBy { it.id }
        val allChapterProgress = chapters.mapNotNull { ch ->
            val weekMins = ((weekChapterMs[ch.id] ?: 0L) / 60_000L).toInt()
            val totalMins = ((totalChapterMs[ch.id] ?: 0L) / 60_000L).toInt()
            val doneCount = doneSessionTasks.count { it.chapterId == ch.id } +
                doneStandaloneTasks.count { it.chapterId == ch.id }
            val openCount = openSessionTasks.count { it.chapterId == ch.id } +
                openStandaloneTasks.count { it.chapterId == ch.id }
            if (weekMins == 0 && totalMins == 0 && doneCount == 0 && openCount == 0) {
                null
            } else {
                ChapterProgressItem(
                    chapterId = ch.id,
                    subjectId = ch.subjectId,
                    chapterName = ch.name,
                    subjectName = subjectMap[ch.subjectId]?.name ?: "Study",
                    weekMinutes = weekMins,
                    totalMinutes = totalMins,
                    doneTasks = doneCount,
                    openTasks = openCount
                )
            }
        }.sortedWith(compareByDescending<ChapterProgressItem> { it.weekMinutes }.thenByDescending { it.doneTasks + it.openTasks })

        val chaptersThisWeek = allChapterProgress.take(4)

        return StudyStats(
            totalFocusMinutes = (totalFocusMs / 60_000L).toInt(),
            todayFocusMinutes = (todayFocusMs / 60_000L).toInt(),
            last7DaysMinutes = (last7DaysMs / 60_000L).toInt(),
            totalCompletedTasks = totalCompletedTasks,
            totalOpenTasks = totalOpenTasks,
            todayCompletedTasks = todayCompletedTasks,
            streakDays = streak,
            totalSessions = sessions.count { it.status == SessionStatus.COMPLETED },
            dailyMinutes = dailyMinutes,
            weeklyMinutes = weeklyMinutes,
            monthlyMinutes = monthlyMinutes,
            subjectBreakdown = subjectBreakdown,
            chaptersThisWeek = chaptersThisWeek,
            allChapterProgress = allChapterProgress
        )
    }
}
