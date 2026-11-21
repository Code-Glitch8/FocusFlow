package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.StudyAiService
import com.example.data.FocusFlowRepository
import com.example.data.SeedData
import com.example.data.local.AllowedAppEntity
import com.example.data.local.BlockedAppEntity
import com.example.data.local.BreakConfigEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.CustomCatalogAppEntity
import com.example.data.local.DashboardWidgetEntity
import com.example.data.local.FocusFlowDatabase
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
import com.example.domain.BlockPolicy
import com.example.domain.EarnedBreakCalculator
import com.example.domain.SessionClock
import com.example.domain.StudyPlanParser
import com.example.domain.StudyStatsCalculator
import com.example.domain.YouTubeUiClassifier
import com.example.model.ActiveShieldAlert
import com.example.model.AiProviderProfile
import com.example.model.AppCategory
import com.example.model.AppSettings
import com.example.model.CatalogApp
import com.example.model.DraftStudyTask
import com.example.model.EarnedBreakSnapshot
import com.example.model.FocusMode
import com.example.model.PermissionHealth
import com.example.model.ProgressRange
import com.example.model.SessionStatus
import com.example.model.SessionTimeSnapshot
import com.example.model.StudyQuote
import com.example.model.StudyStats
import com.example.model.SubjectPlan
import com.example.model.TaskOrigin
import com.example.model.WidgetPresetTask
import com.example.nativebridge.AppLauncherHelper
import com.example.nativebridge.FocusFlowWidgetProvider
import com.example.nativebridge.FocusTimerService
import com.example.nativebridge.LanPeerInfo
import com.example.nativebridge.LanSyncManager
import com.example.nativebridge.ShieldOverlayManager
import com.example.nativebridge.ShieldPolicySnapshot
import com.example.nativebridge.ShieldStateBridge
import com.example.nativebridge.UsageStatsHelper
import com.example.nativebridge.VoiceInputManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "FocusFlowVM"

data class FocusFlowUiState(
    val currentRoute: FocusRoute = FocusRoute.Tab(TopTab.HOME),
    val backStack: List<FocusRoute> = emptyList(),
    val nowMs: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val providers: List<AiProviderProfile> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val studyTasks: List<StudyTaskEntity> = emptyList(),
    val subtasks: List<SubtaskEntity> = emptyList(),
    val sessions: List<FocusSessionEntity> = emptyList(),
    val sessionChapters: List<SessionChapterEntity> = emptyList(),
    val sessionTasks: List<SessionTaskEntity> = emptyList(),
    val presets: List<PresetEntity> = emptyList(),
    val presetChapters: List<PresetChapterEntity> = emptyList(),
    val presetTasks: List<PresetTaskEntity> = emptyList(),
    val blockedApps: List<BlockedAppEntity> = emptyList(),
    val allowedApps: List<AllowedAppEntity> = emptyList(),
    val customCatalogApps: List<CustomCatalogAppEntity> = emptyList(),
    val breakConfig: BreakConfigEntity = SeedData.defaultBreakConfig(),
    val summaries: List<SessionSummaryEntity> = emptyList(),
    val widgets: List<DashboardWidgetEntity> = emptyList(),
    val activeSession: FocusSessionEntity? = null,
    val activeTime: SessionTimeSnapshot? = null,
    val activeEarnedBreaks: EarnedBreakSnapshot? = null,
    val stats: StudyStats = StudyStats(0, 0, 0, 0, 0, 0, 0, 0, emptyList(), emptyList(), emptyList(), emptyList()),
    val quoteIndex: Int = 0,
    val permissionHealth: PermissionHealth = PermissionHealth(false, false, false, true, false),
    val shieldAlert: ActiveShieldAlert? = null,
    val guardStatusBanner: String? = null,
    val snapGptBanner: String? = null,
    val voiceListening: Boolean = false,
    val voicePartial: String = "",
    val voiceError: String? = null,
    val aiBusy: Boolean = false,
    val aiError: String? = null,
    val aiNotice: String? = null,
    val summaryGeneratingSessionId: Long? = null,
    val summaryError: String? = null,
    val providerTestStatus: String? = null,
    val lanPeers: List<LanPeerInfo> = emptyList(),
    val lanSyncMessage: String? = null,
    val lanSyncBusy: Boolean = false,
    val toastMessage: String? = null
) {
    val currentQuote: StudyQuote
        get() = SeedData.STUDY_QUOTES[quoteIndex % SeedData.STUDY_QUOTES.size]

    val allCatalogApps: List<CatalogApp>
        get() {
            val base = SeedData.CATALOG_APPS.associateBy { it.packageName }.toMutableMap()
            customCatalogApps.forEach {
                base[it.packageName] = CatalogApp(it.packageName, it.label, it.category, isCustom = true)
            }
            return base.values.toList()
        }

    val activeProvider: AiProviderProfile?
        get() = providers.find { it.id == settings.activeProviderId }
}

class FocusFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FocusFlowDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val repository = FocusFlowRepository(db.dao(), settingsStore)
    private val aiService = StudyAiService()
    private val voiceManager = VoiceInputManager(application)
    private val lanSyncManager = LanSyncManager(application, viewModelScope)

    private val _route = MutableStateFlow<FocusRoute>(FocusRoute.Tab(TopTab.HOME))
    private val _backStack = MutableStateFlow<List<FocusRoute>>(emptyList())
    private val _nowMs = MutableStateFlow(System.currentTimeMillis())
    private val _quoteIndex = MutableStateFlow(0)
    private val _permissions = MutableStateFlow(ShieldStateBridge.checkPermissions(application))
    private val _guardBanner = MutableStateFlow<String?>(null)
    private val _snapGptBanner = MutableStateFlow<String?>(null)
    private val _voiceListening = MutableStateFlow(false)
    private val _voicePartial = MutableStateFlow("")
    private val _voiceError = MutableStateFlow<String?>(null)
    private val _aiBusy = MutableStateFlow(false)
    private val _aiError = MutableStateFlow<String?>(null)
    private val _aiNotice = MutableStateFlow<String?>(null)
    private val _summaryGenId = MutableStateFlow<Long?>(null)
    private val _summaryError = MutableStateFlow<String?>(null)
    private val _providerTestStatus = MutableStateFlow<String?>(null)
    private val _lanPeers = MutableStateFlow<List<LanPeerInfo>>(emptyList())
    private val _lanMsg = MutableStateFlow<String?>(null)
    private val _lanBusy = MutableStateFlow(false)
    private val _toast = MutableStateFlow<String?>(null)

    private val baseDataFlow = combine(
        repository.settingsFlow,
        repository.providersFlow,
        repository.subjectsFlow,
        repository.chaptersFlow,
        repository.studyTasksFlow
    ) { settings, providers, subjects, chapters, studyTasks ->
        BaseDataSlice(settings, providers, subjects, chapters, studyTasks)
    }

    private val sessionDataFlow = combine(
        repository.subtasksFlow,
        repository.sessionsFlow,
        repository.sessionChaptersFlow,
        repository.sessionTasksFlow,
        repository.breakConfigFlow
    ) { subtasks, sessions, sessionChapters, sessionTasks, breakConfig ->
        SessionDataSlice(subtasks, sessions, sessionChapters, sessionTasks, breakConfig)
    }

    private val auxDataFlow = combine(
        repository.presetsFlow,
        repository.presetChaptersFlow,
        repository.presetTasksFlow,
        repository.blockedAppsFlow,
        repository.allowedAppsFlow
    ) { presets, presetChapters, presetTasks, blockedApps, allowedApps ->
        AuxDataSlice(presets, presetChapters, presetTasks, blockedApps, allowedApps)
    }

    private val metaDataFlow = combine(
        repository.customCatalogAppsFlow,
        repository.summariesFlow,
        repository.widgetsFlow
    ) { customCatalog, summaries, widgets ->
        MetaDataSlice(customCatalog, summaries, widgets)
    }

    private val transientFlow = combine(
        combine(_route, _backStack, _nowMs, _quoteIndex, _permissions) { r, bs, now, q, p ->
            NavTransient(r, bs, now, q, p)
        },
        combine(ShieldStateBridge.activeAlert, _guardBanner, _snapGptBanner, _toast) { a, g, s, t ->
            BannerTransient(a, g, s, t)
        },
        combine(_voiceListening, _voicePartial, _voiceError, _aiBusy, _aiError) { vl, vp, ve, ab, ae ->
            VoiceAiTransient(vl, vp, ve, ab, ae)
        },
        combine(_aiNotice, _summaryGenId, _summaryError, _providerTestStatus) { an, sg, se, pt ->
            ExtraAiTransient(an, sg, se, pt)
        },
        combine(_lanPeers, _lanMsg, _lanBusy) { lp, lm, lb ->
            LanTransient(lp, lm, lb)
        }
    ) { nav, banner, voiceAi, extraAi, lan ->
        AllTransient(nav, banner, voiceAi, extraAi, lan)
    }

    val uiState: StateFlow<FocusFlowUiState> = combine(
        baseDataFlow,
        sessionDataFlow,
        auxDataFlow,
        metaDataFlow,
        transientFlow
    ) { base, sess, aux, meta, tr ->
        val now = tr.nav.nowMs
        val activeSession = sess.sessions.firstOrNull { it.status == SessionStatus.ACTIVE }
        val activeTime = activeSession?.let { SessionClock.computeSnapshot(it, now) }
        val activeEarnedBreaks = activeSession?.let { active ->
            val doneCount = sess.sessionTasks.count { it.sessionId == active.id && it.completed }
            EarnedBreakCalculator.compute(
                completedTasks = doneCount,
                shortBreaksUsed = active.shortBreaksUsed,
                longBreaksUsed = active.longBreaksUsed,
                config = sess.breakConfig
            )
        }
        val stats = StudyStatsCalculator.compute(
            sessions = sess.sessions,
            sessionChapters = sess.sessionChapters,
            sessionTasks = sess.sessionTasks,
            standaloneTasks = base.studyTasks,
            subjects = base.subjects,
            chapters = base.chapters,
            nowMs = now
        )

        FocusFlowUiState(
            currentRoute = tr.nav.route,
            backStack = tr.nav.backStack,
            nowMs = now,
            settings = base.settings,
            providers = base.providers,
            subjects = base.subjects,
            chapters = base.chapters,
            studyTasks = base.studyTasks,
            subtasks = sess.subtasks,
            sessions = sess.sessions,
            sessionChapters = sess.sessionChapters,
            sessionTasks = sess.sessionTasks,
            presets = aux.presets,
            presetChapters = aux.presetChapters,
            presetTasks = aux.presetTasks,
            blockedApps = aux.blockedApps,
            allowedApps = aux.allowedApps,
            customCatalogApps = meta.customCatalog,
            breakConfig = sess.breakConfig,
            summaries = meta.summaries,
            widgets = meta.widgets,
            activeSession = activeSession,
            activeTime = activeTime,
            activeEarnedBreaks = activeEarnedBreaks,
            stats = stats,
            quoteIndex = tr.nav.quoteIndex,
            permissionHealth = tr.nav.permissions,
            shieldAlert = tr.banner.alert,
            guardStatusBanner = tr.banner.guardBanner,
            snapGptBanner = tr.banner.snapGptBanner,
            voiceListening = tr.voiceAi.listening,
            voicePartial = tr.voiceAi.partial,
            voiceError = tr.voiceAi.error,
            aiBusy = tr.voiceAi.aiBusy,
            aiError = tr.voiceAi.aiError,
            aiNotice = tr.extraAi.aiNotice,
            summaryGeneratingSessionId = tr.extraAi.summaryGenId,
            summaryError = tr.extraAi.summaryError,
            providerTestStatus = tr.extraAi.providerTestStatus,
            lanPeers = tr.lan.peers,
            lanSyncMessage = tr.lan.message,
            lanSyncBusy = tr.lan.busy,
            toastMessage = tr.banner.toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FocusFlowUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
        startClockTicker()
        startLanSyncHost()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val now = System.currentTimeMillis()
                _nowMs.value = now
                val state = uiState.value
                val active = state.activeSession
                val time = active?.let { SessionClock.computeSnapshot(it, now) }

                // Settle naturally finished breaks & timed sessions
                if (active != null && time != null) {
                    if (active.breakEndsAt != null && now >= active.breakEndsAt) {
                        repository.settleExpiredBreakIfNeeded(active.id, now)
                    }
                    if (time.isTimedFinished) {
                        val exactEnd = SessionClock.computeExactEndedAt(active, now)
                        finishSessionAndShowSummary(active.id, exactEnd, abandoned = false)
                    }
                }

                // Sync ShieldStateBridge + Foreground Notification Service
                val remainingStr = when {
                    time == null -> ""
                    time.isOnBreak -> SessionClock.formatClock(time.breakRemainingMs)
                    time.remainingMs != null -> SessionClock.formatClock(time.remainingMs)
                    else -> SessionClock.formatClock(time.focusElapsedMs)
                }
                ShieldStateBridge.updatePolicy(
                    ShieldPolicySnapshot(
                        sessionActive = active != null,
                        sessionLabel = active?.label.orEmpty(),
                        remainingLabel = remainingStr,
                        onBreak = time?.isOnBreak == true,
                        settings = state.settings,
                        blockedApps = state.blockedApps,
                        allowedApps = state.allowedApps
                    )
                )

                val ctx = getApplication<Application>()
                if (active != null && time != null) {
                    val notifTitle = if (time.isOnBreak) {
                        "On Break · ${active.label}"
                    } else {
                        "Focusing · ${active.label}"
                    }
                    val notifSubtitle = if (time.isOnBreak) {
                        "${SessionClock.formatClock(time.breakRemainingMs)} break remaining"
                    } else if (time.remainingMs != null) {
                        "${SessionClock.formatClock(time.remainingMs)} remaining"
                    } else {
                        "${SessionClock.formatClock(time.focusElapsedMs)} elapsed"
                    }
                    FocusTimerService.sync(ctx, true, notifTitle, notifSubtitle)
                } else {
                    FocusTimerService.sync(ctx, false, "", "")
                }

                // Fallback UsageStats foreground polling when Accessibility service is off
                if (active != null && !state.permissionHealth.accessibilityEnabled && state.permissionHealth.usageStatsGranted) {
                    val fgPkg = UsageStatsHelper.queryRecentForegroundPackage(ctx)
                    if (fgPkg != null && fgPkg != ctx.packageName && fgPkg !in BlockPolicy.ESSENTIAL_PACKAGES) {
                        val label = state.allCatalogApps.find { it.packageName == fgPkg }?.label ?: fgPkg
                        val decision = BlockPolicy.evaluateApp(
                            packageName = fgPkg,
                            appLabel = label,
                            sessionActive = true,
                            onBreak = time?.isOnBreak == true,
                            settings = state.settings,
                            blockedApps = state.blockedApps,
                            allowedApps = state.allowedApps
                        )
                        if (decision.shouldBlock) {
                            val alert = ActiveShieldAlert(
                                packageName = fgPkg,
                                appLabel = label,
                                reason = decision.reason,
                                isYoutubeShorts = false,
                                isYoutubeApp = fgPkg == BlockPolicy.YOUTUBE_PACKAGE
                            )
                            if (ShieldStateBridge.appInForeground.value) {
                                ShieldStateBridge.triggerAlert(alert)
                            } else {
                                ShieldOverlayManager.showShield(ctx, alert)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startLanSyncHost() {
        lanSyncManager.startHostServer(
            pairingCodeProvider = { uiState.value.settings.wifiSyncCode },
            snapshotExporter = { repository.exportDatabaseJson() },
            onIncomingSnapshot = { peerName, json ->
                runCatching {
                    repository.importDatabaseJson(json)
                    repository.updateSettings {
                        it.copy(
                            wifiSyncLastSyncedAt = System.currentTimeMillis(),
                            wifiSyncLastPeer = peerName
                        )
                    }
                    _lanMsg.value = "Synced snapshot from $peerName."
                }.onFailure { err ->
                    _lanMsg.value = "Incoming LAN sync failed: ${err.localizedMessage}"
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
        lanSyncManager.stop()
    }

    // Navigation
    fun selectTopTab(tab: TopTab) {
        _backStack.value = emptyList()
        _route.value = FocusRoute.Tab(tab)
    }

    fun navigateTo(route: FocusRoute) {
        _backStack.value = _backStack.value + _route.value
        _route.value = route
    }

    fun navigateBack(): Boolean {
        val stack = _backStack.value
        if (stack.isNotEmpty()) {
            _route.value = stack.last()
            _backStack.value = stack.dropLast(1)
            return true
        }
        if (_route.value !is FocusRoute.Tab) {
            _route.value = FocusRoute.Tab(TopTab.HOME)
            return true
        }
        return false
    }

    fun refreshPermissions() {
        _permissions.value = ShieldStateBridge.checkPermissions(getApplication())
    }

    fun nextQuote() {
        val size = SeedData.STUDY_QUOTES.size
        if (size <= 1) return
        val current = _quoteIndex.value % size
        val candidates = (0 until size).filter { it != current }
        _quoteIndex.value = candidates.random()
    }

    fun setChapterConfidence(chapterId: Long, stars: Int) {
        val clamped = stars.coerceIn(1, 5)
        updateSettings { settings ->
            settings.copy(chapterConfidence = settings.chapterConfidence + (chapterId to clamped))
        }
    }

    fun clearToast() {
        _toast.value = null
    }

    fun showToast(msg: String) {
        _toast.value = msg
    }

    fun dismissSnapGptBanner() {
        _snapGptBanner.value = null
    }

    fun dismissGuardBanner() {
        _guardBanner.value = null
    }

    fun dismissShieldAlert() {
        ShieldStateBridge.clearAlert()
    }

    fun openWidget(widget: DashboardWidgetEntity) {
        when (widget.action) {
            com.example.model.WidgetAction.OPEN_STATISTICS -> selectTopTab(TopTab.STATISTICS)
            com.example.model.WidgetAction.OPEN_AI_SETUP -> navigateTo(FocusRoute.AiSettings)
            com.example.model.WidgetAction.START_SESSION -> {
                if (widget.type == com.example.model.WidgetType.PROGRESS) {
                    selectTopTab(TopTab.STATISTICS)
                } else {
                    navigateTo(
                        FocusRoute.SessionPrep(
                            title = widget.title,
                            mode = widget.mode,
                            durationMinutes = widget.durationMinutes ?: 25,
                            strictMode = widget.strictMode,
                            initialSubjectId = widget.subjectId,
                            widgetId = widget.id
                        )
                    )
                }
            }
        }
    }

    fun updateWidgetProgressRange(widget: DashboardWidgetEntity, range: ProgressRange) {
        viewModelScope.launch {
            repository.saveWidget(widget.copy(progressRange = range))
        }
    }

    fun saveWidget(widget: DashboardWidgetEntity) {
        viewModelScope.launch {
            repository.saveWidget(widget)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun saveWidgetWithTasks(widget: DashboardWidgetEntity, tasks: List<WidgetPresetTask>) {
        viewModelScope.launch {
            val savedId = repository.saveWidget(widget)
            val cleaned = tasks
                .map { it.copy(title = it.title.trim(), estimatedMinutes = it.estimatedMinutes.coerceIn(5, 240)) }
                .filter { it.title.isNotEmpty() }
            repository.updateSettings { settings ->
                val next = settings.widgetPresetTasks.toMutableMap()
                if (cleaned.isEmpty()) next.remove(savedId) else next[savedId] = cleaned
                settings.copy(widgetPresetTasks = next)
            }
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun startWidgetSession(widget: DashboardWidgetEntity, launchMinimized: Boolean = false) {
        viewModelScope.launch {
            val sessionId = repository.startSessionFromWidget(widget.id)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
            if (sessionId > 0L) {
                if (uiState.value.settings.autoLaunchSnapGpt) {
                    val pkg = uiState.value.settings.snapGptPackage
                    val launched = AppLauncherHelper.launchPackage(getApplication(), pkg)
                    _snapGptBanner.value = if (launched) {
                        "SnapGPT started automatically ($pkg)"
                    } else {
                        "Auto-launch enabled for $pkg (app not installed on this device)."
                    }
                }
                _backStack.value = emptyList()
                _route.value = FocusRoute.Tab(if (launchMinimized) TopTab.HOME else TopTab.FOCUS)
                _toast.value = "Started ${widget.title}."
            }
        }
    }

    fun deleteWidget(id: Long) {
        viewModelScope.launch {
            repository.deleteWidget(id)
            repository.updateSettings { s ->
                s.copy(
                    widgetPresetTasks = s.widgetPresetTasks - id,
                    androidWidgetBindings = s.androidWidgetBindings.filterValues { it != id }
                )
            }
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun moveWidget(id: Long, delta: Int) {
        viewModelScope.launch {
            repository.moveWidget(id, delta)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun resetWidgets() {
        viewModelScope.launch {
            repository.resetDefaultWidgets()
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
            _toast.value = "Default dashboard widgets restored."
        }
    }

    // Subjects & Chapters
    fun addSubject(name: String) {
        viewModelScope.launch { repository.addSubject(name) }
    }

    fun renameSubject(subject: SubjectEntity, name: String) {
        viewModelScope.launch { repository.renameSubject(subject, name) }
    }

    fun deleteCustomSubject(subject: SubjectEntity) {
        viewModelScope.launch { repository.deleteCustomSubject(subject) }
    }

    fun addChapter(subjectId: Long, name: String) {
        viewModelScope.launch { repository.addChapter(subjectId, name) }
    }

    fun renameChapter(chapter: ChapterEntity, name: String) {
        viewModelScope.launch { repository.renameChapter(chapter, name) }
    }

    fun deleteChapter(chapterId: Long) {
        viewModelScope.launch { repository.deleteChapter(chapterId) }
    }

    // Tasks & Subtasks
    fun saveStudyTask(
        existingId: Long?,
        title: String,
        subjectId: Long?,
        chapterId: Long?,
        estimatedMinutes: Int,
        subtasks: List<String>
    ) {
        viewModelScope.launch {
            repository.saveStudyTask(existingId, title, subjectId, chapterId, estimatedMinutes, TaskOrigin.MANUAL, subtasks)
        }
    }

    fun toggleStudyTask(task: StudyTaskEntity) {
        viewModelScope.launch { repository.toggleStudyTaskDone(task) }
    }

    fun deleteStudyTask(taskId: Long) {
        viewModelScope.launch { repository.deleteStudyTask(taskId) }
    }

    fun toggleSubtask(subtask: SubtaskEntity) {
        viewModelScope.launch { repository.toggleSubtaskDone(subtask) }
    }

    // Voice & AI Extraction
    fun startVoiceCapture(onResult: (String) -> Unit) {
        _voiceError.value = null
        _voicePartial.value = ""
        _voiceListening.value = true
        voiceManager.startListening(
            preferOffline = uiState.value.settings.voiceOfflineOnly,
            onPartial = { text -> _voicePartial.value = text },
            onFinal = { text ->
                _voiceListening.value = false
                _voicePartial.value = text
                onResult(text)
            },
            onFailure = { err ->
                _voiceListening.value = false
                _voiceError.value = err
            }
        )
    }

    fun stopVoiceCapture() {
        voiceManager.stopListening()
        _voiceListening.value = false
    }

    fun parseLocalTasks(
        rawPlan: String,
        subjectPlans: List<SubjectPlan>,
        origin: TaskOrigin = TaskOrigin.AI_TEXT
    ): List<DraftStudyTask> {
        val state = uiState.value
        return StudyPlanParser.parseLocal(rawPlan, subjectPlans, state.subjects, state.chapters, origin)
    }

    fun generateAiTasks(
        rawPlan: String,
        subjectPlans: List<SubjectPlan>,
        origin: TaskOrigin = TaskOrigin.AI_TEXT,
        onTasksReady: (List<DraftStudyTask>) -> Unit
    ) {
        viewModelScope.launch {
            _aiBusy.value = true
            _aiError.value = null
            _aiNotice.value = null
            try {
                val state = uiState.value
                val result = aiService.extractTasks(
                    studentPlan = rawPlan,
                    subjectPlans = subjectPlans,
                    subjects = state.subjects,
                    chapters = state.chapters,
                    activeProvider = state.activeProvider,
                    origin = origin
                )
                onTasksReady(result.tasks)
                _aiNotice.value = result.warning ?: "Suggested ${result.tasks.size} tasks via ${result.providerUsed}."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI task extraction error", e)
                _aiError.value = e.localizedMessage ?: "AI extraction failed."
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun clearAiMessages() {
        _aiError.value = null
        _aiNotice.value = null
    }

    // Session lifecycle
    fun startStudySession(
        label: String,
        mode: FocusMode,
        plannedDurationMinutes: Int?,
        strictMode: Boolean,
        rawStudyPlan: String,
        subjectPlans: List<SubjectPlan>,
        draftTasks: List<DraftStudyTask>,
        selectedStudyTaskIds: List<Long>,
        widgetId: Long? = null,
        launchMinimized: Boolean = false
    ) {
        viewModelScope.launch {
            val sessionId = repository.startSession(
                label = label,
                mode = mode,
                plannedDurationMinutes = plannedDurationMinutes,
                strictMode = strictMode,
                rawStudyPlan = rawStudyPlan,
                subjectPlans = subjectPlans,
                draftTasks = draftTasks,
                selectedStudyTaskIds = selectedStudyTaskIds,
                widgetId = widgetId
            )
            if (uiState.value.settings.autoLaunchSnapGpt && sessionId > 0L) {
                val pkg = uiState.value.settings.snapGptPackage
                val launched = AppLauncherHelper.launchPackage(getApplication(), pkg)
                _snapGptBanner.value = if (launched) {
                    "SnapGPT started automatically ($pkg)"
                } else {
                    "Auto-launch enabled for $pkg (app not installed on this device)."
                }
            }
            _backStack.value = emptyList()
            _route.value = FocusRoute.Tab(if (launchMinimized) TopTab.HOME else TopTab.FOCUS)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
            if (launchMinimized) {
                _toast.value = "Focus session started right from Home."
            }
        }
    }

    fun toggleSessionTask(task: SessionTaskEntity) {
        viewModelScope.launch {
            repository.toggleSessionTask(task)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun addTaskToActiveSession(title: String, minutes: Int) {
        val activeId = uiState.value.activeSession?.id ?: return
        viewModelScope.launch {
            repository.addTaskToActiveSession(activeId, title, minutes)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun deleteSessionTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteSessionTask(taskId)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun startBreak(isLong: Boolean) {
        val activeId = uiState.value.activeSession?.id ?: return
        viewModelScope.launch {
            val ok = repository.startBreak(activeId, isLong)
            if (!ok) {
                _toast.value = "Complete more tasks to earn this break."
            } else {
                FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
            }
        }
    }

    fun endBreakEarly() {
        val activeId = uiState.value.activeSession?.id ?: return
        viewModelScope.launch {
            repository.endBreakEarly(activeId)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun updateActiveSessionDuration(newMinutes: Int?) {
        val activeId = uiState.value.activeSession?.id ?: return
        viewModelScope.launch {
            repository.updateSessionDuration(activeId, newMinutes)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun stopActiveSession(abandoned: Boolean = false) {
        val active = uiState.value.activeSession ?: return
        finishSessionAndShowSummary(active.id, System.currentTimeMillis(), abandoned)
    }

    private fun finishSessionAndShowSummary(sessionId: Long, endedAtMs: Long, abandoned: Boolean) {
        viewModelScope.launch {
            repository.completeSession(sessionId, endedAtMs, abandoned)
            FocusFlowWidgetProvider.refreshAllWidgets(getApplication())
            _backStack.value = listOf(FocusRoute.Tab(TopTab.HOME))
            _route.value = FocusRoute.SessionSummary(sessionId)
            if (uiState.value.settings.autoSummaryEnabled) {
                generateSessionSummary(sessionId)
            }
        }
    }

    fun updateSessionDetails(sessionId: Long, label: String, minutes: Int?) {
        viewModelScope.launch { repository.updateSessionDetails(sessionId, label, minutes) }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            navigateBack()
        }
    }

    fun generateSessionSummary(sessionId: Long) {
        viewModelScope.launch {
            val state = uiState.value
            val session = state.sessions.find { it.id == sessionId } ?: return@launch
            val snap = SessionClock.computeSnapshot(session, state.nowMs)
            val tasks = state.sessionTasks.filter { it.sessionId == sessionId }
            val doneCount = tasks.count { it.completed }
            val subjNames = state.sessionChapters
                .filter { it.sessionId == sessionId }
                .mapNotNull { sc -> state.subjects.find { it.id == sc.subjectId }?.name }
                .distinct()
                .ifEmpty { listOf("General study") }

            val focusMins = (snap.focusElapsedMs / 60_000L).toInt()
            val breakMins = (snap.breakElapsedMs / 60_000L).toInt()
            val facts = """
                Session: ${session.label}
                Mode: ${session.mode.label}
                Subjects: ${subjNames.joinToString(", ")}
                Focus time: $focusMins minutes
                Break time: $breakMins minutes
                Tasks completed: $doneCount of ${tasks.size}
                Task list: ${tasks.joinToString("; ") { "${it.title} (${if (it.completed) "done" else "pending"})" }.ifBlank { "None" }}
            """.trimIndent()

            val prompt = """
                Write a concise, encouraging 3 to 4 sentence study session summary strictly grounded in the facts below.
                Only reference numbers, subjects, and tasks explicitly listed in the facts. Do not invent extra data.

                $facts
            """.trimIndent()

            _summaryGenId.value = sessionId
            _summaryError.value = null
            try {
                val res = aiService.generateSessionSummary(prompt, state.activeProvider)
                repository.saveSessionSummary(sessionId, res.summary, res.providerUsed)
                if (res.warning != null) {
                    _summaryError.value = res.warning
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Summary generation error", e)
                _summaryError.value = e.localizedMessage ?: "Failed to generate summary."
            } finally {
                _summaryGenId.value = null
            }
        }
    }

    // Presets
    fun savePreset(
        name: String,
        mode: FocusMode,
        durationMinutes: Int?,
        strictMode: Boolean,
        subjectPlans: List<SubjectPlan>,
        draftTasks: List<DraftStudyTask>
    ) {
        viewModelScope.launch {
            repository.savePreset(name, mode, durationMinutes, strictMode, subjectPlans, draftTasks)
            _toast.value = "Preset '$name' saved."
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    // App Guard & Blocking
    fun testOpenTarget(packageName: String, label: String, isYoutubeShorts: Boolean = false) {
        val state = uiState.value
        val sessionActive = state.activeSession != null
        val onBreak = state.activeTime?.isOnBreak == true

        if (isYoutubeShorts) {
            val shortsBlocked = BlockPolicy.shouldBlockYoutubeShorts(
                mode = state.settings.blockYoutubeShorts,
                sessionActive = sessionActive,
                onBreak = onBreak
            )
            if (shortsBlocked || (state.settings.blockYoutubeApp && sessionActive && (!onBreak || state.settings.blockYoutubeAppDuringBreaks))) {
                ShieldStateBridge.triggerAlert(
                    ActiveShieldAlert(
                        packageName = BlockPolicy.YOUTUBE_PACKAGE,
                        appLabel = "YouTube Shorts",
                        reason = "YouTube Shorts is blocked while focusing. Regular YouTube stays available unless whole-app block is enabled.",
                        isYoutubeShorts = true,
                        isYoutubeApp = true
                    )
                )
            } else {
                val opened = AppLauncherHelper.openYoutube(getApplication(), shorts = true)
                _guardBanner.value = if (opened) {
                    "Opened YouTube Shorts (currently allowed)."
                } else {
                    "YouTube Shorts is currently allowed and open (preview mode)."
                }
            }
            return
        }

        val decision = BlockPolicy.evaluateApp(
            packageName = packageName,
            appLabel = label,
            sessionActive = sessionActive,
            onBreak = onBreak,
            settings = state.settings,
            blockedApps = state.blockedApps,
            allowedApps = state.allowedApps
        )
        if (decision.shouldBlock) {
            ShieldStateBridge.triggerAlert(
                ActiveShieldAlert(
                    packageName = packageName,
                    appLabel = label,
                    reason = decision.reason,
                    isYoutubeShorts = false,
                    isYoutubeApp = packageName == BlockPolicy.YOUTUBE_PACKAGE
                )
            )
        } else {
            val opened = if (packageName == BlockPolicy.YOUTUBE_PACKAGE) {
                AppLauncherHelper.openYoutube(getApplication(), shorts = false)
            } else {
                AppLauncherHelper.launchPackage(getApplication(), packageName)
            }
            _guardBanner.value = if (opened) {
                "Launched $label (currently allowed)."
            } else {
                "$label is currently allowed and open."
            }
        }
    }

    fun unblockAppFromShield(packageName: String) {
        viewModelScope.launch {
            if (packageName == BlockPolicy.YOUTUBE_PACKAGE) {
                repository.updateSettings {
                    it.copy(blockYoutubeApp = false, blockYoutubeAppDuringBreaks = false)
                }
            }
            val app = uiState.value.allCatalogApps.find { it.packageName == packageName }
            repository.toggleBlockedApp(packageName, app?.label ?: packageName, app?.category ?: AppCategory.CUSTOM, block = false)
            ShieldStateBridge.clearAlert()
            ShieldOverlayManager.dismissOverlay(getApplication())
        }
    }

    fun toggleBlockedApp(packageName: String, label: String, category: AppCategory, block: Boolean) {
        viewModelScope.launch { repository.toggleBlockedApp(packageName, label, category, block) }
    }

    fun setBlockedDuringBreak(packageName: String, blockedDuringBreak: Boolean) {
        viewModelScope.launch { repository.setBlockedDuringBreak(packageName, blockedDuringBreak) }
    }

    fun toggleAllowedApp(packageName: String, label: String, category: AppCategory, allow: Boolean) {
        viewModelScope.launch { repository.toggleAllowedApp(packageName, label, category, allow) }
    }

    fun addCustomApp(packageName: String, label: String, category: AppCategory, addToBlocked: Boolean) {
        viewModelScope.launch { repository.addCustomApp(packageName, label, category, addToBlocked) }
    }

    fun saveBreakConfig(config: BreakConfigEntity) {
        viewModelScope.launch { repository.saveBreakConfig(config) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    // AI Providers
    fun upsertProvider(profile: AiProviderProfile, activate: Boolean) {
        viewModelScope.launch {
            val existing = uiState.value.providers.toMutableList()
            val idx = existing.indexOfFirst { it.id == profile.id }
            if (idx >= 0) existing[idx] = profile else existing.add(profile)
            repository.saveProviders(existing)
            if (activate) {
                repository.updateSettings { it.copy(activeProviderId = profile.id) }
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            val remaining = uiState.value.providers.filterNot { it.id == id }
            repository.saveProviders(remaining)
            if (uiState.value.settings.activeProviderId == id) {
                repository.updateSettings { it.copy(activeProviderId = null) }
            }
        }
    }

    fun selectActiveProvider(id: String?) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(activeProviderId = id) }
        }
    }

    fun testProvider(provider: AiProviderProfile?) {
        viewModelScope.launch {
            _providerTestStatus.value = "Testing connection..."
            try {
                val reply = aiService.testProviderConnection(provider)
                _providerTestStatus.value = "Success: $reply"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _providerTestStatus.value = "Error: ${e.localizedMessage ?: "Connection failed."}"
            }
        }
    }

    // Backup & LAN Sync
    suspend fun exportDatabaseJson(): String = repository.exportDatabaseJson()

    suspend fun exportSettingsJson(): String = repository.exportSettingsJson()

    fun importDatabaseJson(raw: String) {
        viewModelScope.launch {
            runCatching { repository.importDatabaseJson(raw) }
                .onSuccess { _toast.value = "Database backup restored." }
                .onFailure { _toast.value = "Restore failed: ${it.localizedMessage}" }
        }
    }

    fun importSettingsJson(raw: String) {
        viewModelScope.launch {
            runCatching { repository.importSettingsJson(raw) }
                .onSuccess { _toast.value = "Settings imported." }
                .onFailure { _toast.value = "Import failed: ${it.localizedMessage}" }
        }
    }

    fun clearAllStudyData() {
        viewModelScope.launch {
            repository.clearAllStudyData()
            _toast.value = "Study data cleared. Default workspace restored."
        }
    }

    fun regeneratePairingCode() {
        val code = "FF-" + (1000..9999).random()
        updateSettings { it.copy(wifiSyncCode = code) }
    }

    fun discoverLanPeers() {
        viewModelScope.launch {
            _lanBusy.value = true
            _lanMsg.value = "Scanning local Wi-Fi network..."
            try {
                val peers = lanSyncManager.discoverPeers(uiState.value.settings.wifiSyncCode)
                _lanPeers.value = peers
                _lanMsg.value = if (peers.isEmpty()) {
                    "No FocusFlow peers responded for code ${uiState.value.settings.wifiSyncCode}. Ensure both devices share the same Wi-Fi and code."
                } else {
                    "Found ${peers.size} peer(s) on local Wi-Fi."
                }
            } catch (e: Exception) {
                _lanMsg.value = "Discovery error: ${e.localizedMessage}"
            } finally {
                _lanBusy.value = false
            }
        }
    }

    fun syncWithLanPeer(peer: LanPeerInfo) {
        viewModelScope.launch {
            _lanBusy.value = true
            _lanMsg.value = "Syncing with ${peer.deviceName}..."
            try {
                val localJson = repository.exportDatabaseJson()
                val (peerName, remoteJson) = lanSyncManager.syncWithPeer(
                    hostAddress = peer.hostAddress,
                    port = peer.tcpPort,
                    pairingCode = uiState.value.settings.wifiSyncCode,
                    localSnapshotJson = localJson
                )
                repository.importDatabaseJson(remoteJson)
                repository.updateSettings {
                    it.copy(
                        wifiSyncLastSyncedAt = System.currentTimeMillis(),
                        wifiSyncLastPeer = peerName
                    )
                }
                _lanMsg.value = "Synced successfully with $peerName."
            } catch (e: Exception) {
                _lanMsg.value = "LAN sync failed: ${e.localizedMessage}"
            } finally {
                _lanBusy.value = false
            }
        }
    }

    fun createNewDraftId(): String = UUID.randomUUID().toString()
}

private data class BaseDataSlice(
    val settings: AppSettings,
    val providers: List<AiProviderProfile>,
    val subjects: List<SubjectEntity>,
    val chapters: List<ChapterEntity>,
    val studyTasks: List<StudyTaskEntity>
)

private data class SessionDataSlice(
    val subtasks: List<SubtaskEntity>,
    val sessions: List<FocusSessionEntity>,
    val sessionChapters: List<SessionChapterEntity>,
    val sessionTasks: List<SessionTaskEntity>,
    val breakConfig: BreakConfigEntity
)

private data class AuxDataSlice(
    val presets: List<PresetEntity>,
    val presetChapters: List<PresetChapterEntity>,
    val presetTasks: List<PresetTaskEntity>,
    val blockedApps: List<BlockedAppEntity>,
    val allowedApps: List<AllowedAppEntity>
)

private data class MetaDataSlice(
    val customCatalog: List<CustomCatalogAppEntity>,
    val summaries: List<SessionSummaryEntity>,
    val widgets: List<DashboardWidgetEntity>
)

private data class NavTransient(
    val route: FocusRoute,
    val backStack: List<FocusRoute>,
    val nowMs: Long,
    val quoteIndex: Int,
    val permissions: PermissionHealth
)

private data class BannerTransient(
    val alert: ActiveShieldAlert?,
    val guardBanner: String?,
    val snapGptBanner: String?,
    val toast: String?
)

private data class VoiceAiTransient(
    val listening: Boolean,
    val partial: String,
    val error: String?,
    val aiBusy: Boolean,
    val aiError: String?
)

private data class ExtraAiTransient(
    val aiNotice: String?,
    val summaryGenId: Long?,
    val summaryError: String?,
    val providerTestStatus: String?
)

private data class LanTransient(
    val peers: List<LanPeerInfo>,
    val message: String?,
    val busy: Boolean
)

private data class AllTransient(
    val nav: NavTransient,
    val banner: BannerTransient,
    val voiceAi: VoiceAiTransient,
    val extraAi: ExtraAiTransient,
    val lan: LanTransient
)
