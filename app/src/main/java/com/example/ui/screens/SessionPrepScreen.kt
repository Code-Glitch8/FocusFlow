package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DraftStudyTask
import com.example.model.FocusMode
import com.example.model.SubjectPlan
import com.example.model.TaskOrigin
import com.example.model.TaskStatus
import com.example.ui.FocusFlowUiState
import com.example.ui.FocusRoute
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.SectionHeader
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionPrepScreen(
    route: FocusRoute.SessionPrep,
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onParseLocal: (String, List<SubjectPlan>, TaskOrigin) -> List<DraftStudyTask>,
    onGenerateAiTasks: (String, List<SubjectPlan>, TaskOrigin, (List<DraftStudyTask>) -> Unit) -> Unit,
    onClearAiMessages: () -> Unit,
    onStartVoice: ((String) -> Unit) -> Unit,
    onStopVoice: () -> Unit,
    onSavePreset: (String, FocusMode, Int?, Boolean, List<SubjectPlan>, List<DraftStudyTask>) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onAddSubject: (String) -> Unit,
    onAddChapter: (Long, String) -> Unit,
    onStartSession: (
        label: String,
        mode: FocusMode,
        durationMinutes: Int?,
        strictMode: Boolean,
        rawStudyPlan: String,
        subjectPlans: List<SubjectPlan>,
        draftTasks: List<DraftStudyTask>,
        selectedStudyTaskIds: List<Long>,
        widgetId: Long?,
        launchMinimized: Boolean
    ) -> Unit
) {
    BackHandler(onBack = onBack)

    var label by remember(route) { mutableStateOf(route.title) }
    var mode by remember(route) { mutableStateOf(route.mode) }
    var durationText by remember(route) { mutableStateOf(route.durationMinutes.toString()) }
    var strictMode by remember(route) { mutableStateOf(route.strictMode) }
    var launchMinimized by remember { mutableStateOf(false) }
    var studyPlanText by remember { mutableStateOf("") }
    var chapterQuery by remember { mutableStateOf("") }

    val selectedSubjectIds = remember(route) {
        mutableStateListOf<Long>().apply {
            route.initialSubjectId?.let { add(it) }
        }
    }
    val selectedChapterIds = remember { mutableStateListOf<Long>() }
    val draftTasks = remember(route.widgetId) {
        mutableStateListOf<DraftStudyTask>().apply {
            val preset = route.widgetId?.let { state.settings.widgetPresetTasks[it] }.orEmpty()
            preset.forEachIndexed { idx, wt ->
                add(
                    DraftStudyTask(
                        id = "widget_prep_${route.widgetId}_$idx",
                        title = wt.title,
                        estimatedMinutes = wt.estimatedMinutes,
                        subjectId = route.initialSubjectId,
                        origin = TaskOrigin.MANUAL
                    )
                )
            }
        }
    }
    val selectedPendingIds = remember { mutableStateListOf<Long>() }

    var showAddDraftDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddChapterDialog by remember { mutableStateOf(false) }

    fun currentSubjectPlans(): List<SubjectPlan> {
        return selectedSubjectIds.mapNotNull { sId ->
            val subj = state.subjects.find { it.id == sId } ?: return@mapNotNull null
            val chIds = selectedChapterIds.filter { chId ->
                state.chapters.any { it.id == chId && it.subjectId == sId }
            }
            val chSummary = chIds.mapNotNull { chId -> state.chapters.find { it.id == chId }?.name }
                .joinToString(", ")
            SubjectPlan(sId, subj.name, chIds, chSummary)
        }
    }

    fun triggerVoiceCapture() {
        onStartVoice { transcript ->
            val combined = if (studyPlanText.isBlank()) transcript else "$studyPlanText\n$transcript"
            studyPlanText = combined
            val plans = currentSubjectPlans()
            val parsed = onParseLocal(transcript, plans, TaskOrigin.AI_VOICE)
            parsed.forEach { dt ->
                if (draftTasks.none { it.dedupeKey == dt.dedupeKey && dt.dedupeKey.isNotEmpty() }) {
                    draftTasks.add(dt)
                }
            }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) triggerVoiceCapture()
    }

    LaunchedEffect(route.openVoiceOnLaunch) {
        if (route.openVoiceOnLaunch) {
            if (state.permissionHealth.microphoneGranted) {
                triggerVoiceCapture()
            } else {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val pendingTasks = remember(state.studyTasks) {
        state.studyTasks.filter { it.status == TaskStatus.PENDING }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prepare session") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("prep_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSavePresetDialog = true },
                        modifier = Modifier.testTag("prep_save_preset_button")
                    ) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save as preset")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.presets.isNotEmpty()) {
                item {
                    SectionHeader("Saved presets")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.presets.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    label = preset.name
                                    mode = preset.mode
                                    durationText = (preset.plannedDurationMinutes ?: 25).toString()
                                    strictMode = preset.strictMode
                                    selectedSubjectIds.clear()
                                    selectedChapterIds.clear()
                                    state.presetChapters.filter { it.presetId == preset.id }.forEach { pc ->
                                        if (pc.subjectId !in selectedSubjectIds) selectedSubjectIds.add(pc.subjectId)
                                        if (pc.chapterId != null && pc.chapterId !in selectedChapterIds) selectedChapterIds.add(pc.chapterId)
                                    }
                                    draftTasks.clear()
                                    state.presetTasks.filter { it.presetId == preset.id }.forEach { pt ->
                                        draftTasks.add(
                                            DraftStudyTask(
                                                id = UUID.randomUUID().toString(),
                                                title = pt.title,
                                                estimatedMinutes = pt.estimatedMinutes,
                                                subjectId = pt.subjectId,
                                                chapterId = pt.chapterId,
                                                origin = TaskOrigin.MANUAL
                                            )
                                        )
                                    }
                                },
                                label = { Text(preset.name) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onDeletePreset(preset.id) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Delete preset", modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Session details")
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Session label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("prep_label_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusMode.entries.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.label) },
                                modifier = Modifier.testTag("prep_mode_${m.name.lowercase()}")
                            )
                        }
                    }
                    if (mode == FocusMode.TIMED) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 25, 45, 60, 80, 90).forEach { presetMin ->
                                FilterChip(
                                    selected = durationText.toIntOrNull() == presetMin,
                                    onClick = { durationText = presetMin.toString() },
                                    label = { Text("${presetMin}m") }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it.filter(Char::isDigit) },
                            label = { Text("Custom minutes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("prep_duration_input")
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Focus Shield", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Requires confirmation for early exit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = strictMode,
                            onCheckedChange = { strictMode = it },
                            modifier = Modifier.testTag("prep_strict_switch")
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Launch minimized in circle mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Starts with floating timer bubble on Home",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = launchMinimized,
                            onCheckedChange = { launchMinimized = it },
                            modifier = Modifier.testTag("prep_launch_minimized_switch")
                        )
                    }
                }
            }

            // Subjects & Chapters
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("Subjects & chapters")
                        TextButton(onClick = { showAddSubjectDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add subject")
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.subjects.forEach { subj ->
                            val selected = subj.id in selectedSubjectIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) selectedSubjectIds.remove(subj.id)
                                    else selectedSubjectIds.add(subj.id)
                                    val activeNames = selectedSubjectIds.mapNotNull { id -> state.subjects.find { it.id == id }?.name }
                                    if (activeNames.isNotEmpty() && (label == "Custom session" || state.subjects.any { it.name == label } || label.contains(" + "))) {
                                        label = activeNames.joinToString(" + ")
                                    }
                                },
                                label = { Text(subj.name) },
                                modifier = Modifier.testTag("prep_subject_${subj.id}")
                            )
                        }
                    }

                    val availableChapters = state.chapters.filter {
                        it.subjectId in selectedSubjectIds &&
                            (chapterQuery.isBlank() || it.name.contains(chapterQuery, ignoreCase = true))
                    }
                    if (selectedSubjectIds.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chapters",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { showAddChapterDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add chapter")
                            }
                        }
                        if (state.chapters.count { it.subjectId in selectedSubjectIds } > 5) {
                            OutlinedTextField(
                                value = chapterQuery,
                                onValueChange = { chapterQuery = it },
                                label = { Text("Search chapters") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableChapters.forEach { ch ->
                                val selected = ch.id in selectedChapterIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) selectedChapterIds.remove(ch.id)
                                        else selectedChapterIds.add(ch.id)
                                    },
                                    label = { Text(ch.name) }
                                )
                            }
                        }
                    }
                }
            }

            // Study Plan + Voice + AI Task Extraction
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("Study plan & AI task breakdown")
                        if (state.voiceListening) {
                            OutlinedButton(
                                onClick = onStopVoice,
                                modifier = Modifier.testTag("prep_stop_voice_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop listening", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop mic")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (state.permissionHealth.microphoneGranted) triggerVoiceCapture()
                                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.testTag("prep_voice_button")
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "Voice dictation", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Dictate")
                            }
                        }
                    }

                    if (state.voiceListening) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = state.voicePartial.ifBlank { "Listening... describe what you plan to study." },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    state.voiceError?.let { vErr ->
                        Text(
                            text = vErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = studyPlanText,
                        onValueChange = {
                            studyPlanText = it
                            onClearAiMessages()
                        },
                        label = { Text("What are you studying? (e.g. Solve 20 quadratic equations for math and revise thermodynamics)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("prep_plan_input")
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val parsed = onParseLocal(studyPlanText, currentSubjectPlans(), TaskOrigin.AI_TEXT)
                                parsed.forEach { dt ->
                                    if (draftTasks.none { it.dedupeKey == dt.dedupeKey && dt.dedupeKey.isNotEmpty() }) {
                                        draftTasks.add(dt)
                                    }
                                }
                            },
                            enabled = studyPlanText.isNotBlank(),
                            modifier = Modifier.weight(1f).testTag("prep_parse_local_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Extract locally")
                        }

                        Button(
                            onClick = {
                                onGenerateAiTasks(
                                    studyPlanText,
                                    currentSubjectPlans(),
                                    TaskOrigin.AI_TEXT
                                ) { suggested ->
                                    suggested.forEach { dt ->
                                        if (draftTasks.none { it.dedupeKey == dt.dedupeKey && dt.dedupeKey.isNotEmpty() }) {
                                            draftTasks.add(dt)
                                        }
                                    }
                                }
                            },
                            enabled = studyPlanText.isNotBlank() && !state.aiBusy,
                            modifier = Modifier.weight(1f).testTag("prep_ai_suggest_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.aiBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.aiBusy) "Thinking..." else "Suggest with AI")
                        }
                    }

                    state.aiError?.let { err ->
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

                    state.aiNotice?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Session Tasks Queue
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("Session tasks (${draftTasks.size + selectedPendingIds.size})")
                        TextButton(
                            onClick = { showAddDraftDialog = true },
                            modifier = Modifier.testTag("prep_add_draft_task")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add task")
                        }
                    }

                    if (draftTasks.isEmpty() && pendingTasks.isEmpty()) {
                        Text(
                            text = "Optional: add or extract tasks to earn study breaks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    draftTasks.forEachIndexed { index, dt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dt.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "~${dt.estimatedMinutes} min" + if (dt.subtasks.isNotEmpty()) " · ${dt.subtasks.size} steps" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { draftTasks.removeAt(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove task", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    if (pendingTasks.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Include from your To-Do list",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pendingTasks.forEach { st ->
                                val selected = st.id in selectedPendingIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) selectedPendingIds.remove(st.id)
                                        else selectedPendingIds.add(st.id)
                                    },
                                    label = { Text("${st.title} (${st.estimatedMinutes}m)") }
                                )
                            }
                        }
                    }
                }
            }

            // Start CTA
            item {
                Button(
                    onClick = {
                        val plans = currentSubjectPlans()
                        val mins = if (mode == FocusMode.TIMED) durationText.toIntOrNull()?.coerceIn(1, 480) ?: 25 else null
                        onStartSession(
                            label.trim().ifEmpty { "Focus session" },
                            mode,
                            mins,
                            strictMode,
                            studyPlanText,
                            plans,
                            draftTasks.toList(),
                            selectedPendingIds.toList(),
                            route.widgetId,
                            launchMinimized
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("prep_start_session_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (mode == FocusMode.TIMED) "Start ${durationText.toIntOrNull() ?: 25}m Focus Session"
                        else "Start Open-Ended Focus Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddDraftDialog) {
        var title by remember { mutableStateOf("") }
        var mins by remember { mutableStateOf("25") }
        AlertDialog(
            onDismissRequest = { showAddDraftDialog = false },
            title = { Text("Add session task") },
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
                        draftTasks.add(
                            DraftStudyTask(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                estimatedMinutes = mins.toIntOrNull()?.coerceIn(5, 240) ?: 25,
                                subjectId = selectedSubjectIds.firstOrNull(),
                                origin = TaskOrigin.MANUAL
                            )
                        )
                        showAddDraftDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDraftDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf(label) }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save as preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = if (mode == FocusMode.TIMED) durationText.toIntOrNull()?.coerceIn(1, 480) ?: 25 else null
                        onSavePreset(presetName, mode, mins, strictMode, currentSubjectPlans(), draftTasks.toList())
                        showSavePresetDialog = false
                    },
                    enabled = presetName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddSubjectDialog) {
        var newSubj by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Add custom subject") },
            text = {
                OutlinedTextField(
                    value = newSubj,
                    onValueChange = { newSubj = it },
                    label = { Text("Subject name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddSubject(newSubj.trim())
                        showAddSubjectDialog = false
                    },
                    enabled = newSubj.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddChapterDialog && selectedSubjectIds.isNotEmpty()) {
        var newCh by remember { mutableStateOf("") }
        val targetSubjectId = selectedSubjectIds.last()
        val targetSubjectName = state.subjects.find { it.id == targetSubjectId }?.name ?: "subject"
        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = { Text("Add chapter to $targetSubjectName") },
            text = {
                OutlinedTextField(
                    value = newCh,
                    onValueChange = { newCh = it },
                    label = { Text("Chapter title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddChapter(targetSubjectId, newCh.trim())
                        showAddChapterDialog = false
                    },
                    enabled = newCh.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) { Text("Cancel") }
            }
        )
    }
}
