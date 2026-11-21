package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.local.SessionTaskEntity
import com.example.domain.SessionClock
import com.example.model.FocusMode
import com.example.ui.FocusFlowUiState
import com.example.ui.components.AppGuardBar
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.SectionHeader

@Composable
fun FocusScreen(
    state: FocusFlowUiState,
    onQuickStartWidget: (title: String, mode: FocusMode, durationMinutes: Int) -> Unit,
    onOpenCustomSession: (openVoice: Boolean) -> Unit,
    onToggleSessionTask: (SessionTaskEntity) -> Unit,
    onAddTaskToSession: (title: String, minutes: Int) -> Unit,
    onDeleteSessionTask: (Long) -> Unit,
    onStartBreak: (isLong: Boolean) -> Unit,
    onEndBreakEarly: () -> Unit,
    onUpdateDuration: (Int?) -> Unit,
    onStopSession: (abandoned: Boolean) -> Unit,
    onTestGuardTarget: (String, String, Boolean) -> Unit,
    onDismissGuardBanner: () -> Unit
) {
    val active = state.activeSession
    val time = state.activeTime

    if (active == null || time == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Focus", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Start a timed study block or open-ended focus session to enable Focus Shield and earned breaks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Quick launch")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onQuickStartWidget("Focus 25", FocusMode.TIMED, 25) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("focus_quick_25"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Focus 25")
                        }
                        OutlinedButton(
                            onClick = { onQuickStartWidget("Open focus", FocusMode.OPEN_ENDED, 25) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("focus_quick_open"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open focus")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenCustomSession(false) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("focus_custom_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Custom session...")
                        }
                        OutlinedButton(
                            onClick = { onOpenCustomSession(true) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("focus_voice_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Voice start")
                        }
                    }
                }
            }
        }
        return
    }

    val sessionTasks = state.sessionTasks.filter { it.sessionId == active.id }
    val doneCount = sessionTasks.count { it.completed }
    val earned = state.activeEarnedBreaks

    var showEditDurationDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showEmergencyExitDialog by remember { mutableStateOf(false) }

    val mainClock = when {
        time.isOnBreak -> SessionClock.formatClock(time.breakRemainingMs)
        time.remainingMs != null -> SessionClock.formatClock(time.remainingMs)
        else -> SessionClock.formatClock(time.focusElapsedMs)
    }

    val progressFraction = if (time.totalPlannedMs != null && time.totalPlannedMs > 0L) {
        (time.focusElapsedMs.toFloat() / time.totalPlannedMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Timer Card
        item {
            FocusFlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (time.isOnBreak) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (time.isOnBreak) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (time.isOnBreak) Icons.Filled.Coffee else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    time.isOnBreak -> "ON BREAK"
                                    active.strictMode -> "STRICT SHIELD"
                                    else -> "RELAXED SESSION"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (time.isOnBreak) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    TextButton(
                        onClick = { showEditDurationDialog = true },
                        modifier = Modifier.testTag("focus_edit_duration_button")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit duration")
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(text = active.label, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mainClock,
                    style = MaterialTheme.typography.displayLarge,
                    color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(10.dp))
                if (time.totalPlannedMs != null) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Focused ${SessionClock.formatClock(time.focusElapsedMs)} of ${SessionClock.formatClock(time.totalPlannedMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Open-ended focus · ${SessionClock.formatClock(time.focusElapsedMs)} elapsed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // App Guard & YouTube Tester
        item {
            FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("App guard & YouTube tester")
                Text(
                    text = "Test opening YouTube, YouTube Shorts, or blocked apps. Blocked targets trigger the Focus Shield overlay until you earn a break or unblock them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                AppGuardBar(
                    sessionActive = true,
                    onBreak = time.isOnBreak,
                    settings = state.settings,
                    blockedApps = state.blockedApps,
                    allowedApps = state.allowedApps,
                    statusBanner = state.guardStatusBanner,
                    onDismissBanner = onDismissGuardBanner,
                    onTestTarget = onTestGuardTarget
                )
            }
        }

        // Breaks Card
        item {
            FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Breaks")
                if (time.isOnBreak) {
                    Text(
                        text = "Enjoy your break. Focus resumes automatically when it ends (${SessionClock.formatClock(time.breakRemainingMs)} left).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onEndBreakEarly,
                        modifier = Modifier.testTag("focus_end_break_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("End break now")
                    }
                } else if (!state.breakConfig.enabled) {
                    Text(
                        text = "Earned breaks are disabled in Blocking & Shield settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (earned != null) {
                    val shortAvail = earned.shortAvailable
                    val longAvail = earned.longAvailable
                    Text(
                        text = "Earned breaks available: $shortAvail short (${state.breakConfig.shortBreakMinutes}m), $longAvail long (${state.breakConfig.longBreakMinutes}m).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Complete ${state.breakConfig.tasksForShortBreak} task(s) for a short break, ${state.breakConfig.tasksForLongBreak} for a long break.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onStartBreak(false) },
                            enabled = shortAvail > 0,
                            modifier = Modifier.weight(1f).testTag("focus_start_short_break"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Short break (${state.breakConfig.shortBreakMinutes}m)")
                        }
                        OutlinedButton(
                            onClick = { onStartBreak(true) },
                            enabled = longAvail > 0,
                            modifier = Modifier.weight(1f).testTag("focus_start_long_break"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Long break (${state.breakConfig.longBreakMinutes}m)")
                        }
                    }
                }
            }
        }

        // Session Tasks
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader("Tasks · $doneCount/${sessionTasks.size} done")
                OutlinedButton(
                    onClick = { showAddTaskDialog = true },
                    modifier = Modifier.testTag("focus_add_task_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add task")
                }
            }
        }

        if (sessionTasks.isEmpty()) {
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No tasks queued for this session. Add a task above to earn breaks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(sessionTasks, key = { it.id }) { task ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("focus_task_${task.id}")
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleSessionTask(task) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (task.completed) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = if (task.completed) "Mark incomplete" else "Mark complete",
                            tint = if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "~${task.estimatedMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteSessionTask(task.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove task", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Stop / Emergency Exit
        item {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    if (active.strictMode && (time.remainingMs == null || time.remainingMs > 0L)) {
                        showEmergencyExitDialog = true
                    } else {
                        onStopSession(false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("focus_stop_session_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (active.strictMode) "Emergency exit / Stop session" else "Finish & save session")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEditDurationDialog) {
        var minsText by remember { mutableStateOf((active.plannedDurationMinutes ?: 25).toString()) }
        var openEnded by remember { mutableStateOf(active.mode == FocusMode.OPEN_ENDED) }
        AlertDialog(
            onDismissRequest = { showEditDurationDialog = false },
            title = { Text("Edit session duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { openEnded = false },
                            colors = if (!openEnded) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                        ) { Text("Timed") }
                        OutlinedButton(onClick = { openEnded = true }) { Text("Open-ended") }
                    }
                    if (!openEnded) {
                        OutlinedTextField(
                            value = minsText,
                            onValueChange = { minsText = it.filter(Char::isDigit) },
                            label = { Text("Target minutes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateDuration(if (openEnded) null else minsText.toIntOrNull()?.coerceIn(5, 480) ?: 25)
                    showEditDurationDialog = false
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDurationDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var mins by remember { mutableStateOf("25") }
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Add task to session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mins,
                        onValueChange = { mins = it.filter(Char::isDigit) },
                        label = { Text("Estimated minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTaskToSession(title, mins.toIntOrNull()?.coerceIn(5, 240) ?: 25)
                        showAddTaskDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEmergencyExitDialog) {
        var confirmPhrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEmergencyExitDialog = false },
            title = { Text("Strict shield active") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Type STOP to confirm ending this strict focus session early, or choose Finish to log completed work so far.")
                    OutlinedTextField(
                        value = confirmPhrase,
                        onValueChange = { confirmPhrase = it },
                        label = { Text("Type STOP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("emergency_exit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyExitDialog = false
                        onStopSession(false)
                    },
                    enabled = confirmPhrase.trim().equals("STOP", ignoreCase = true),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("emergency_exit_confirm")
                ) { Text("End session") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyExitDialog = false }) { Text("Keep focusing") }
            }
        )
    }
}
