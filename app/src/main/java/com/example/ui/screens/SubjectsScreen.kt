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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.data.local.ChapterEntity
import com.example.data.local.SubjectEntity
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onAddSubject: (String) -> Unit,
    onRenameSubject: (SubjectEntity, String) -> Unit,
    onDeleteSubject: (SubjectEntity) -> Unit,
    onAddChapter: (Long, String) -> Unit,
    onRenameChapter: (ChapterEntity, String) -> Unit,
    onDeleteChapter: (Long) -> Unit
) {
    BackHandler(onBack = onBack)
    var expandedSubjectId by remember { mutableStateOf<Long?>(state.subjects.firstOrNull()?.id) }
    var showAddSubject by remember { mutableStateOf(false) }
    var renamingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var addChapterForSubjectId by remember { mutableStateOf<Long?>(null) }
    var renamingChapter by remember { mutableStateOf<ChapterEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subjects & chapters") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("subjects_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddSubject = true },
                        modifier = Modifier.testTag("subjects_add_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add custom subject")
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
            items(state.subjects, key = { it.id }) { subject ->
                val chapters = state.chapters.filter { it.subjectId == subject.id }
                val expanded = expandedSubjectId == subject.id

                FocusFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { expandedSubjectId = if (expanded) null else subject.id }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = subject.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${chapters.size} chapters" + if (subject.builtIn) " · Built-in" else " · Custom",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!subject.builtIn) {
                            IconButton(onClick = { renamingSubject = subject }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename subject")
                            }
                            IconButton(onClick = { onDeleteSubject(subject) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete subject")
                            }
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }

                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        chapters.forEach { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ch.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { renamingChapter = ch }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename chapter")
                                }
                                IconButton(onClick = { onDeleteChapter(ch.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete chapter")
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { addChapterForSubjectId = subject.id },
                            modifier = Modifier.testTag("subject_add_chapter_${subject.id}")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add chapter")
                        }
                    }
                }
            }
        }
    }

    if (showAddSubject) {
        SingleFieldDialog(
            title = "New custom subject",
            label = "Subject name",
            initial = "",
            onDismiss = { showAddSubject = false },
            onConfirm = { name ->
                onAddSubject(name)
                showAddSubject = false
            }
        )
    }

    renamingSubject?.let { subj ->
        SingleFieldDialog(
            title = "Rename subject",
            label = "Subject name",
            initial = subj.name,
            onDismiss = { renamingSubject = null },
            onConfirm = { name ->
                onRenameSubject(subj, name)
                renamingSubject = null
            }
        )
    }

    addChapterForSubjectId?.let { subjId ->
        SingleFieldDialog(
            title = "Add chapter",
            label = "Chapter title",
            initial = "",
            onDismiss = { addChapterForSubjectId = null },
            onConfirm = { name ->
                onAddChapter(subjId, name)
                addChapterForSubjectId = null
            }
        )
    }

    renamingChapter?.let { ch ->
        SingleFieldDialog(
            title = "Rename chapter",
            label = "Chapter title",
            initial = ch.name,
            onDismiss = { renamingChapter = null },
            onConfirm = { name ->
                onRenameChapter(ch, name)
                renamingChapter = null
            }
        )
    }
}

@Composable
private fun SingleFieldDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
