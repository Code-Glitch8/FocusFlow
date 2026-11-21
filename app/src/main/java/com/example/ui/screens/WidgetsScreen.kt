package com.example.ui.screens

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.DashboardWidgetEntity
import com.example.data.local.SubjectEntity
import com.example.model.FocusMode
import com.example.model.WidgetAccent
import com.example.model.WidgetAction
import com.example.model.WidgetIcon
import com.example.model.WidgetPresetTask
import com.example.model.WidgetSize
import com.example.model.WidgetType
import com.example.nativebridge.FocusFlowWidgetProvider
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.widgetIconToVector
import com.example.ui.theme.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onSaveWidgetWithTasks: (DashboardWidgetEntity, List<WidgetPresetTask>) -> Unit,
    onDeleteWidget: (Long) -> Unit,
    onMoveWidget: (Long, Int) -> Unit,
    onResetDefaults: () -> Unit,
    onShowToast: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    var editingWidget by remember { mutableStateOf<DashboardWidgetEntity?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widgets & shortcuts") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("widgets_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.testTag("widgets_reset_button")
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset default widgets")
                    }
                    IconButton(
                        onClick = {
                            editingWidget = DashboardWidgetEntity(
                                title = "Focus 30",
                                type = WidgetType.TIMER,
                                action = WidgetAction.START_SESSION,
                                icon = WidgetIcon.TIMER,
                                accent = WidgetAccent.INDIGO,
                                size = WidgetSize.SMALL,
                                mode = FocusMode.TIMED,
                                durationMinutes = 30,
                                strictMode = state.settings.strictDefault
                            )
                        },
                        modifier = Modifier.testTag("widgets_add_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add widget")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FocusFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "Configure duration and preset tasks on any widget, then tap the pin icon to place it on your Android home screen. Tapping Start on the home screen launches that exact session & tasks immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(state.widgets, key = { _, w -> w.id }) { index, widget ->
                val accentColor = widget.accent.toColor()
                val presetTasks = state.settings.widgetPresetTasks[widget.id].orEmpty()
                val canStart = widget.type != WidgetType.PROGRESS && widget.type != WidgetType.QUOTE
                val subtitle = buildString {
                    append("${widget.type.label} · ${widget.size.label}")
                    if (canStart) {
                        val dur = if (widget.mode == FocusMode.OPEN_ENDED) "Open" else "${widget.durationMinutes ?: 25}m"
                        append(" · $dur")
                        if (presetTasks.isNotEmpty()) {
                            append(" · ${presetTasks.size} task(s)")
                        }
                    }
                }

                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = widgetIconToVector(widget.icon),
                                contentDescription = widget.title,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = widget.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canStart) {
                            IconButton(
                                onClick = {
                                    pinWidgetToHomeScreen(context, widget.id, onShowToast)
                                },
                                modifier = Modifier.testTag("widget_pin_${widget.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pin to home screen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { onMoveWidget(widget.id, -1) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMoveWidget(widget.id, 1) },
                            enabled = index < state.widgets.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(
                            onClick = { editingWidget = widget },
                            modifier = Modifier.testTag("widget_edit_${widget.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit widget")
                        }
                        IconButton(
                            onClick = { onDeleteWidget(widget.id) },
                            modifier = Modifier.testTag("widget_delete_${widget.id}")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete widget")
                        }
                    }
                }
            }
        }
    }

    editingWidget?.let { current ->
        val existingTasks = if (current.id > 0L) {
            state.settings.widgetPresetTasks[current.id].orEmpty()
        } else {
            emptyList()
        }
        WidgetEditorDialog(
            initial = current,
            initialTasks = existingTasks,
            subjects = state.subjects,
            onDismiss = { editingWidget = null },
            onSave = { updated, tasks ->
                onSaveWidgetWithTasks(updated, tasks)
                editingWidget = null
            }
        )
    }
}

