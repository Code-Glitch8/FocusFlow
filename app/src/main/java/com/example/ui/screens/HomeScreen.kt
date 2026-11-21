package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DashboardWidgetEntity
import com.example.data.local.SessionTaskEntity
import com.example.domain.SessionClock
import com.example.domain.StudyPlanParser
import com.example.model.DraftStudyTask
import com.example.model.FocusMode
import com.example.model.ProgressRange
import com.example.model.SessionStatus
import com.example.model.SubjectPlan
import com.example.model.TaskOrigin
import com.example.model.WidgetAction
import com.example.model.WidgetSize
import com.example.model.WidgetType
import com.example.nativebridge.ShieldStateBridge
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.MiniBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.components.widgetIconToVector
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.toColor
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: FocusFlowUiState,
    onEditDashboard: () -> Unit,
    onOpenWidget: (DashboardWidgetEntity) -> Unit,
    onQuickStartWidget: (DashboardWidgetEntity) -> Unit,
    onUpdateProgressRange: (DashboardWidgetEntity, ProgressRange) -> Unit,
    onNewQuote: () -> Unit,
    onOpenCustomSession: (openVoice: Boolean) -> Unit,
    onStartVoice: ((String) -> Unit) -> Unit,
    onStopVoice: () -> Unit,
    onParseLocal: (String, List<SubjectPlan>, TaskOrigin) -> List<DraftStudyTask>,
    onStartVoiceCircleSession: (
        label: String,
        durationMinutes: Int,
        rawPlan: String,
        subjectPlans: List<SubjectPlan>,
        draftTasks: List<DraftStudyTask>
    ) -> Unit,
    onToggleSessionTask: (SessionTaskEntity) -> Unit,
    onStartBreak: (Boolean) -> Unit,
    onEndBreakEarly: () -> Unit,
    onStopSession: () -> Unit,
    onOpenFocusTab: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSessionSummary: (Long) -> Unit,
    onDismissSnapGptBanner: () -> Unit,
    onPermissionsRefreshed: () -> Unit
) {
    val context = LocalContext.current
    var inlinePlanInput by remember { mutableStateOf("") }
    var durationAdjustment by remember { mutableIntStateOf(0) }

    LaunchedEffect(inlinePlanInput) {
        durationAdjustment = 0
    }

    val parsedTasks = remember(inlinePlanInput, state.subjects, state.chapters) {
        if (inlinePlanInput.isBlank()) emptyList()
        else onParseLocal(inlinePlanInput, emptyList(), TaskOrigin.AI_TEXT)
    }
    val hasExplicitDuration = remember(inlinePlanInput) {
        StudyPlanParser.hasExplicitDuration(inlinePlanInput)
    }
    val detectedSubjectIds = remember(parsedTasks) {
        parsedTasks.mapNotNull { it.subjectId }.distinct()
    }
    val detectedChapterIds = remember(parsedTasks) {
        parsedTasks.mapNotNull { it.chapterId }.distinct()
    }
    val subjectPlans = remember(detectedSubjectIds, detectedChapterIds, state.subjects, state.chapters) {
        detectedSubjectIds.mapNotNull { sId ->
            val subj = state.subjects.find { it.id == sId } ?: return@mapNotNull null
            val chIds = detectedChapterIds.filter { chId ->
                state.chapters.any { it.id == chId && it.subjectId == sId }
            }
            val chSummary = chIds.mapNotNull { chId -> state.chapters.find { it.id == chId }?.name }
                .joinToString(", ")
            SubjectPlan(sId, subj.name, chIds, chSummary)
        }
    }
    val baseEstimatedMinutes = remember(inlinePlanInput, parsedTasks) {
        val sum = parsedTasks.sumOf { it.estimatedMinutes }
        if (sum > 0) sum.coerceIn(10, 240) else StudyPlanParser.estimateMinutes(inlinePlanInput)
    }
    val finalMinutes = (baseEstimatedMinutes + durationAdjustment).coerceIn(10, 240)

    val triggerInlineVoice = {
        if (state.voiceListening) {
            onStopVoice()
        } else {
            onStartVoice { transcript ->
                inlinePlanInput = if (inlinePlanInput.isBlank()) transcript else "$inlinePlanInput\n$transcript"
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionsRefreshed()
        if (granted && !state.voiceListening) {
            onStartVoice { transcript ->
                inlinePlanInput = if (inlinePlanInput.isBlank()) transcript else "$inlinePlanInput\n$transcript"
            }
        }
    }

    val smallWidgets = state.widgets.filter { it.size == WidgetSize.SMALL }
    val largeWidgets = state.widgets.filter { it.size == WidgetSize.LARGE }
    val recentSessions = state.sessions
        .filter { it.status != SessionStatus.ACTIVE }
        .take(3)

    val needsOverlayOrShield = !state.permissionHealth.overlayGranted ||
        (!state.permissionHealth.accessibilityEnabled && !state.permissionHealth.usageStatsGranted)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    TextButton(
                        onClick = onEditDashboard,
                        modifier = Modifier.testTag("home_edit_dashboard_button")
                    ) {
                        Text("Edit dashboard")
                    }
                }
            }

            if (needsOverlayOrShield) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "Permissions",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Enable background timer & shield overlays",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "Grant Display over other apps so the floating timer circle and blocked-app shield appear across your phone when FocusFlow is closed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!state.permissionHealth.overlayGranted) {
                                    OutlinedButton(
                                        onClick = { ShieldStateBridge.openOverlaySettings(context) },
                                        modifier = Modifier.height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Enable overlay", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (!state.permissionHealth.accessibilityEnabled) {
                                    OutlinedButton(
                                        onClick = { ShieldStateBridge.openAccessibilitySettings(context) },
                                        modifier = Modifier.height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Enable shield", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!state.snapGptBanner.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.snapGptBanner,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Target app: ${state.settings.snapGptPackage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = onDismissSnapGptBanner,
                                modifier = Modifier.testTag("home_dismiss_snapgpt_banner")
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            // Active Session Card directly manageable from Home
            if (state.activeSession != null && state.activeTime != null) {
                item {
                    val active = state.activeSession
                    val time = state.activeTime
                    val activeTasks = state.sessionTasks.filter { it.sessionId == active.id }
                    val doneTasks = activeTasks.count { it.completed }
                    val earned = state.activeEarnedBreaks
                    val clockText = when {
                        time.isOnBreak -> SessionClock.formatClock(time.breakRemainingMs)
                        time.remainingMs != null -> SessionClock.formatClock(time.remainingMs)
                        else -> SessionClock.formatClock(time.focusElapsedMs)
                    }
                    FocusFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (time.isOnBreak) "ON BREAK" else "SESSION IN PROGRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = active.label,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${SessionClock.formatMinutesCompact((time.focusElapsedMs / 60_000L).toInt())} focused · $doneTasks/${activeTasks.size} tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = clockText,
                                        style = MonoMetricSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (time.isOnBreak) MaterialTheme.colorScheme.onSecondary
                                        else MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        if (activeTasks.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                activeTasks.forEach { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onToggleSessionTask(task) }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = task.completed,
                                            onCheckedChange = { onToggleSessionTask(task) }
                                        )
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${task.estimatedMinutes}m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (time.isOnBreak) {
                                OutlinedButton(
                                    onClick = onEndBreakEarly,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("End break")
                                }
                            } else if (earned != null && (earned.shortAvailable > 0 || earned.longAvailable > 0)) {
                                val useLong = earned.longAvailable > 0
                                OutlinedButton(
                                    onClick = { onStartBreak(useLong) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (useLong) "Long break" else "Short break")
                                }
                            }
                            OutlinedButton(
                                onClick = onStopSession,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Finish")
                            }
                            Button(
                                onClick = onOpenFocusTab,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_open_active_session_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Focus view")
                            }
                        }
                    }
                }
            }

            // Inline Quick Study Plan Box directly on Home (auto-extracts subjects, tasks & duration)
            item {
                FocusFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Quick study plan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "QUICK STUDY START",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.voiceListening) {
                            TextButton(onClick = onStopVoice) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop voice", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop mic")
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Say or type what you're studying — FocusFlow sets subject, tasks & calculates duration automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inlinePlanInput,
                        onValueChange = { inlinePlanInput = it },
                        placeholder = { Text("e.g. I am studying maths doing quadratic equations") },
                        minLines = 2,
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (state.permissionHealth.microphoneGranted) {
                                        triggerInlineVoice()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.testTag("home_voice_start_button")
                            ) {
                                Icon(
                                    imageVector = if (state.voiceListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = if (state.voiceListening) "Stop dictation" else "Speak study plan",
                                    tint = if (state.voiceListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_inline_plan_input")
                    )

                    if (state.voiceListening && state.voicePartial.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = state.voicePartial,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
                    }

                    state.voiceError?.let { err ->
                        Spacer(Modifier.height(6.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    if (inlinePlanInput.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val subjLabel = subjectPlans.joinToString(" + ") { sp ->
                                    if (sp.chaptersSummary.isNotBlank()) "${sp.subjectName} (${sp.chaptersSummary})"
                                    else sp.subjectName
                                }.ifBlank { "General focus" }
                                val timeSourceLabel = if (hasExplicitDuration) "${finalMinutes}m" else "~${finalMinutes}m auto-estimated"
                                Text(
                                    text = "$subjLabel · $timeSourceLabel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                parsedTasks.forEach { dt ->
                                    Text(
                                        text = "• ${dt.title} (~${dt.estimatedMinutes}m)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Adjust timer:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { durationAdjustment -= 5 },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("-5m", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { durationAdjustment += 5 },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("+5m", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onStopVoice()
                                val sessionLabel = subjectPlans.joinToString(" + ") { it.subjectName }
                                    .ifBlank { parsedTasks.firstOrNull()?.title ?: "Quick study focus" }
                                onStartVoiceCircleSession(
                                    sessionLabel,
                                    finalMinutes,
                                    inlinePlanInput,
                                    subjectPlans,
                                    parsedTasks
                                )
                                inlinePlanInput = ""
                                durationAdjustment = 0
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("home_inline_start_focus_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start focus now ($finalMinutes min)")
                        }
                    }
                }
            }

            // Quote Motivation Card
            item {
                val quote = state.currentQuote
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = "Study motivation",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "STUDY MOTIVATION · ${quote.topic.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onNewQuote,
                            modifier = Modifier.testTag("home_new_quote_button"),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("New quote", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "\"${quote.quote}\"",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "— ${quote.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Today Summary Card
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(text = "Today")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TodayMetric(
                            value = SessionClock.formatMinutesCompact(state.stats.todayFocusMinutes),
                            label = "focused",
                            modifier = Modifier.weight(1f)
                        )
                        TodayMetric(
                            value = state.stats.todayCompletedTasks.toString(),
                            label = "tasks done",
                            modifier = Modifier.weight(1f)
                        )
                        TodayMetric(
                            value = state.stats.streakDays.toString(),
                            label = "day streak",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Widgets Section
            item {
                SectionHeader(text = "Widgets", modifier = Modifier.padding(top = 4.dp))
            }

            if (twoColumns) {
                items(smallWidgets.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { widget ->
                            SmallWidgetCard(
                                widget = widget,
                                subjectName = state.subjects.find { it.id == widget.subjectId }?.name,
                                presetTaskCount = state.settings.widgetPresetTasks[widget.id]?.size ?: 0,
                                onClick = { onOpenWidget(widget) },
                                onQuickStart = if (widget.action == WidgetAction.START_SESSION && widget.type != WidgetType.PROGRESS && widget.type != WidgetType.QUOTE) {
                                    { onQuickStartWidget(widget) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(smallWidgets, key = { "w_${it.id}" }) { widget ->
                    SmallWidgetCard(
                        widget = widget,
                        subjectName = state.subjects.find { it.id == widget.subjectId }?.name,
                        presetTaskCount = state.settings.widgetPresetTasks[widget.id]?.size ?: 0,
                        onClick = { onOpenWidget(widget) },
                        onQuickStart = if (widget.action == WidgetAction.START_SESSION && widget.type != WidgetType.PROGRESS && widget.type != WidgetType.QUOTE) {
                            { onQuickStartWidget(widget) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(largeWidgets, key = { "lw_${it.id}" }) { widget ->
                if (widget.type == WidgetType.PROGRESS) {
                    val bars = when (widget.progressRange) {
                        ProgressRange.DAYS -> state.stats.dailyMinutes
                        ProgressRange.WEEKS -> state.stats.weeklyMinutes
                        ProgressRange.MONTH -> state.stats.monthlyMinutes
                    }
                    val totalRangeMins = bars.sumOf { it.second }
                    val rangeLabel = when (widget.progressRange) {
                        ProgressRange.DAYS -> "${SessionClock.formatMinutesCompact(totalRangeMins)} in past 7 days"
                        ProgressRange.WEEKS -> "${SessionClock.formatMinutesCompact(totalRangeMins)} in past 4 weeks"
                        ProgressRange.MONTH -> "${SessionClock.formatMinutesCompact(totalRangeMins)} in past 30 days"
                    }
                    FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = widget.title.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = rangeLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = onOpenStatistics,
                                modifier = Modifier.testTag("home_widget_full_stats")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Full stats")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProgressRange.entries.forEach { range ->
                                FilterChip(
                                    selected = widget.progressRange == range,
                                    onClick = { onUpdateProgressRange(widget, range) },
                                    label = { Text(range.label) },
                                    modifier = Modifier.testTag("home_progress_range_${range.name.lowercase()}")
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        MiniBarChart(
                            bars = bars,
                            barColor = widget.accent.toColor()
                        )
                    }
                } else {
                    SmallWidgetCard(
                        widget = widget,
                        subjectName = state.subjects.find { it.id == widget.subjectId }?.name,
                        presetTaskCount = state.settings.widgetPresetTasks[widget.id]?.size ?: 0,
                        onClick = { onOpenWidget(widget) },
                        onQuickStart = if (widget.action == WidgetAction.START_SESSION && widget.type != WidgetType.QUOTE) {
                            { onQuickStartWidget(widget) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Custom Session button
            item {
                OutlinedButton(
                    onClick = { onOpenCustomSession(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("home_custom_session_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Custom session planner...")
                }
            }

            // Chapters This Week
            if (state.stats.chaptersThisWeek.isNotEmpty()) {
                item {
                    SectionHeader(text = "Chapters this week", modifier = Modifier.padding(top = 4.dp))
                    val maxChMins = max(1, state.stats.chaptersThisWeek.maxOf { it.weekMinutes })
                    FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.stats.chaptersThisWeek.forEach { ch ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ch.chapterName,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${ch.subjectName} · ${ch.doneTasks} done, ${ch.openTasks} open",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = SessionClock.formatMinutesCompact(ch.weekMinutes),
                                            style = MonoMetricSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (ch.weekMinutes.toFloat() / maxChMins.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Sessions (last 3 + See all history)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader(text = "Recent sessions")
                    TextButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("home_see_all_history")
                    ) {
                        Text("See all history")
                    }
                }
                if (recentSessions.isEmpty()) {
                    Text(
                        text = "No sessions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(recentSessions, key = { "recent_${it.id}" }) { session ->
                val snap = SessionClock.computeSnapshot(session, state.nowMs)
                val focusMins = (snap.focusElapsedMs / 60_000L).toInt()
                val dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(session.startedAt))
                FocusFlowCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_recent_session_${session.id}"),
                    onClick = { onOpenSessionSummary(session.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = session.label, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$dateLabel · ${SessionClock.formatMinutesCompact(focusMins)} focused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open summary",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TodayMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MonoMetricLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmallWidgetCard(
    widget: DashboardWidgetEntity,
    subjectName: String?,
    presetTaskCount: Int,
    onClick: () -> Unit,
    onQuickStart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = widget.accent.toColor()
    val subtitle = buildString {
        append(
            when (widget.type) {
                WidgetType.TIMER -> "${widget.durationMinutes ?: 25} min"
                WidgetType.OPEN_ENDED -> "Open-ended"
                WidgetType.SUBJECT -> buildString {
                    append(subjectName ?: "Subject")
                    if (widget.mode == FocusMode.TIMED) {
                        append(" · ${widget.durationMinutes ?: 60} min")
                    } else {
                        append(" · Open")
                    }
                }
                WidgetType.PROGRESS -> widget.progressRange.label
                WidgetType.QUOTE -> "Daily motivation"
            }
        )
        if (presetTaskCount > 0) {
            append(" · $presetTaskCount task(s)")
        }
    }

    FocusFlowCard(
        modifier = modifier.testTag("widget_tile_${widget.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = widgetIconToVector(widget.icon),
                    contentDescription = widget.title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = widget.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onQuickStart != null) {
                IconButton(
                    onClick = onQuickStart,
                    modifier = Modifier.testTag("widget_quick_start_${widget.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Start ${widget.title}",
                        tint = accentColor
                    )
                }
            }
        }
    }
}
