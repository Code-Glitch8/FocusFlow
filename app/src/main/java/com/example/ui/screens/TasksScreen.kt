package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.local.ChapterEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.local.SubjectEntity
import com.example.data.local.SubtaskEntity
import com.example.domain.SessionClock
import com.example.model.TaskStatus
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard

private enum class TaskFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    DONE("Done")
}

@Composable
fun TasksScreen(
    state: FocusFlowUiState,
    onToggleTask: (StudyTaskEntity) -> Unit,
    onToggleSubtask: (SubtaskEntity) -> Unit,
    onSaveTask: (existingId: Long?, title: String, subjectId: Long?, chapterId: Long?, minutes: Int, subtasks: List<String>) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onManageSubjects: () -> Unit
) {
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    var editingTask by remember { mutableStateOf<StudyTaskEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredTasks = remember(state.studyTasks, filter) {
        when (filter) {
            TaskFilter.ALL -> state.studyTasks
            TaskFilter.PENDING -> state.studyTasks.filter { it.status == TaskStatus.PENDING }
            TaskFilter.DONE -> state.studyTasks.filter { it.status == TaskStatus.DONE }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tasks", style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onManageSubjects,
                        modifier = Modifier.testTag("tasks_subjects_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Subjects")
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("tasks_add_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New task")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = {
                            val count = when (f) {
                                TaskFilter.ALL -> state.studyTasks.size
                                TaskFilter.PENDING -> state.studyTasks.count { it.status == TaskStatus.PENDING }
                                TaskFilter.DONE -> state.studyTasks.count { it.status == TaskStatus.DONE }
                            }
                            Text("${f.label} ($count)")
                        },
                        modifier = Modifier.testTag("tasks_filter_${f.name.lowercase()}")
                    )
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (state.studyTasks.isEmpty()) {
                            "No study tasks yet. Add tasks manually or start a session with AI task planning."
                        } else {
                            "No tasks match the selected filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
                val isDone = task.status == TaskStatus.DONE
                val subj = state.subjects.find { it.id == task.subjectId }
                val chapter = state.chapters.find { it.id == task.chapterId }
                val taskSubtasks = state.subtasks.filter { it.taskId == task.id }

                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconButton(
                            onClick = { onToggleTask(task) },
                            modifier = Modifier.testTag("task_toggle_${task.id}")
                        ) {
                            Icon(
                                imageVector = if (isDone) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                contentDescription = if (isDone) "Mark pending" else "Mark completed",
                                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            val meta = buildList {
                                add("~${SessionClock.formatMinutesCompact(task.estimatedMinutes)}")
                                if (subj != null) add(subj.name)
                                if (chapter != null) add(chapter.name)
                            }.joinToString(" · ")
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (taskSubtasks.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                taskSubtasks.forEach { sub ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleSubtask(sub) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (sub.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                            contentDescription = if (sub.done) "Subtask done" else "Subtask pending",
                                            tint = if (sub.done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = sub.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (sub.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { editingTask = task },
                            modifier = Modifier.testTag("task_edit_${task.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit task")
                        }
                        IconButton(
                            onClick = { onDeleteTask(task.id) },
                            modifier = Modifier.testTag("task_delete_${task.id}")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCreateDialog) {
        TaskEditorDialog(
            initialTask = null,
            initialSubtasks = emptyList(),
            subjects = state.subjects,
            chapters = state.chapters,
            onDismiss = { showCreateDialog = false },
            onSave = { title, subjId, chId, mins, subs ->
                onSaveTask(null, title, subjId, chId, mins, subs)
                showCreateDialog = false
            }
        )
    }

    editingTask?.let { target ->
        val subs = state.subtasks.filter { it.taskId == target.id }.map { it.title }
        TaskEditorDialog(
            initialTask = target,
            initialSubtasks = subs,
            subjects = state.subjects,
            chapters = state.chapters,
            onDismiss = { editingTask = null },
            onSave = { title, subjId, chId, mins, newSubs ->
                onSaveTask(target.id, title, subjId, chId, mins, newSubs)
                editingTask = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorDialog(
    initialTask: StudyTaskEntity?,
    initialSubtasks: List<String>,
    subjects: List<SubjectEntity>,
    chapters: List<ChapterEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, subjectId: Long?, chapterId: Long?, minutes: Int, subtasks: List<String>) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title.orEmpty()) }
    var minutesText by remember { mutableStateOf((initialTask?.estimatedMinutes ?: 25).toString()) }
    var subjectId by remember { mutableStateOf(initialTask?.subjectId) }
    var chapterId by remember { mutableStateOf(initialTask?.chapterId) }
    var subtasksText by remember { mutableStateOf(initialSubtasks.joinToString("\n")) }

    val subjectChapters = remember(subjectId, chapters) {
        if (subjectId == null) emptyList() else chapters.filter { it.subjectId == subjectId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTask == null) "New study task" else "Edit task") },
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
                    label = { Text("Task title") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().testTag("task_dialog_title_input")
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit) },
                    label = { Text("Estimated minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Subject", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = subjectId == null,
                        onClick = { subjectId = null; chapterId = null },
                        label = { Text("None") }
                    )
                    subjects.forEach { s ->
                        FilterChip(
                            selected = subjectId == s.id,
                            onClick = { subjectId = s.id; if (chapterId != null && chapters.none { it.id == chapterId && it.subjectId == s.id }) chapterId = null },
                            label = { Text(s.name) }
                        )
                    }
                }
                if (subjectChapters.isNotEmpty()) {
                    Text("Chapter", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = chapterId == null,
                            onClick = { chapterId = null },
                            label = { Text("Any") }
                        )
                        subjectChapters.forEach { ch ->
                            FilterChip(
                                selected = chapterId == ch.id,
                                onClick = { chapterId = ch.id },
                                label = { Text(ch.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subtasksText,
                    onValueChange = { subtasksText = it },
                    label = { Text("Subtasks (one per line, optional)") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = minutesText.toIntOrNull()?.coerceIn(5, 360) ?: 25
                    val subs = subtasksText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(title, subjectId, chapterId, mins, subs)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("task_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