private fun pinWidgetToHomeScreen(
    context: Context,
    widgetId: Long,
    onFeedback: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onFeedback("Long-press your device home screen and add the FocusFlow widget.")
        return
    }
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) {
        onFeedback("Your launcher doesn't support quick pinning. Long-press your home screen to add FocusFlow.")
        return
    }
    val provider = ComponentName(context, FocusFlowWidgetProvider::class.java)
    val callbackIntent = Intent(context, FocusFlowWidgetProvider::class.java).apply {
        action = FocusFlowWidgetProvider.ACTION_WIDGET_PINNED
        putExtra(FocusFlowWidgetProvider.EXTRA_WIDGET_ID, widgetId)
    }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    val successCallback = PendingIntent.getBroadcast(
        context,
        widgetId.toInt(),
        callbackIntent,
        flags
    )
    val requested = runCatching {
        mgr.requestPinAppWidget(provider, null, successCallback)
    }.getOrDefault(false)
    if (requested) {
        onFeedback("Pin prompt sent to your launcher.")
    } else {
        onFeedback("Long-press your home screen to place a FocusFlow widget.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetEditorDialog(
    initial: DashboardWidgetEntity,
    initialTasks: List<WidgetPresetTask>,
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (DashboardWidgetEntity, List<WidgetPresetTask>) -> Unit
) {
    var title by remember { mutableStateOf(initial.title) }
    var type by remember { mutableStateOf(initial.type) }
    var icon by remember { mutableStateOf(initial.icon) }
    var accent by remember { mutableStateOf(initial.accent) }
    var size by remember { mutableStateOf(initial.size) }
    var mode by remember { mutableStateOf(initial.mode) }
    var durationText by remember { mutableStateOf((initial.durationMinutes ?: 30).toString()) }
    var strictMode by remember { mutableStateOf(initial.strictMode) }
    var subjectId by remember { mutableStateOf(initial.subjectId) }

    val presetTasks = remember {
        mutableStateListOf<WidgetPresetTask>().apply { addAll(initialTasks) }
    }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskMins by remember { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id > 0L) "Edit widget" else "New widget") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (t == WidgetType.OPEN_ENDED) mode = FocusMode.OPEN_ENDED
                                else if (t == WidgetType.TIMER) mode = FocusMode.TIMED
                                if (t == WidgetType.PROGRESS) size = WidgetSize.LARGE
                            },
                            label = { Text(t.label) }
                        )
                    }
                }

                if (type == WidgetType.SUBJECT) {
                    Text("Subject", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subjects.forEach { subj ->
                            FilterChip(
                                selected = subjectId == subj.id,
                                onClick = { subjectId = subj.id },
                                label = { Text(subj.name) }
                            )
                        }
                    }
                }

                if (type != WidgetType.PROGRESS && type != WidgetType.QUOTE) {
                    Text("Session mode", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusMode.entries.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.label) }
                            )
                        }
                    }
                    if (mode == FocusMode.TIMED) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it.filter(Char::isDigit) },
                            label = { Text("Duration (minutes)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Strict shield mode")
                        Switch(checked = strictMode, onCheckedChange = { strictMode = it })
                    }

                    // Preset tasks attached to this widget
                    Text("Preset session tasks", style = MaterialTheme.typography.labelMedium)
                    if (presetTasks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            presetTasks.forEachIndexed { idx, task ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${task.title} (~${task.estimatedMinutes}m)",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { presetTasks.removeAt(idx) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove preset task",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            label = { Text("Add task") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("widget_preset_task_input")
                        )
                        OutlinedTextField(
                            value = newTaskMins,
                            onValueChange = { newTaskMins = it.filter(Char::isDigit) },
                            label = { Text("Min") },
                            singleLine = true,
                            modifier = Modifier.width(68.dp)
                        )
                        IconButton(
                            onClick = {
                                val clean = newTaskTitle.trim()
                                if (clean.isNotEmpty()) {
                                    presetTasks.add(
                                        WidgetPresetTask(
                                            title = clean,
                                            estimatedMinutes = newTaskMins.toIntOrNull()?.coerceIn(5, 240) ?: 15
                                        )
                                    )
                                    newTaskTitle = ""
                                }
                            },
                            modifier = Modifier.testTag("widget_preset_task_add")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add preset task")
                        }
                    }
                }

                Text("Size", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetSize.entries.forEach { s ->
                        FilterChip(
                            selected = size == s,
                            onClick = { size = s },
                            label = { Text(s.label) }
                        )
                    }
                }

                Text("Accent color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetAccent.entries.forEach { a ->
                        val c = a.toColor()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (accent == a) 3.dp else 0.dp,
                                    color = if (accent == a) MaterialTheme.colorScheme.onSurface else c,
                                    shape = CircleShape
                                )
                                .clickable { accent = a }
                        )
                    }
                }

                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetIcon.entries.forEach { ic ->
                        val selected = icon == ic
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { icon = ic },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = widgetIconToVector(ic),
                                contentDescription = ic.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val action = when (type) {
                        WidgetType.PROGRESS -> WidgetAction.OPEN_STATISTICS
                        else -> WidgetAction.START_SESSION
                    }
                    val mins = if (mode == FocusMode.TIMED) durationText.toIntOrNull()?.coerceIn(5, 360) ?: 30 else null
                    val finalTasks = presetTasks.toMutableList()
                    val pendingTitle = newTaskTitle.trim()
                    if (pendingTitle.isNotEmpty()) {
                        finalTasks.add(
                            WidgetPresetTask(
                                title = pendingTitle,
                                estimatedMinutes = newTaskMins.toIntOrNull()?.coerceIn(5, 240) ?: 15
                            )
                        )
                    }
                    onSave(
                        initial.copy(
                            title = title.trim().ifEmpty { "Focus" },
                            type = type,
                            action = action,
                            icon = icon,
                            accent = accent,
                            size = size,
                            mode = mode,
                            durationMinutes = mins,
                            strictMode = strictMode,
                            subjectId = if (type == WidgetType.SUBJECT) subjectId else null
                        ),
                        finalTasks
                    )
                },
                modifier = Modifier.testTag("widget_editor_save")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
