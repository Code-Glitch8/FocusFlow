package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.SessionClock
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoMetricLarge
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    sessionId: Long,
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onGenerateSummary: (Long) -> Unit,
    onEditSession: (Long, String, Int?) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    BackHandler(onBack = onBack)

    val session = state.sessions.find { it.id == sessionId }
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session summary") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("summary_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("summary_edit_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit session")
                        }
                        IconButton(
                            onClick = { onDeleteSession(sessionId) },
                            modifier = Modifier.testTag("summary_delete_button")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete session")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (session == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Session not found.", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        val snap = SessionClock.computeSnapshot(session, state.nowMs)
        val focusMins = (snap.focusElapsedMs / 60_000L).toInt()
        val breakMins = (snap.breakElapsedMs / 60_000L).toInt()
        val tasks = state.sessionTasks.filter { it.sessionId == sessionId }
        val doneCount = tasks.count { it.completed }
        val summary = state.summaries.find { it.sessionId == sessionId }
        val isGenerating = state.summaryGeneratingSessionId == sessionId
        val subjectNames = state.sessionChapters
            .filter { it.sessionId == sessionId }
            .mapNotNull { sc -> state.subjects.find { it.id == sc.subjectId }?.name }
            .distinct()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(session.label, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(session.startedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(SessionClock.formatMinutesCompact(focusMins), style = MonoMetricLarge)
                            Text("focused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(SessionClock.formatMinutesCompact(breakMins), style = MonoMetricLarge)
                            Text("breaks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$doneCount/${tasks.size}", style = MonoMetricLarge)
                            Text("tasks done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (subjectNames.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Subjects: ${subjectNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // AI Study Summary Card
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("AI session reflection")
                        OutlinedButton(
                            onClick = { onGenerateSummary(sessionId) },
                            enabled = !isGenerating,
                            modifier = Modifier.testTag("summary_regenerate_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (isGenerating) "Summarizing..." else if (summary == null) "Generate summary" else "Regenerate")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    when {
                        summary != null -> {
                            Text(summary.summaryText, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Generated via ${summary.providerUsed}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        isGenerating -> {
                            Text(
                                text = "Generating grounded summary from your session metrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = "Tap Generate summary to create a grounded reflection via Built-in Gemini AI.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    state.summaryError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth().padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Tasks Snapshot
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Session tasks")
                    if (tasks.isEmpty()) {
                        Text(
                            text = "No tasks were attached to this session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tasks.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (t.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (t.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(t.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("~${t.estimatedMinutes}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && session != null) {
        var newLabel by remember { mutableStateOf(session.label) }
        var newMins by remember { mutableStateOf((session.plannedDurationMinutes ?: 25).toString()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newMins,
                        onValueChange = { newMins = it.filter(Char::isDigit) },
                        label = { Text("Planned minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onEditSession(sessionId, newLabel, newMins.toIntOrNull())
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}
