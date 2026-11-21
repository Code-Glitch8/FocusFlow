package com.example.domain

import com.example.data.local.FocusSessionEntity
import com.example.model.SessionStatus
import com.example.model.SessionTimeSnapshot
import kotlin.math.max
import kotlin.math.min

object SessionClock {

    fun computeSnapshot(session: FocusSessionEntity, nowMs: Long): SessionTimeSnapshot {
        val refTime = if (session.status != SessionStatus.ACTIVE) {
            session.endedAt ?: nowMs
        } else {
            nowMs
        }

        val breakStarted = session.breakStartedAt
        val breakEnds = session.breakEndsAt

        val currentBreakElapsed = if (breakStarted != null && breakEnds != null) {
            max(0L, min(refTime, breakEnds) - breakStarted)
        } else {
            0L
        }

        val breakElapsedMs = session.breakMsCompleted + currentBreakElapsed
        val isOnBreak = session.status == SessionStatus.ACTIVE &&
            breakStarted != null &&
            breakEnds != null &&
            nowMs < breakEnds

        val breakRemainingMs = if (isOnBreak && breakEnds != null) {
            max(0L, breakEnds - nowMs)
        } else {
            0L
        }

        val focusElapsedMs = max(0L, refTime - session.startedAt - breakElapsedMs)
        val totalPlannedMs = session.plannedDurationMinutes?.let { it * 60_000L }
        val remainingMs = totalPlannedMs?.let { max(0L, it - focusElapsedMs) }
        val isTimedFinished = session.status == SessionStatus.ACTIVE &&
            remainingMs != null &&
            remainingMs <= 0L

        return SessionTimeSnapshot(
            focusElapsedMs = focusElapsedMs,
            breakElapsedMs = breakElapsedMs,
            totalPlannedMs = totalPlannedMs,
            remainingMs = remainingMs,
            isOnBreak = isOnBreak,
            breakRemainingMs = breakRemainingMs,
            isTimedFinished = isTimedFinished
        )
    }

    fun computeExactEndedAt(session: FocusSessionEntity, nowMs: Long): Long {
        val snapshot = computeSnapshot(session, nowMs)
        val totalPlannedMs = snapshot.totalPlannedMs ?: return nowMs
        val overshoot = max(0L, snapshot.focusElapsedMs - totalPlannedMs)
        return max(session.startedAt, nowMs - overshoot)
    }

    fun formatClock(ms: Long): String {
        val totalSeconds = max(0L, ms / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun formatMinutesCompact(minutes: Int): String {
        val safe = max(0, minutes)
        val hrs = safe / 60
        val mins = safe % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 -> "${hrs}h"
            else -> "${mins}m"
        }
    }
}
