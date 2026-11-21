package com.example.data

import com.example.data.local.AllowedAppEntity
import com.example.data.local.BlockedAppEntity
import com.example.data.local.BreakConfigEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.CustomCatalogAppEntity
import com.example.data.local.DashboardWidgetEntity
import com.example.data.local.FocusFlowDao
import com.example.data.local.FocusSessionEntity
import com.example.data.local.PresetChapterEntity
import com.example.data.local.PresetEntity
import com.example.data.local.PresetTaskEntity
import com.example.data.local.SessionChapterEntity
import com.example.data.local.SessionSummaryEntity
import com.example.data.local.SessionTaskEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.StudyTaskEntity
import com.example.data.local.SubjectEntity
import com.example.data.local.SubtaskEntity
import com.example.domain.EarnedBreakCalculator
import com.example.domain.SessionClock
import com.example.model.AiProviderProfile
import com.example.model.AppCategory
import com.example.model.AppSettings
import com.example.model.DraftStudyTask
import com.example.model.FocusMode
import com.example.model.ProgressRange
import com.example.model.SessionStatus
import com.example.model.ShortsMode
import com.example.model.SubjectPlan
import com.example.model.TaskOrigin
import com.example.model.TaskStatus
import com.example.model.ThemeMode
import com.example.model.WidgetAccent
import com.example.model.WidgetAction
import com.example.model.WidgetIcon
import com.example.model.WidgetSize
import com.example.model.WidgetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class FocusFlowRepository(
    private val dao: FocusFlowDao,
    private val settingsStore: SettingsDataStore
) {
    val subjectsFlow: Flow<List<SubjectEntity>> = dao.observeSubjects()
    val chaptersFlow: Flow<List<ChapterEntity>> = dao.observeChapters()
    val studyTasksFlow: Flow<List<StudyTaskEntity>> = dao.observeStudyTasks()
    val subtasksFlow: Flow<List<SubtaskEntity>> = dao.observeSubtasks()
    val sessionsFlow: Flow<List<FocusSessionEntity>> = dao.observeSessions()
    val sessionChaptersFlow: Flow<List<SessionChapterEntity>> = dao.observeSessionChapters()
    val sessionTasksFlow: Flow<List<SessionTaskEntity>> = dao.observeSessionTasks()
    val presetsFlow: Flow<List<PresetEntity>> = dao.observePresets()
    val presetChaptersFlow: Flow<List<PresetChapterEntity>> = dao.observePresetChapters()
    val presetTasksFlow: Flow<List<PresetTaskEntity>> = dao.observePresetTasks()
    val blockedAppsFlow: Flow<List<BlockedAppEntity>> = dao.observeBlockedApps()
    val allowedAppsFlow: Flow<List<AllowedAppEntity>> = dao.observeAllowedApps()
    val breakConfigFlow: Flow<BreakConfigEntity> = dao.observeBreakConfig().map { it ?: SeedData.defaultBreakConfig() }
    val summariesFlow: Flow<List<SessionSummaryEntity>> = dao.observeSummaries()
    val widgetsFlow: Flow<List<DashboardWidgetEntity>> = dao.observeWidgets()
    val customCatalogAppsFlow: Flow<List<CustomCatalogAppEntity>> = dao.observeCustomCatalogApps()

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow
    val providersFlow: Flow<List<AiProviderProfile>> = settingsStore.providersFlow

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val currentSettings = settingsStore.getSettingsOnce()
        val existingSubjects = dao.getSubjectsOnce()

        if (currentSettings.seedVersion < SeedData.CURRENT_SEED_VERSION || existingSubjects.isEmpty()) {
            // Seed subjects
            val existingByName = existingSubjects.associateBy { it.name.lowercase() }.toMutableMap()
            SeedData.BUILT_IN_SUBJECTS.forEachIndexed { idx, name ->
                if (!existingByName.containsKey(name.lowercase())) {
                    val id = dao.insertSubject(SubjectEntity(name = name, builtIn = true, sortOrder = idx))
                    existingByName[name.lowercase()] = SubjectEntity(id = id, name = name, builtIn = true, sortOrder = idx)
                }
            }

            // Seed Class 11 chapters
            val existingChapters = dao.getChaptersOnce()
            SeedData.CLASS_11_CHAPTERS.forEach { (subjName, chapterNames) ->
                val subj = existingByName[subjName.lowercase()] ?: return@forEach
                val subjExisting = existingChapters
                    .filter { it.subjectId == subj.id }
                    .map { it.name.lowercase() }
                    .toSet()
                chapterNames.forEachIndexed { idx, chName ->
                    if (chName.lowercase() !in subjExisting) {
                        dao.insertChapter(ChapterEntity(subjectId = subj.id, name = chName, sortOrder = idx))
                    }
                }
            }

            // Break config
            if (dao.getBreakConfigOnce() == null) {
                dao.insertBreakConfig(SeedData.defaultBreakConfig())
            }

            // Default widgets
            if (dao.getWidgetsOnce().isEmpty()) {
                val seededSubjects = dao.getSubjectsOnce()
                dao.insertWidgets(SeedData.defaultWidgets(seededSubjects))
            }

            settingsStore.updateSettings { it.copy(seedVersion = SeedData.CURRENT_SEED_VERSION) }
        } else if (dao.getBreakConfigOnce() == null) {
            dao.insertBreakConfig(SeedData.defaultBreakConfig())
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) =
        settingsStore.updateSettings(transform)

    suspend fun saveProviders(providers: List<AiProviderProfile>) =
        settingsStore.saveProviders(providers)

    // Subjects & Chapters
    suspend fun addSubject(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        val existing = dao.getSubjectsOnce()
        val maxSort = existing.maxOfOrNull { it.sortOrder } ?: -1
        return dao.insertSubject(SubjectEntity(name = clean, builtIn = false, sortOrder = maxSort + 1))
    }

    suspend fun renameSubject(subject: SubjectEntity, newName: String) {
        if (subject.builtIn) return
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            dao.updateSubject(subject.copy(name = clean))
        }
    }

    suspend fun deleteCustomSubject(subject: SubjectEntity) {
        if (subject.builtIn) return
        dao.deleteChaptersForSubject(subject.id)
        dao.deleteCustomSubject(subject.id)
    }

    suspend fun addChapter(subjectId: Long, name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        val existing = dao.getChaptersOnce().filter { it.subjectId == subjectId }
        val maxSort = existing.maxOfOrNull { it.sortOrder } ?: -1
        return dao.insertChapter(ChapterEntity(subjectId = subjectId, name = clean, sortOrder = maxSort + 1))
    }

    suspend fun renameChapter(chapter: ChapterEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            dao.updateChapter(chapter.copy(name = clean))
        }
    }

    suspend fun deleteChapter(chapterId: Long) = dao.deleteChapter(chapterId)

    // Study Tasks & Subtasks
    suspend fun saveStudyTask(
        existingId: Long? = null,
        title: String,
        subjectId: Long?,
        chapterId: Long?,
        estimatedMinutes: Int,
        origin: TaskOrigin = TaskOrigin.MANUAL,
        subtaskTitles: List<String> = emptyList()
    ): Long {
        val clean = title.trim()
        if (clean.isEmpty()) return -1L
        val taskId = if (existingId != null && existingId > 0L) {
            val current = dao.getStudyTasksOnce().find { it.id == existingId }
            val updated = (current ?: StudyTaskEntity(id = existingId, title = clean)).copy(
                title = clean,
                subjectId = subjectId,
                chapterId = chapterId,
                estimatedMinutes = estimatedMinutes.coerceIn(5, 360)
            )
            dao.updateStudyTask(updated)
            dao.deleteSubtasksForTask(existingId)
            existingId
        } else {
            dao.insertStudyTask(
                StudyTaskEntity(
                    title = clean,
                    subjectId = subjectId,
                    chapterId = chapterId,
                    estimatedMinutes = estimatedMinutes.coerceIn(5, 360),
                    status = TaskStatus.PENDING,
                    origin = origin,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        val cleanSubtasks = subtaskTitles.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanSubtasks.isNotEmpty()) {
            dao.insertSubtasks(
                cleanSubtasks.mapIndexed { idx, sTitle ->
                    SubtaskEntity(taskId = taskId, title = sTitle, done = false, sortOrder = idx)
                }
            )
        }
        return taskId
    }

    suspend fun toggleStudyTaskDone(task: StudyTaskEntity) {
        val nextStatus = if (task.status == TaskStatus.DONE) TaskStatus.PENDING else TaskStatus.DONE
        dao.updateStudyTask(
            task.copy(
                status = nextStatus,
                completedAt = if (nextStatus == TaskStatus.DONE) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun deleteStudyTask(taskId: Long) {
        dao.deleteSubtasksForTask(taskId)
        dao.deleteStudyTask(taskId)
    }

    suspend fun toggleSubtaskDone(subtask: SubtaskEntity) {
        dao.updateSubtask(subtask.copy(done = !subtask.done))
    }

    // Widgets
    suspend fun saveWidget(widget: DashboardWidgetEntity): Long {
        return if (widget.id > 0L) {
            dao.updateWidget(widget)
            widget.id
        } else {
            val nextOrder = (dao.getWidgetsOnce().maxOfOrNull { it.sortOrder } ?: -1) + 1
            dao.insertWidget(widget.copy(sortOrder = nextOrder))
        }
    }

    suspend fun deleteWidget(widgetId: Long) = dao.deleteWidget(widgetId)

    suspend fun moveWidget(widgetId: Long, delta: Int) {
        val list = dao.getWidgetsOnce().sortedBy { it.sortOrder }.toMutableList()
        val idx = list.indexOfFirst { it.id == widgetId }
        if (idx < 0) return
        val target = idx + delta
        if (target !in list.indices) return
        val item = list.removeAt(idx)
        list.add(target, item)
        list.forEachIndexed { index, w ->
            dao.updateWidget(w.copy(sortOrder = index))
        }
    }

    suspend fun resetDefaultWidgets() {
        dao.clearWidgets()
        val subjects = dao.getSubjectsOnce()
        dao.insertWidgets(SeedData.defaultWidgets(subjects))
    }

    // Break Config
    suspend fun saveBreakConfig(config: BreakConfigEntity) {
        dao.insertBreakConfig(config.copy(id = 1))
    }

    // Blocked & Allowed Apps
    suspend fun toggleBlockedApp(packageName: String, label: String, category: AppCategory, block: Boolean) {
        if (block) {
            dao.deleteAllowedApp(packageName)
            dao.insertBlockedApp(BlockedAppEntity(packageName, label, category, blockedDuringBreak = false))
        } else {
            dao.deleteBlockedApp(packageName)
        }
    }

    suspend fun setBlockedDuringBreak(packageName: String, blockedDuringBreak: Boolean) {
        val existing = dao.getBlockedAppsOnce().find { it.packageName == packageName } ?: return
        dao.insertBlockedApp(existing.copy(blockedDuringBreak = blockedDuringBreak))
    }

    suspend fun toggleAllowedApp(packageName: String, label: String, category: AppCategory, allow: Boolean) {
        if (allow) {
            dao.deleteBlockedApp(packageName)
            dao.insertAllowedApp(AllowedAppEntity(packageName, label, category))
        } else {
            dao.deleteAllowedApp(packageName)
        }
    }

    suspend fun addCustomApp(packageName: String, label: String, category: AppCategory, addToBlocked: Boolean) {
        val pkg = packageName.trim()
        val name = label.trim().ifEmpty { pkg }
        if (pkg.isEmpty()) return
        dao.insertCustomCatalogApp(CustomCatalogAppEntity(pkg, name, category))
        if (addToBlocked) {
            toggleBlockedApp(pkg, name, category, block = true)
        } else {
            toggleAllowedApp(pkg, name, category, allow = true)
        }
    }

    // Sessions
    suspend fun startSession(
        label: String,
        mode: FocusMode,
        plannedDurationMinutes: Int?,
        strictMode: Boolean,
        rawStudyPlan: String,
        subjectPlans: List<SubjectPlan>,
        draftTasks: List<DraftStudyTask>,
        selectedStudyTaskIds: List<Long>,
        widgetId: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        // End any stale active session cleanly first
        dao.getSessionsOnce().filter { it.status == SessionStatus.ACTIVE }.forEach { active ->
            completeSession(active.id, now, abandoned = false)
        }

        val sessionId = dao.insertSession(
            FocusSessionEntity(
                label = label.ifBlank { "Focus session" },
                mode = mode,
                status = SessionStatus.ACTIVE,
                plannedDurationMinutes = if (mode == FocusMode.TIMED) (plannedDurationMinutes ?: 25).coerceAtLeast(1) else null,
                startedAt = now,
                strictMode = strictMode,
                rawStudyPlan = rawStudyPlan,
                widgetId = widgetId
            )
        )

        val chapterEntries = mutableListOf<SessionChapterEntity>()
        subjectPlans.forEach { sp ->
            if (sp.chapterIds.isEmpty()) {
                chapterEntries.add(SessionChapterEntity(sessionId = sessionId, subjectId = sp.subjectId, chapterId = null))
            } else {
                sp.chapterIds.forEach { chId ->
                    chapterEntries.add(SessionChapterEntity(sessionId = sessionId, subjectId = sp.subjectId, chapterId = chId))
                }
            }
        }
        if (chapterEntries.isNotEmpty()) {
            dao.insertSessionChapters(chapterEntries)
        }

        val allStudyTasks = dao.getStudyTasksOnce()
        val sessionTasks = mutableListOf<SessionTaskEntity>()
        var order = 0
        for (taskId in selectedStudyTaskIds) {
            val st = allStudyTasks.find { it.id == taskId } ?: continue
            sessionTasks.add(
                SessionTaskEntity(
                    sessionId = sessionId,
                    studyTaskId = st.id,
                    title = st.title,
                    subjectId = st.subjectId,
                    chapterId = st.chapterId,
                    estimatedMinutes = st.estimatedMinutes,
                    completed = false,
                    sortOrder = order++
                )
            )
        }
        for (draft in draftTasks) {
            // Also create a persistent StudyTask if generated via AI/voice or drafted inline
            val linkedId = dao.insertStudyTask(
                StudyTaskEntity(
                    title = draft.title,
                    subjectId = draft.subjectId,
                    chapterId = draft.chapterId,
                    estimatedMinutes = draft.estimatedMinutes,
                    status = TaskStatus.PENDING,
                    origin = draft.origin,
                    createdAt = now
                )
            )
            if (draft.subtasks.isNotEmpty()) {
                dao.insertSubtasks(
                    draft.subtasks.mapIndexed { idx, sub ->
                        SubtaskEntity(taskId = linkedId, title = sub.title, done = false, sortOrder = idx)
                    }
                )
            }
            sessionTasks.add(
                SessionTaskEntity(
                    sessionId = sessionId,
                    studyTaskId = linkedId,
                    title = draft.title,
                    subjectId = draft.subjectId,
                    chapterId = draft.chapterId,
                    estimatedMinutes = draft.estimatedMinutes,
                    completed = false,
                    sortOrder = order++
                )
            )
        }
        if (sessionTasks.isNotEmpty()) {
            dao.insertSessionTasks(sessionTasks)
        }

        return sessionId
    }

    suspend fun startSessionFromWidget(widgetId: Long?): Long = withContext(Dispatchers.IO) {
        ensureSeeded()
        val widgets = dao.getWidgetsOnce()
        val target = widgets.find { it.id == widgetId }
            ?: widgets.firstOrNull { it.type != WidgetType.PROGRESS && it.type != WidgetType.QUOTE }
        val settings = settingsStore.getSettingsOnce()
        val subjects = dao.getSubjectsOnce()

        val label = target?.title ?: "Quick Focus"
        val mode = target?.mode ?: FocusMode.TIMED
        val duration = if (mode == FocusMode.TIMED) (target?.durationMinutes ?: 25) else null
        val strict = target?.strictMode ?: settings.strictDefault
        val subjectPlans = target?.subjectId?.let { sId ->
            val sName = subjects.find { it.id == sId }?.name
            if (sName != null) listOf(SubjectPlan(sId, sName, emptyList(), "")) else emptyList()
        } ?: emptyList()
        val presetTasks = target?.id?.let { id -> settings.widgetPresetTasks[id] }.orEmpty()
        val draftTasks = presetTasks.mapIndexed { idx, wt ->
            DraftStudyTask(
                id = "widget_${target?.id ?: 0}_$idx",
                title = wt.title,
                estimatedMinutes = wt.estimatedMinutes,
                subjectId = target?.subjectId,
                origin = TaskOrigin.MANUAL
            )
        }

        startSession(
            label = label,
            mode = mode,
            plannedDurationMinutes = duration,
            strictMode = strict,
            rawStudyPlan = "",
            subjectPlans = subjectPlans,
            draftTasks = draftTasks,
            selectedStudyTaskIds = emptyList(),
            widgetId = target?.id
        )
    }

    suspend fun toggleSessionTask(task: SessionTaskEntity) {
        val now = System.currentTimeMillis()
        val nextDone = !task.completed
        dao.updateSessionTask(
            task.copy(
                completed = nextDone,
                completedAt = if (nextDone) now else null
            )
        )
        task.studyTaskId?.let { stId ->
            dao.getStudyTasksOnce().find { it.id == stId }?.let { st ->
                dao.updateStudyTask(
                    st.copy(
                        status = if (nextDone) TaskStatus.DONE else TaskStatus.PENDING,
                        completedAt = if (nextDone) now else null
                    )
                )
            }
        }
    }

    suspend fun addTaskToActiveSession(
        sessionId: Long,
        title: String,
        minutes: Int,
        subjectId: Long? = null,
        chapterId: Long? = null
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val existing = dao.getSessionTasksOnce().filter { it.sessionId == sessionId }
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val stId = dao.insertStudyTask(
            StudyTaskEntity(
                title = clean,
                subjectId = subjectId,
                chapterId = chapterId,
                estimatedMinutes = minutes.coerceIn(5, 240),
                status = TaskStatus.PENDING,
                origin = TaskOrigin.MANUAL
            )
        )
        dao.insertSessionTask(
            SessionTaskEntity(
                sessionId = sessionId,
                studyTaskId = stId,
                title = clean,
                subjectId = subjectId,
                chapterId = chapterId,
                estimatedMinutes = minutes.coerceIn(5, 240),
                completed = false,
                sortOrder = nextOrder
            )
        )
    }

    suspend fun deleteSessionTask(taskId: Long) = dao.deleteSessionTask(taskId)

    suspend fun startBreak(sessionId: Long, isLong: Boolean): Boolean {
        val session = dao.getSessionById(sessionId) ?: return false
        if (session.status != SessionStatus.ACTIVE) return false
        val config = dao.getBreakConfigOnce() ?: SeedData.defaultBreakConfig()
        val tasksDone = dao.getSessionTasksOnce().count { it.sessionId == sessionId && it.completed }
        val earned = EarnedBreakCalculator.compute(
            completedTasks = tasksDone,
            shortBreaksUsed = session.shortBreaksUsed,
            longBreaksUsed = session.longBreaksUsed,
            config = config
        )
        if (isLong && earned.longAvailable <= 0) return false
        if (!isLong && earned.shortAvailable <= 0) return false

        val now = System.currentTimeMillis()
        val settledBreakMs = settleBreakMs(session, now)
        val durationMin = if (isLong) config.longBreakMinutes else config.shortBreakMinutes
        dao.updateSession(
            session.copy(
                breakMsCompleted = settledBreakMs,
                breakStartedAt = now,
                breakEndsAt = now + durationMin * 60_000L,
                shortBreaksUsed = if (isLong) session.shortBreaksUsed else session.shortBreaksUsed + 1,
                longBreaksUsed = if (isLong) session.longBreaksUsed + 1 else session.longBreaksUsed
            )
        )
        return true
    }

    suspend fun endBreakEarly(sessionId: Long, nowMs: Long = System.currentTimeMillis()) {
        val session = dao.getSessionById(sessionId) ?: return
        if (session.breakStartedAt == null) return
        val settled = settleBreakMs(session, nowMs)
        dao.updateSession(
            session.copy(
                breakMsCompleted = settled,
                breakStartedAt = null,
                breakEndsAt = null
            )
        )
    }

    suspend fun settleExpiredBreakIfNeeded(sessionId: Long, nowMs: Long = System.currentTimeMillis()) {
        val session = dao.getSessionById(sessionId) ?: return
        val endsAt = session.breakEndsAt ?: return
        if (nowMs >= endsAt) {
            val settled = settleBreakMs(session, nowMs)
            dao.updateSession(
                session.copy(
                    breakMsCompleted = settled,
                    breakStartedAt = null,
                    breakEndsAt = null
                )
            )
        }
    }

    suspend fun updateSessionDuration(sessionId: Long, newMinutes: Int?) {
        val session = dao.getSessionById(sessionId) ?: return
        dao.updateSession(
            session.copy(
                mode = if (newMinutes == null) FocusMode.OPEN_ENDED else FocusMode.TIMED,
                plannedDurationMinutes = newMinutes?.coerceIn(5, 480)
            )
        )
    }

    suspend fun updateSessionDetails(sessionId: Long, label: String, plannedMinutes: Int?) {
        val session = dao.getSessionById(sessionId) ?: return
        val clean = label.trim().ifEmpty { session.label }
        dao.updateSession(
            session.copy(
                label = clean,
                plannedDurationMinutes = plannedMinutes?.coerceIn(1, 720)
            )
        )
    }

    suspend fun completeSession(
        sessionId: Long,
        endedAtMs: Long = System.currentTimeMillis(),
        abandoned: Boolean = false
    ) {
        val session = dao.getSessionById(sessionId) ?: return
        if (session.status != SessionStatus.ACTIVE) return
        val settledBreak = settleBreakMs(session, endedAtMs)
        dao.updateSession(
            session.copy(
                status = if (abandoned) SessionStatus.ABANDONED else SessionStatus.COMPLETED,
                endedAt = max(session.startedAt, endedAtMs),
                breakMsCompleted = settledBreak,
                breakStartedAt = null,
                breakEndsAt = null
            )
        )
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSessionChapters(sessionId)
        dao.deleteSessionTasksForSession(sessionId)
        dao.deleteSummary(sessionId)
        dao.deleteSession(sessionId)
    }

    suspend fun saveSessionSummary(sessionId: Long, text: String, providerUsed: String) {
        dao.insertSummary(
            SessionSummaryEntity(
                sessionId = sessionId,
                summaryText = text,
                providerUsed = providerUsed,
                generatedAt = System.currentTimeMillis()
            )
        )
    }

    // Presets
    suspend fun savePreset(
        name: String,
        mode: FocusMode,
        plannedDurationMinutes: Int?,
        strictMode: Boolean,
        subjectPlans: List<SubjectPlan>,
        draftTasks: List<DraftStudyTask>
    ): Long {
        val clean = name.trim().ifEmpty { "Saved routine" }
        val presetId = dao.insertPreset(
            PresetEntity(
                name = clean,
                mode = mode,
                plannedDurationMinutes = plannedDurationMinutes,
                strictMode = strictMode,
                createdAt = System.currentTimeMillis()
            )
        )
        val chapters = mutableListOf<PresetChapterEntity>()
        subjectPlans.forEach { sp ->
            if (sp.chapterIds.isEmpty()) {
                chapters.add(PresetChapterEntity(presetId = presetId, subjectId = sp.subjectId, chapterId = null))
            } else {
                sp.chapterIds.forEach { chId ->
                    chapters.add(PresetChapterEntity(presetId = presetId, subjectId = sp.subjectId, chapterId = chId))
                }
            }
        }
        if (chapters.isNotEmpty()) dao.insertPresetChapters(chapters)
        if (draftTasks.isNotEmpty()) {
            dao.insertPresetTasks(
                draftTasks.mapIndexed { idx, dt ->
                    PresetTaskEntity(
                        presetId = presetId,
                        title = dt.title,
                        subjectId = dt.subjectId,
                        chapterId = dt.chapterId,
                        estimatedMinutes = dt.estimatedMinutes,
                        sortOrder = idx
                    )
                }
            )
        }
        return presetId
    }

    suspend fun deletePreset(presetId: Long) {
        dao.deletePresetChapters(presetId)
        dao.deletePresetTasks(presetId)
        dao.deletePreset(presetId)
    }

    private fun settleBreakMs(session: FocusSessionEntity, refTime: Long): Long {
        val bStart = session.breakStartedAt ?: return session.breakMsCompleted
        val bEnd = session.breakEndsAt ?: return session.breakMsCompleted
        val delta = max(0L, min(refTime, bEnd) - bStart)
        return session.breakMsCompleted + delta
    }

    // Backup Export / Import (focusflow-database v2)
    suspend fun exportDatabaseJson(): String = withContext(Dispatchers.IO) {
        val settings = settingsStore.getSettingsOnce()
        val data = JSONObject().apply {
            put("subjects", JSONArray().apply {
                dao.getSubjectsOnce().forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id); put("name", s.name); put("builtIn", s.builtIn); put("sortOrder", s.sortOrder)
                    })
                }
            })
            put("chapters", JSONArray().apply {
                dao.getChaptersOnce().forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("subjectId", c.subjectId); put("name", c.name); put("sortOrder", c.sortOrder)
                    })
                }
            })
            put("studyTasks", JSONArray().apply {
                dao.getStudyTasksOnce().forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id); put("title", t.title); put("subjectId", t.subjectId ?: JSONObject.NULL)
                        put("chapterId", t.chapterId ?: JSONObject.NULL); put("estimatedMinutes", t.estimatedMinutes)
                        put("status", t.status.name); put("origin", t.origin.name)
                        put("createdAt", t.createdAt); put("completedAt", t.completedAt ?: JSONObject.NULL)
                    })
                }
            })
            put("subtasks", JSONArray().apply {
                dao.getSubtasksOnce().forEach { st ->
                    put(JSONObject().apply {
                        put("id", st.id); put("taskId", st.taskId); put("title", st.title)
                        put("done", st.done); put("sortOrder", st.sortOrder)
                    })
                }
            })
            put("sessions", JSONArray().apply {
                dao.getSessionsOnce().forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id); put("label", s.label); put("mode", s.mode.name); put("status", s.status.name)
                        put("plannedDurationMinutes", s.plannedDurationMinutes ?: JSONObject.NULL)
                        put("startedAt", s.startedAt); put("endedAt", s.endedAt ?: JSONObject.NULL)
                        put("breakMsCompleted", s.breakMsCompleted); put("shortBreaksUsed", s.shortBreaksUsed)
                        put("longBreaksUsed", s.longBreaksUsed); put("strictMode", s.strictMode)
                        put("rawStudyPlan", s.rawStudyPlan)
                    })
                }
            })
            put("sessionChapters", JSONArray().apply {
                dao.getSessionChaptersOnce().forEach { sc ->
                    put(JSONObject().apply {
                        put("id", sc.id); put("sessionId", sc.sessionId); put("subjectId", sc.subjectId)
                        put("chapterId", sc.chapterId ?: JSONObject.NULL)
                    })
                }
            })
            put("sessionTasks", JSONArray().apply {
                dao.getSessionTasksOnce().forEach { st ->
                    put(JSONObject().apply {
                        put("id", st.id); put("sessionId", st.sessionId)
                        put("studyTaskId", st.studyTaskId ?: JSONObject.NULL)
                        put("title", st.title); put("subjectId", st.subjectId ?: JSONObject.NULL)
                        put("chapterId", st.chapterId ?: JSONObject.NULL)
                        put("estimatedMinutes", st.estimatedMinutes); put("completed", st.completed)
                        put("completedAt", st.completedAt ?: JSONObject.NULL); put("sortOrder", st.sortOrder)
                    })
                }
            })
            put("presets", JSONArray().apply {
                dao.getPresetsOnce().forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id); put("name", p.name); put("mode", p.mode.name)
                        put("plannedDurationMinutes", p.plannedDurationMinutes ?: JSONObject.NULL)
                        put("strictMode", p.strictMode); put("createdAt", p.createdAt)
                    })
                }
            })
            put("presetChapters", JSONArray().apply {
                dao.getPresetChaptersOnce().forEach { pc ->
                    put(JSONObject().apply {
                        put("id", pc.id); put("presetId", pc.presetId); put("subjectId", pc.subjectId)
                        put("chapterId", pc.chapterId ?: JSONObject.NULL)
                    })
                }
            })
            put("presetTasks", JSONArray().apply {
                dao.getPresetTasksOnce().forEach { pt ->
                    put(JSONObject().apply {
                        put("id", pt.id); put("presetId", pt.presetId); put("title", pt.title)
                        put("subjectId", pt.subjectId ?: JSONObject.NULL)
                        put("chapterId", pt.chapterId ?: JSONObject.NULL)
                        put("estimatedMinutes", pt.estimatedMinutes); put("sortOrder", pt.sortOrder)
                    })
                }
            })
            put("blockedApps", JSONArray().apply {
                dao.getBlockedAppsOnce().forEach { a ->
                    put(JSONObject().apply {
                        put("packageName", a.packageName); put("label", a.label)
                        put("category", a.category.name); put("blockedDuringBreak", a.blockedDuringBreak)
                    })
                }
            })
            put("allowedApps", JSONArray().apply {
                dao.getAllowedAppsOnce().forEach { a ->
                    put(JSONObject().apply {
                        put("packageName", a.packageName); put("label", a.label); put("category", a.category.name)
                    })
                }
            })
            val bc = dao.getBreakConfigOnce() ?: SeedData.defaultBreakConfig()
            put("breakConfig", JSONObject().apply {
                put("enabled", bc.enabled); put("tasksForShortBreak", bc.tasksForShortBreak)
                put("shortBreakMinutes", bc.shortBreakMinutes); put("tasksForLongBreak", bc.tasksForLongBreak)
                put("longBreakMinutes", bc.longBreakMinutes)
            })
            put("summaries", JSONArray().apply {
                dao.getSummariesOnce().forEach { sum ->
                    put(JSONObject().apply {
                        put("sessionId", sum.sessionId); put("summaryText", sum.summaryText)
                        put("providerUsed", sum.providerUsed); put("generatedAt", sum.generatedAt)
                    })
                }
            })
            put("widgets", JSONArray().apply {
                dao.getWidgetsOnce().forEach { w ->
                    put(JSONObject().apply {
                        put("id", w.id); put("title", w.title); put("type", w.type.name)
                        put("action", w.action.name); put("icon", w.icon.name); put("accent", w.accent.name)
                        put("size", w.size.name); put("mode", w.mode.name)
                        put("durationMinutes", w.durationMinutes ?: JSONObject.NULL)
                        put("strictMode", w.strictMode); put("subjectId", w.subjectId ?: JSONObject.NULL)
                        put("progressRange", w.progressRange.name); put("sortOrder", w.sortOrder)
                    })
                }
            })
            put("customCatalogApps", JSONArray().apply {
                dao.getCustomCatalogAppsOnce().forEach { a ->
                    put(JSONObject().apply {
                        put("packageName", a.packageName); put("label", a.label); put("category", a.category.name)
                    })
                }
            })
            put("settings", settingsToJson(settings))
        }
        JSONObject().apply {
            put("format", "focusflow-database")
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("data", data)
        }.toString(2)
    }

    suspend fun importDatabaseJson(rawJson: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(rawJson)
        require(root.optString("format") == "focusflow-database") {
            "Invalid backup format. Expected focusflow-database JSON."
        }
        val data = root.getJSONObject("data")

        dao.clearSubjects(); dao.clearChapters(); dao.clearStudyTasks(); dao.clearSubtasks()
        dao.clearSessions(); dao.clearSessionChapters(); dao.clearSessionTasks()
        dao.clearPresets(); dao.clearPresetChapters(); dao.clearPresetTasks()
        dao.clearBlockedApps(); dao.clearAllowedApps(); dao.clearBreakConfig()
        dao.clearSummaries(); dao.clearWidgets(); dao.clearCustomCatalogApps()

        data.optJSONArray("subjects")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SubjectEntity(o.getLong("id"), o.getString("name"), o.optBoolean("builtIn", false), o.optInt("sortOrder", i))
            }
            dao.insertSubjects(items)
        }
        data.optJSONArray("chapters")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChapterEntity(o.getLong("id"), o.getLong("subjectId"), o.getString("name"), o.optInt("sortOrder", i))
            }
            dao.insertChapters(items)
        }
        data.optJSONArray("studyTasks")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StudyTaskEntity(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    subjectId = o.optLongOrNull("subjectId"),
                    chapterId = o.optLongOrNull("chapterId"),
                    estimatedMinutes = o.optInt("estimatedMinutes", 25),
                    status = runCatching { TaskStatus.valueOf(o.optString("status")) }.getOrDefault(TaskStatus.PENDING),
                    origin = runCatching { TaskOrigin.valueOf(o.optString("origin")) }.getOrDefault(TaskOrigin.MANUAL),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    completedAt = o.optLongOrNull("completedAt")
                )
            }
            dao.insertStudyTasks(items)
        }
        data.optJSONArray("subtasks")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SubtaskEntity(o.getLong("id"), o.getLong("taskId"), o.getString("title"), o.optBoolean("done", false), o.optInt("sortOrder", i))
            }
            dao.insertSubtasks(items)
        }
        data.optJSONArray("sessions")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FocusSessionEntity(
                    id = o.getLong("id"),
                    label = o.optString("label", "Focus session"),
                    mode = runCatching { FocusMode.valueOf(o.optString("mode")) }.getOrDefault(FocusMode.TIMED),
                    status = runCatching { SessionStatus.valueOf(o.optString("status")) }.getOrDefault(SessionStatus.COMPLETED),
                    plannedDurationMinutes = o.optIntOrNull("plannedDurationMinutes"),
                    startedAt = o.optLong("startedAt", System.currentTimeMillis()),
                    endedAt = o.optLongOrNull("endedAt"),
                    breakMsCompleted = o.optLong("breakMsCompleted", 0L),
                    shortBreaksUsed = o.optInt("shortBreaksUsed", 0),
                    longBreaksUsed = o.optInt("longBreaksUsed", 0),
                    strictMode = o.optBoolean("strictMode", true),
                    rawStudyPlan = o.optString("rawStudyPlan", "")
                )
            }
            dao.insertSessions(items)
        }
        data.optJSONArray("sessionChapters")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SessionChapterEntity(o.getLong("id"), o.getLong("sessionId"), o.getLong("subjectId"), o.optLongOrNull("chapterId"))
            }
            dao.insertSessionChapters(items)
        }
        data.optJSONArray("sessionTasks")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SessionTaskEntity(
                    id = o.getLong("id"),
                    sessionId = o.getLong("sessionId"),
                    studyTaskId = o.optLongOrNull("studyTaskId"),
                    title = o.getString("title"),
                    subjectId = o.optLongOrNull("subjectId"),
                    chapterId = o.optLongOrNull("chapterId"),
                    estimatedMinutes = o.optInt("estimatedMinutes", 25),
                    completed = o.optBoolean("completed", false),
                    completedAt = o.optLongOrNull("completedAt"),
                    sortOrder = o.optInt("sortOrder", i)
                )
            }
            dao.insertSessionTasks(items)
        }
        data.optJSONArray("presets")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PresetEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    mode = runCatching { FocusMode.valueOf(o.optString("mode")) }.getOrDefault(FocusMode.TIMED),
                    plannedDurationMinutes = o.optIntOrNull("plannedDurationMinutes"),
                    strictMode = o.optBoolean("strictMode", true),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
            dao.insertPresets(items)
        }
        data.optJSONArray("presetChapters")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PresetChapterEntity(o.getLong("id"), o.getLong("presetId"), o.getLong("subjectId"), o.optLongOrNull("chapterId"))
            }
            dao.insertPresetChapters(items)
        }
        data.optJSONArray("presetTasks")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PresetTaskEntity(
                    id = o.getLong("id"),
                    presetId = o.getLong("presetId"),
                    title = o.getString("title"),
                    subjectId = o.optLongOrNull("subjectId"),
                    chapterId = o.optLongOrNull("chapterId"),
                    estimatedMinutes = o.optInt("estimatedMinutes", 25),
                    sortOrder = o.optInt("sortOrder", i)
                )
            }
            dao.insertPresetTasks(items)
        }
        data.optJSONArray("blockedApps")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BlockedAppEntity(
                    packageName = o.getString("packageName"),
                    label = o.getString("label"),
                    category = runCatching { AppCategory.valueOf(o.optString("category")) }.getOrDefault(AppCategory.SOCIAL),
                    blockedDuringBreak = o.optBoolean("blockedDuringBreak", false)
                )
            }
            dao.insertBlockedApps(items)
        }
        data.optJSONArray("allowedApps")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AllowedAppEntity(
                    packageName = o.getString("packageName"),
                    label = o.getString("label"),
                    category = runCatching { AppCategory.valueOf(o.optString("category")) }.getOrDefault(AppCategory.CUSTOM)
                )
            }
            dao.insertAllowedApps(items)
        }
        data.optJSONObject("breakConfig")?.let { o ->
            dao.insertBreakConfig(
                BreakConfigEntity(
                    id = 1,
                    enabled = o.optBoolean("enabled", true),
                    tasksForShortBreak = o.optInt("tasksForShortBreak", 1),
                    shortBreakMinutes = o.optInt("shortBreakMinutes", 5),
                    tasksForLongBreak = o.optInt("tasksForLongBreak", 3),
                    longBreakMinutes = o.optInt("longBreakMinutes", 15)
                )
            )
        }
        data.optJSONArray("summaries")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SessionSummaryEntity(o.getLong("sessionId"), o.getString("summaryText"), o.optString("providerUsed", "Firebase AI"), o.optLong("generatedAt", System.currentTimeMillis()))
            }
            dao.insertSummaries(items)
        }
        data.optJSONArray("widgets")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DashboardWidgetEntity(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    type = runCatching { WidgetType.valueOf(o.optString("type")) }.getOrDefault(WidgetType.TIMER),
                    action = runCatching { WidgetAction.valueOf(o.optString("action")) }.getOrDefault(WidgetAction.START_SESSION),
                    icon = runCatching { WidgetIcon.valueOf(o.optString("icon")) }.getOrDefault(WidgetIcon.TIMER),
                    accent = runCatching { WidgetAccent.valueOf(o.optString("accent")) }.getOrDefault(WidgetAccent.INDIGO),
                    size = runCatching { WidgetSize.valueOf(o.optString("size")) }.getOrDefault(WidgetSize.SMALL),
                    mode = runCatching { FocusMode.valueOf(o.optString("mode")) }.getOrDefault(FocusMode.TIMED),
                    durationMinutes = o.optIntOrNull("durationMinutes"),
                    strictMode = o.optBoolean("strictMode", true),
                    subjectId = o.optLongOrNull("subjectId"),
                    progressRange = runCatching { ProgressRange.valueOf(o.optString("progressRange")) }.getOrDefault(ProgressRange.DAYS),
                    sortOrder = o.optInt("sortOrder", i)
                )
            }
            dao.insertWidgets(items)
        }
        data.optJSONArray("customCatalogApps")?.let { arr ->
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomCatalogAppEntity(
                    packageName = o.getString("packageName"),
                    label = o.getString("label"),
                    category = runCatching { AppCategory.valueOf(o.optString("category")) }.getOrDefault(AppCategory.CUSTOM)
                )
            }
            dao.insertCustomCatalogApps(items)
        }
        data.optJSONObject("settings")?.let { sObj ->
            applySettingsJson(sObj)
        }
        ensureSeeded()
    }

    suspend fun exportSettingsJson(): String = withContext(Dispatchers.IO) {
        val settings = settingsStore.getSettingsOnce()
        val bc = dao.getBreakConfigOnce() ?: SeedData.defaultBreakConfig()
        val providersWithoutKeys = JSONArray().apply {
            settingsStore.getProvidersOnce().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("type", p.type.name)
                    put("baseUrl", p.baseUrl); put("model", p.model)
                })
            }
        }
        JSONObject().apply {
            put("format", "focusflow-settings")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("settings", settingsToJson(settings))
            put("breakConfig", JSONObject().apply {
                put("enabled", bc.enabled); put("tasksForShortBreak", bc.tasksForShortBreak)
                put("shortBreakMinutes", bc.shortBreakMinutes); put("tasksForLongBreak", bc.tasksForLongBreak)
                put("longBreakMinutes", bc.longBreakMinutes)
            })
            put("providers", providersWithoutKeys)
        }.toString(2)
    }

    suspend fun importSettingsJson(rawJson: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(rawJson)
        require(root.optString("format") == "focusflow-settings") {
            "Invalid settings format. Expected focusflow-settings JSON."
        }
        root.optJSONObject("settings")?.let { applySettingsJson(it) }
        root.optJSONObject("breakConfig")?.let { o ->
            dao.insertBreakConfig(
                BreakConfigEntity(
                    id = 1,
                    enabled = o.optBoolean("enabled", true),
                    tasksForShortBreak = o.optInt("tasksForShortBreak", 1),
                    shortBreakMinutes = o.optInt("shortBreakMinutes", 5),
                    tasksForLongBreak = o.optInt("tasksForLongBreak", 3),
                    longBreakMinutes = o.optInt("longBreakMinutes", 15)
                )
            )
        }
    }

    suspend fun clearAllStudyData() = withContext(Dispatchers.IO) {
        dao.clearStudyTasks()
        dao.clearSubtasks()
        dao.clearSessions()
        dao.clearSessionChapters()
        dao.clearSessionTasks()
        dao.clearPresets()
        dao.clearPresetChapters()
        dao.clearPresetTasks()
        dao.clearSummaries()
        ensureSeeded()
    }

    private fun settingsToJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("themeMode", s.themeMode.name)
        put("blockYoutubeApp", s.blockYoutubeApp)
        put("blockYoutubeAppDuringBreaks", s.blockYoutubeAppDuringBreaks)
        put("blockYoutubeShorts", s.blockYoutubeShorts.name)
        put("skipYoutubeHomeFeed", s.skipYoutubeHomeFeed)
        put("blockAllExceptAllowed", s.blockAllExceptAllowed)
        put("assistantBubbleEnabled", s.assistantBubbleEnabled)
        put("strictDefault", s.strictDefault)
        put("voiceOfflineOnly", s.voiceOfflineOnly)
        put("autoSummaryEnabled", s.autoSummaryEnabled)
        put("autoLaunchSnapGpt", s.autoLaunchSnapGpt)
        put("snapGptPackage", s.snapGptPackage)
        put("wifiSyncCode", s.wifiSyncCode)
    }

    private suspend fun applySettingsJson(o: JSONObject) {
        settingsStore.updateSettings { current ->
            current.copy(
                themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode", current.themeMode.name)) }.getOrDefault(current.themeMode),
                blockYoutubeApp = o.optBoolean("blockYoutubeApp", current.blockYoutubeApp),
                blockYoutubeAppDuringBreaks = o.optBoolean("blockYoutubeAppDuringBreaks", current.blockYoutubeAppDuringBreaks),
                blockYoutubeShorts = runCatching { ShortsMode.valueOf(o.optString("blockYoutubeShorts", current.blockYoutubeShorts.name)) }.getOrDefault(current.blockYoutubeShorts),
                skipYoutubeHomeFeed = o.optBoolean("skipYoutubeHomeFeed", current.skipYoutubeHomeFeed),
                blockAllExceptAllowed = o.optBoolean("blockAllExceptAllowed", current.blockAllExceptAllowed),
                assistantBubbleEnabled = o.optBoolean("assistantBubbleEnabled", current.assistantBubbleEnabled),
                strictDefault = o.optBoolean("strictDefault", current.strictDefault),
                voiceOfflineOnly = o.optBoolean("voiceOfflineOnly", current.voiceOfflineOnly),
                autoSummaryEnabled = o.optBoolean("autoSummaryEnabled", current.autoSummaryEnabled),
                autoLaunchSnapGpt = o.optBoolean("autoLaunchSnapGpt", current.autoLaunchSnapGpt),
                snapGptPackage = o.optString("snapGptPackage", current.snapGptPackage),
                wifiSyncCode = o.optString("wifiSyncCode", current.wifiSyncCode)
            )
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)
}
