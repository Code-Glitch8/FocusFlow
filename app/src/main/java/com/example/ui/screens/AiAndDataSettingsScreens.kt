package com.example.ui.screens

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AiProviderProfile
import com.example.model.AiProviderType
import com.example.model.AppSettings
import com.example.nativebridge.AppLauncherHelper
import com.example.nativebridge.LanPeerInfo
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoMetricMedium
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onOpenProviders: () -> Unit,
    onShowToast: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var snapPkg by remember(state.settings.snapGptPackage) {
        mutableStateOf(state.settings.snapGptPackage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI, Voice & SnapGPT") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ai_settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Voice & reflections")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prefer offline voice recognition", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Passes EXTRA_PREFER_OFFLINE to Android SpeechRecognizer when supported.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.voiceOfflineOnly,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(voiceOfflineOnly = v) } }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-generate session summary", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Creates a grounded reflection when a focus session finishes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.autoSummaryEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(autoSummaryEnabled = v) } }
                        )
                    }
                }
            }

            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("SnapGPT companion app")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-launch SnapGPT when session starts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Opens your companion study assistant automatically when starting focus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.autoLaunchSnapGpt,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(autoLaunchSnapGpt = v) } },
                            modifier = Modifier.testTag("ai_settings_snapgpt_switch")
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = snapPkg,
                        onValueChange = {
                            snapPkg = it
                            onUpdateSettings { s -> s.copy(snapGptPackage = it.trim().ifEmpty { "com.snapgpt.assistant" }) }
                        },
                        label = { Text("SnapGPT package name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val ok = AppLauncherHelper.launchPackage(context, snapPkg)
                            if (!ok) {
                                onShowToast("SnapGPT ($snapPkg) is not installed on this device.")
                            }
                        },
                        modifier = Modifier.testTag("ai_settings_test_snapgpt")
                    ) {
                        Text("Test launch SnapGPT")
                    }
                }
            }

            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Active AI engine")
                    Text(
                        text = state.activeProvider?.name ?: "Built-in Gemini AI (Firebase AI Logic)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.activeProvider == null) "Ready with zero API keys via App Check." else "Using custom provider profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenProviders) {
                        Text("Manage AI providers")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiProvidersScreen(
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onSelectProvider: (String?) -> Unit,
    onUpsertProvider: (AiProviderProfile, Boolean) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestProvider: (AiProviderProfile?) -> Unit
) {
    BackHandler(onBack = onBack)
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Providers") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("providers_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("providers_add_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add custom AI provider")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Built-in Firebase AI Logic card
            item {
                val isBuiltInActive = state.settings.activeProviderId == null
                FocusFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isBuiltInActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Built-in Gemini AI (gemini-3.8-flash)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Powered by Firebase AI Logic. Works out-of-the-box with no API key required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isBuiltInActive) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isBuiltInActive) {
                            Button(onClick = { onSelectProvider(null) }) { Text("Use built-in") }
                        }
                        OutlinedButton(
                            onClick = { onTestProvider(null) },
                            modifier = Modifier.testTag("providers_test_builtin")
                        ) {
                            Text("Test connection")
                        }
                    }
                }
            }

            state.providerTestStatus?.let { status ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }
            }

            if (state.providers.isNotEmpty()) {
                item { SectionHeader("Custom API providers") }
                items(state.providers, key = { it.id }) { p ->
                    val isActive = state.settings.activeProviderId == p.id
                    FocusFlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${p.type.label} · ${p.model.ifBlank { "default model" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteProvider(p.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove provider")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSelectProvider(p.id) },
                                enabled = !isActive
                            ) {
                                Text(if (isActive) "Active" else "Activate")
                            }
                            OutlinedButton(onClick = { onTestProvider(p) }) {
                                Text("Test")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("My Custom AI") }
        var type by remember { mutableStateOf(AiProviderType.OPENAI) }
        var baseUrl by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add custom AI provider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile name") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiProviderType.entries.filter { it != AiProviderType.FIREBASE_AI }.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                        }
                    }
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID (optional)") }, singleLine = true)
                    if (type == AiProviderType.OPENAI_COMPATIBLE) {
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                    }
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpsertProvider(
                            AiProviderProfile(
                                id = UUID.randomUUID().toString(),
                                name = name.trim().ifEmpty { type.label },
                                type = type,
                                baseUrl = baseUrl.trim(),
                                model = model.trim(),
                                apiKey = apiKey.trim()
                            ),
                            true
                        )
                        showAddDialog = false
                    },
                    enabled = apiKey.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onExportDatabaseJson: suspend () -> String,
    onImportDatabaseJson: (String) -> Unit,
    onExportSettingsJson: suspend () -> String,
    onImportSettingsJson: (String) -> Unit,
    onRegenerateSyncCode: () -> Unit,
    onDiscoverPeers: () -> Unit,
    onSyncWithPeer: (LanPeerInfo) -> Unit,
    onClearStudyData: () -> Unit,
    onShowToast: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = onExportDatabaseJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess { onShowToast("Full backup exported.") }
                    .onFailure { onShowToast("Export failed: ${it.localizedMessage}") }
            }
        }
    }

    val importDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (raw != null) onImportDatabaseJson(raw)
        }
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = onExportSettingsJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess { onShowToast("Settings exported.") }
                    .onFailure { onShowToast("Export failed: ${it.localizedMessage}") }
            }
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (raw != null) onImportSettingsJson(raw)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Local Wi-Fi Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("data_settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("JSON Backup & Restore")
                    Text(
                        "Save or restore your entire FocusFlow database (subjects, chapters, tasks, sessions, widgets, and rules) without extra storage permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { exportDbLauncher.launch("focusflow-backup.json") },
                            modifier = Modifier.weight(1f).testTag("data_export_db")
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export backup")
                        }
                        OutlinedButton(
                            onClick = { importDbLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f).testTag("data_import_db")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { exportSettingsLauncher.launch("focusflow-settings.json") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Export settings") }
                        OutlinedButton(
                            onClick = { importSettingsLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Import settings") }
                    }
                }
            }

            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Local Wi-Fi peer sync")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pairing code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.settings.wifiSyncCode, style = MonoMetricMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(onClick = onRegenerateSyncCode) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New code")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onDiscoverPeers,
                        enabled = !state.lanSyncBusy,
                        modifier = Modifier.fillMaxWidth().testTag("data_discover_peers")
                    ) {
                        if (state.lanSyncBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.lanSyncBusy) "Scanning Wi-Fi..." else "Scan local Wi-Fi peers")
                    }
                    state.lanSyncMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.settings.wifiSyncLastSyncedAt?.let { ts ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Last synced with ${state.settings.wifiSyncLastPeer ?: "peer"} on ${DateFormat.format("MMM d, h:mm a", Date(ts))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    state.lanPeers.forEach { peer ->
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.deviceName, style = MaterialTheme.typography.titleMedium)
                                Text(peer.hostAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { onSyncWithPeer(peer) }) {
                                Text("Sync now")
                            }
                        }
                    }
                }
            }

            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Danger zone")
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("data_clear_study_data")
                    ) {
                        Text("Clear study history & tasks")
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Clear study data?") },
            text = { Text("This deletes all recorded focus sessions, summaries, and tasks while preserving subjects, chapters, and shield rules.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearStudyData()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear data") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
