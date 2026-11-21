package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.SessionClock
import com.example.model.FocusMode
import com.example.nativebridge.AppLauncherHelper
import com.example.nativebridge.ShieldOverlayManager
import com.example.ui.components.FocusFlowBrandMark
import com.example.ui.components.FocusShieldDialog
import com.example.ui.screens.AiProvidersScreen
import com.example.ui.screens.AiSettingsScreen
import com.example.ui.screens.BlockingScreen
import com.example.ui.screens.DataSettingsScreen
import com.example.ui.screens.DetectorTestScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SessionPrepScreen
import com.example.ui.screens.SessionSummaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.SubjectsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WidgetsScreen
import com.example.ui.theme.FocusFlowTheme
import com.example.ui.theme.MonoMetricMedium

@Composable
fun FocusFlowApp(viewModel: FocusFlowViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    FocusFlowTheme(themeMode = state.settings.themeMode) {
        val currentTab = (state.currentRoute as? FocusRoute.Tab)?.tab
        val showChrome = currentTab != null

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 720.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showChrome && !isExpanded) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            TopTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { viewModel.selectTopTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showChrome && isExpanded) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxHeight().width(216.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight().padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FocusFlowBrandMark(size = 38.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = stringResource(R.string.workspace_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(18.dp))
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                ) {
                                    TopTab.entries.forEach { tab ->
                                        NavigationRailItem(
                                            selected = currentTab == tab,
                                            onClick = { viewModel.selectTopTab(tab) },
                                            icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }

                                // Sidebar Mini Active Timer Card matching desktop screenshot
                                val active = state.activeSession
                                val time = state.activeTime
                                if (active != null && time != null) {
                                    val clockStr = when {
                                        time.isOnBreak -> SessionClock.formatClock(time.breakRemainingMs)
                                        time.remainingMs != null -> SessionClock.formatClock(time.remainingMs)
                                        else -> SessionClock.formatClock(time.focusElapsedMs)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.background,
                                        border = BorderStroke(
                                            1.dp,
                                            if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectTopTab(TopTab.FOCUS) }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (time.isOnBreak) "ON BREAK" else "ACTIVE FOCUS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                                                else MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(active.label, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(4.dp))
                                            Text(clockStr, style = MonoMetricMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (val route = state.currentRoute) {
                            is FocusRoute.Tab -> when (route.tab) {
                                TopTab.HOME -> HomeScreen(
                                    state = state,
                                    onEditDashboard = { viewModel.navigateTo(FocusRoute.Widgets) },
                                    onOpenWidget = viewModel::openWidget,
                                    onQuickStartWidget = { widget -> viewModel.startWidgetSession(widget, launchMinimized = true) },
                                    onUpdateProgressRange = viewModel::updateWidgetProgressRange,
                                    onNewQuote = viewModel::nextQuote,
                                    onOpenCustomSession = { openVoice ->
                                        viewModel.navigateTo(
                                            FocusRoute.SessionPrep(
                                                title = "Custom session",
                                                mode = FocusMode.TIMED,
                                                durationMinutes = 25,
                                                strictMode = state.settings.strictDefault,
                                                openVoiceOnLaunch = openVoice
                                            )
                                        )
                                    },
                                    onStartVoice = viewModel::startVoiceCapture,
                                    onStopVoice = viewModel::stopVoiceCapture,
                                    onParseLocal = viewModel::parseLocalTasks,
                                    onStartVoiceCircleSession = { label, mins, rawPlan, plans, tasks ->
                                        viewModel.startStudySession(
                                            label = label,
                                            mode = FocusMode.TIMED,
                                            plannedDurationMinutes = mins,
                                            strictMode = state.settings.strictDefault,
                                            rawStudyPlan = rawPlan,
                                            subjectPlans = plans,
                                            draftTasks = tasks,
                                            selectedStudyTaskIds = emptyList(),
                                            widgetId = null,
                                            launchMinimized = true
                                        )
                                    },
                                    onToggleSessionTask = viewModel::toggleSessionTask,
                                    onStartBreak = viewModel::startBreak,
                                    onEndBreakEarly = viewModel::endBreakEarly,
                                    onStopSession = { viewModel.stopActiveSession(abandoned = false) },
                                    onOpenFocusTab = { viewModel.selectTopTab(TopTab.FOCUS) },
                                    onOpenStatistics = { viewModel.selectTopTab(TopTab.STATISTICS) },
                                    onOpenHistory = { viewModel.navigateTo(FocusRoute.History) },
                                    onOpenSessionSummary = { id -> viewModel.navigateTo(FocusRoute.SessionSummary(id)) },
                                    onDismissSnapGptBanner = viewModel::dismissSnapGptBanner,
                                    onPermissionsRefreshed = viewModel::refreshPermissions
                                )
                                TopTab.TASKS -> TasksScreen(
                                    state = state,
                                    onToggleTask = viewModel::toggleStudyTask,
                                    onToggleSubtask = viewModel::toggleSubtask,
                                    onSaveTask = viewModel::saveStudyTask,
                                    onDeleteTask = viewModel::deleteStudyTask,
                                    onManageSubjects = { viewModel.navigateTo(FocusRoute.Subjects) }
                                )
                                TopTab.FOCUS -> FocusScreen(
                                    state = state,
                                    onQuickStartWidget = { title, mode, mins ->
                                        viewModel.navigateTo(
                                            FocusRoute.SessionPrep(
                                                title = title,
                                                mode = mode,
                                                durationMinutes = mins,
                                                strictMode = state.settings.strictDefault
                                            )
                                        )
                                    },
                                    onOpenCustomSession = { openVoice ->
                                        viewModel.navigateTo(
                                            FocusRoute.SessionPrep(
                                                title = "Custom session",
                                                mode = FocusMode.TIMED,
                                                durationMinutes = 25,
                                                strictMode = state.settings.strictDefault,
                                                openVoiceOnLaunch = openVoice
                                            )
                                        )
                                    },
                                    onToggleSessionTask = viewModel::toggleSessionTask,
                                    onAddTaskToSession = viewModel::addTaskToActiveSession,
                                    onDeleteSessionTask = viewModel::deleteSessionTask,
                                    onStartBreak = viewModel::startBreak,
                                    onEndBreakEarly = viewModel::endBreakEarly,
                                    onUpdateDuration = viewModel::updateActiveSessionDuration,
                                    onStopSession = viewModel::stopActiveSession,
                                    onTestGuardTarget = viewModel::testOpenTarget,
                                    onDismissGuardBanner = viewModel::dismissGuardBanner
                                )
                                TopTab.STATISTICS -> StatisticsScreen(
                                    state = state,
                                    onRateChapter = viewModel::setChapterConfidence,
                                    onOpenHistory = { viewModel.navigateTo(FocusRoute.History) }
                                )
                                TopTab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    onSetThemeMode = { tm -> viewModel.updateSettings { it.copy(themeMode = tm) } },
                                    onToggleBubble = { b -> viewModel.updateSettings { it.copy(assistantBubbleEnabled = b) } },
                                    onToggleStrictDefault = { s -> viewModel.updateSettings { it.copy(strictDefault = s) } },
                                    onNavigate = viewModel::navigateTo
                                )
                            }

                            FocusRoute.Widgets -> WidgetsScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onSaveWidgetWithTasks = viewModel::saveWidgetWithTasks,
                                onDeleteWidget = viewModel::deleteWidget,
                                onMoveWidget = viewModel::moveWidget,
                                onResetDefaults = viewModel::resetWidgets,
                                onShowToast = viewModel::showToast
                            )

                            FocusRoute.Subjects -> SubjectsScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onAddSubject = viewModel::addSubject,
                                onRenameSubject = viewModel::renameSubject,
                                onDeleteSubject = viewModel::deleteCustomSubject,
                                onAddChapter = viewModel::addChapter,
                                onRenameChapter = viewModel::renameChapter,
                                onDeleteChapter = viewModel::deleteChapter
                            )

                            is FocusRoute.SessionPrep -> SessionPrepScreen(
                                route = route,
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onParseLocal = viewModel::parseLocalTasks,
                                onGenerateAiTasks = viewModel::generateAiTasks,
                                onClearAiMessages = viewModel::clearAiMessages,
                                onStartVoice = viewModel::startVoiceCapture,
                                onStopVoice = viewModel::stopVoiceCapture,
                                onSavePreset = viewModel::savePreset,
                                onDeletePreset = viewModel::deletePreset,
                                onAddSubject = viewModel::addSubject,
                                onAddChapter = viewModel::addChapter,
                                onStartSession = viewModel::startStudySession
                            )

                            is FocusRoute.SessionSummary -> SessionSummaryScreen(
                                sessionId = route.sessionId,
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onGenerateSummary = viewModel::generateSessionSummary,
                                onEditSession = viewModel::updateSessionDetails,
                                onDeleteSession = viewModel::deleteSession
                            )

                            FocusRoute.History -> HistoryScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onOpenSummary = { id -> viewModel.navigateTo(FocusRoute.SessionSummary(id)) }
                            )

                            FocusRoute.Blocking -> BlockingScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onRefreshPermissions = viewModel::refreshPermissions,
                                onUpdateSettings = viewModel::updateSettings,
                                onSaveBreakConfig = viewModel::saveBreakConfig,
                                onToggleBlockedApp = viewModel::toggleBlockedApp,
                                onSetBlockedDuringBreak = viewModel::setBlockedDuringBreak,
                                onToggleAllowedApp = viewModel::toggleAllowedApp,
                                onAddCustomApp = viewModel::addCustomApp,
                                onTestGuardTarget = viewModel::testOpenTarget,
                                onDismissGuardBanner = viewModel::dismissGuardBanner,
                                onOpenDetectorTest = { viewModel.navigateTo(FocusRoute.DetectorTest) }
                            )

                            FocusRoute.DetectorTest -> DetectorTestScreen(
                                onBack = { viewModel.navigateBack() }
                            )

                            FocusRoute.AiSettings -> AiSettingsScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onUpdateSettings = viewModel::updateSettings,
                                onOpenProviders = { viewModel.navigateTo(FocusRoute.AiProviders) },
                                onShowToast = viewModel::showToast
                            )

                            FocusRoute.AiProviders -> AiProvidersScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onSelectProvider = viewModel::selectActiveProvider,
                                onUpsertProvider = viewModel::upsertProvider,
                                onDeleteProvider = viewModel::deleteProvider,
                                onTestProvider = viewModel::testProvider
                            )

                            FocusRoute.DataSettings -> DataSettingsScreen(
                                state = state,
                                onBack = { viewModel.navigateBack() },
                                onExportDatabaseJson = viewModel::exportDatabaseJson,
                                onImportDatabaseJson = viewModel::importDatabaseJson,
                                onExportSettingsJson = viewModel::exportSettingsJson,
                                onImportSettingsJson = viewModel::importSettingsJson,
                                onRegenerateSyncCode = viewModel::regeneratePairingCode,
                                onDiscoverPeers = viewModel::discoverLanPeers,
                                onSyncWithPeer = viewModel::syncWithLanPeer,
                                onClearStudyData = viewModel::clearAllStudyData,
                                onShowToast = viewModel::showToast
                            )
                        }

                        // System-wide floating timer circle appears automatically outside FocusFlow via FloatingOverlayManager.
                    }
                }
            }

            // Focus Shield Alert Dialog (for inside-app guard tests)
            state.shieldAlert?.let { alert ->
                val active = state.activeSession
                val time = state.activeTime
                val clockLabel = when {
                    time == null -> "00:00"
                    time.isOnBreak -> SessionClock.formatClock(time.breakRemainingMs)
                    time.remainingMs != null -> SessionClock.formatClock(time.remainingMs)
                    else -> SessionClock.formatClock(time.focusElapsedMs)
                }
                FocusShieldDialog(
                    alert = alert,
                    sessionLabel = active?.label,
                    onBreak = time?.isOnBreak == true,
                    clockLabel = clockLabel,
                    onCloseBlockedApp = {
                        viewModel.dismissShieldAlert()
                        ShieldOverlayManager.dismissOverlay(context)
                    },
                    onOpenRegularYoutube = {
                        viewModel.dismissShieldAlert()
                        ShieldOverlayManager.dismissOverlay(context)
                        if (!state.settings.blockYoutubeApp) {
                            AppLauncherHelper.openYoutube(context, shorts = false)
                        } else {
                            viewModel.showToast("Whole YouTube app block is enabled.")
                        }
                    },
                    onReturnToFocusFlow = {
                        viewModel.dismissShieldAlert()
                        ShieldOverlayManager.dismissOverlay(context)
                        viewModel.selectTopTab(TopTab.FOCUS)
                    },
                    onUnblockApp = { viewModel.unblockAppFromShield(alert.packageName) }
                )
            }
        }
    }
}

private fun tabIcon(tab: TopTab) = when (tab) {
    TopTab.HOME -> Icons.Filled.Home
    TopTab.TASKS -> Icons.Filled.CheckBox
    TopTab.FOCUS -> Icons.Filled.Timer
    TopTab.STATISTICS -> Icons.Filled.BarChart
    TopTab.SETTINGS -> Icons.Filled.Settings
}
