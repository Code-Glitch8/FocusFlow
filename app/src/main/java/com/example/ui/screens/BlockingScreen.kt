package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.BreakConfigEntity
import com.example.domain.YouTubeUiClassifier
import com.example.model.AppCategory
import com.example.model.AppSettings
import com.example.model.ShortsMode
import com.example.model.YouTubeDetectionResult
import com.example.nativebridge.ShieldStateBridge
import com.example.ui.FocusFlowUiState
import com.example.ui.components.AppGuardBar
import com.example.ui.components.CatalogAppIcon
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockingScreen(
    state: FocusFlowUiState,
    onBack: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onSaveBreakConfig: (BreakConfigEntity) -> Unit,
    onToggleBlockedApp: (String, String, AppCategory, Boolean) -> Unit,
    onSetBlockedDuringBreak: (String, Boolean) -> Unit,
    onToggleAllowedApp: (String, String, AppCategory, Boolean) -> Unit,
    onAddCustomApp: (String, String, AppCategory, Boolean) -> Unit,
    onTestGuardTarget: (String, String, Boolean) -> Unit,
    onDismissGuardBanner: () -> Unit,
    onOpenDetectorTest: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var showAddAppDialog by remember { mutableStateOf(false) }

    val filteredCatalog = remember(state.allCatalogApps, searchQuery, selectedCategory) {
        state.allCatalogApps.filter { app ->
            (selectedCategory == null || app.category == selectedCategory) &&
                (searchQuery.isBlank() || app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    val coreShieldsReady = state.permissionHealth.overlayGranted &&
        (state.permissionHealth.accessibilityEnabled || state.permissionHealth.usageStatsGranted)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & YouTube Shield") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("blocking_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddAppDialog = true },
                        modifier = Modifier.testTag("blocking_add_custom_app")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add custom app")
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
            // Hero Shield Status Overview
            item {
                FocusFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (coreShieldsReady) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    borderColor = if (coreShieldsReady) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (coreShieldsReady) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Shield status",
                                tint = if (coreShieldsReady) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (coreShieldsReady) "Focus Shield ready" else "Permissions needed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (coreShieldsReady) {
                                    "Distracting apps & Shorts will close immediately and display the shield screen."
                                } else {
                                    "Grant Accessibility (or Usage Access) + Display over other apps below."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusSummaryPill(
                            icon = Icons.Filled.Block,
                            label = "${state.blockedApps.size} blocked",
                            tint = MaterialTheme.colorScheme.error
                        )
                        StatusSummaryPill(
                            icon = Icons.Filled.VerifiedUser,
                            label = "${state.allowedApps.size} allowed",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        StatusSummaryPill(
                            icon = Icons.Filled.SmartDisplay,
                            label = "Shorts: ${state.settings.blockYoutubeShorts.label}",
                            tint = Color(0xFFFF5252)
                        )
                    }
                }
            }

            // Android System Permissions
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    IconSectionHeader(
                        icon = Icons.Filled.Security,
                        tint = MaterialTheme.colorScheme.primary,
                        title = "Android Shield Permissions"
                    )
                    PermissionRow(
                        icon = Icons.Filled.AccessibilityNew,
                        title = "Accessibility Service",
                        subtitle = "Detects blocked apps and YouTube Shorts without logging keystrokes.",
                        granted = state.permissionHealth.accessibilityEnabled,
                        buttonLabel = "Open settings",
                        onClick = { ShieldStateBridge.openAccessibilitySettings(context) }
                    )
                    Spacer(Modifier.height(12.dp))
                    PermissionRow(
                        icon = Icons.Filled.QueryStats,
                        title = "Usage Access",
                        subtitle = "Fallback foreground app detection when Accessibility is off.",
                        granted = state.permissionHealth.usageStatsGranted,
                        buttonLabel = "Grant access",
                        onClick = { ShieldStateBridge.openUsageAccessSettings(context) }
                    )
                    Spacer(Modifier.height(12.dp))
                    PermissionRow(
                        icon = Icons.Filled.Layers,
                        title = "Display over other apps",
                        subtitle = "Shows the Focus Shield & auto-blending floating timer circle across apps.",
                        granted = state.permissionHealth.overlayGranted,
                        buttonLabel = "Allow overlay",
                        onClick = { ShieldStateBridge.openOverlaySettings(context) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(Modifier.height(12.dp))
                        PermissionRow(
                            icon = Icons.Filled.Notifications,
                            title = "Notifications",
                            subtitle = "Keeps live focus & break countdown active in the status bar.",
                            granted = state.permissionHealth.notificationsGranted,
                            buttonLabel = "Allow",
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                }
            }

            // Live App Guard Tester
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    IconSectionHeader(
                        icon = Icons.Filled.TouchApp,
                        tint = MaterialTheme.colorScheme.secondary,
                        title = "Test YouTube & App Shield right now"
                    )
                    AppGuardBar(
                        sessionActive = state.activeSession != null,
                        onBreak = state.activeTime?.isOnBreak == true,
                        settings = state.settings,
                        blockedApps = state.blockedApps,
                        allowedApps = state.allowedApps,
                        statusBanner = state.guardStatusBanner,
                        onDismissBanner = onDismissGuardBanner,
                        onTestTarget = onTestGuardTarget
                    )
                }
            }

            // YouTube & Shorts Independent Controls
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    IconSectionHeader(
                        icon = Icons.Filled.SmartDisplay,
                        tint = Color(0xFFFF5252),
                        title = "YouTube & Shorts (independent controls)"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block the entire YouTube app", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Shows Focus Shield when opening YouTube during focus sessions. Turn off to block only Shorts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.settings.blockYoutubeApp,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(blockYoutubeApp = checked) }
                            },
                            modifier = Modifier.testTag("blocking_yt_app_switch")
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Checkbox(
                            checked = state.settings.blockYoutubeAppDuringBreaks,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(blockYoutubeAppDuringBreaks = checked) }
                            },
                            enabled = state.settings.blockYoutubeApp
                        )
                        Text("Keep YouTube blacklisted even during breaks", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Block YouTube Shorts only", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Regular YouTube videos and search stay open while Shorts is blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShortsMode.entries.forEach { sm ->
                            FilterChip(
                                selected = state.settings.blockYoutubeShorts == sm,
                                onClick = { onUpdateSettings { it.copy(blockYoutubeShorts = sm) } },
                                label = { Text(sm.label) },
                                modifier = Modifier.testTag("blocking_shorts_${sm.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Skip the Home feed", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "When YouTube opens on Home, jump to Subscriptions instead.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.settings.skipYoutubeHomeFeed,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(skipYoutubeHomeFeed = checked) }
                            },
                            modifier = Modifier.testTag("blocking_skip_home_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenDetectorTest,
                        modifier = Modifier.height(44.dp).testTag("blocking_test_detector_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test the YouTube detector")
                    }
                }
            }

            // Strict Allowlist Mode
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    IconSectionHeader(
                        icon = Icons.Filled.VerifiedUser,
                        tint = MaterialTheme.colorScheme.secondary,
                        title = "Strict allowlist mode"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block all non-essential apps except allowed", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Phone, Dialer, Settings, Contacts, and FocusFlow always stay reachable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.settings.blockAllExceptAllowed,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(blockAllExceptAllowed = checked) }
                            },
                            modifier = Modifier.testTag("blocking_allowlist_switch")
                        )
                    }
                }
            }

            // Earned Breaks Config
            item {
                val bc = state.breakConfig
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    IconSectionHeader(
                        icon = Icons.Filled.Coffee,
                        tint = MaterialTheme.colorScheme.primary,
                        title = "Earned break rules"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable earned study breaks", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = bc.enabled,
                            onCheckedChange = { onSaveBreakConfig(bc.copy(enabled = it)) }
                        )
                    }
                    if (bc.enabled) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StepperField(
                                label = "Tasks / short break",
                                value = bc.tasksForShortBreak,
                                onChange = { v -> onSaveBreakConfig(bc.copy(tasksForShortBreak = v.coerceIn(1, 10))) },
                                modifier = Modifier.weight(1f)
                            )
                            StepperField(
                                label = "Short break (min)",
                                value = bc.shortBreakMinutes,
                                onChange = { v -> onSaveBreakConfig(bc.copy(shortBreakMinutes = v.coerceIn(1, 30))) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StepperField(
                                label = "Tasks / long break",
                                value = bc.tasksForLongBreak,
                                onChange = { v -> onSaveBreakConfig(bc.copy(tasksForLongBreak = v.coerceIn(2, 20))) },
                                modifier = Modifier.weight(1f)
                            )
                            StepperField(
                                label = "Long break (min)",
                                value = bc.longBreakMinutes,
                                onChange = { v -> onSaveBreakConfig(bc.copy(longBreakMinutes = v.coerceIn(5, 60))) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // App Catalog Header + Search + Category Filter
            item {
                IconSectionHeader(
                    icon = Icons.Filled.Apps,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "App blocklist & allowlist (${filteredCatalog.size})"
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps or package names") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") }
                    )
                    AppCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }

            // App Catalog Cards with real / stylized icons & Block/Allow controls
            items(filteredCatalog, key = { it.packageName }) { app ->
                val blockedEntry = state.blockedApps.find { it.packageName == app.packageName }
                val isBlocked = blockedEntry != null
                val isAllowed = state.allowedApps.any { it.packageName == app.packageName }

                FocusFlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = when {
                        isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                        isAllowed -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatalogAppIcon(
                            packageName = app.packageName,
                            label = app.label,
                            category = app.category,
                            isBlocked = isBlocked,
                            isAllowed = isAllowed,
                            size = 44.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${app.category.label} · ${app.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isBlocked,
                            onClick = { onToggleBlockedApp(app.packageName, app.label, app.category, !isBlocked) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(if (isBlocked) "Blocked" else "Block") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f).testTag("app_block_${app.packageName}")
                        )
                        FilterChip(
                            selected = isAllowed,
                            onClick = { onToggleAllowedApp(app.packageName, app.label, app.category, !isAllowed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(if (isAllowed) "Allowed" else "Allow") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f).testTag("app_allow_${app.packageName}")
                        )
                    }

                    if (isBlocked && blockedEntry != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Checkbox(
                                checked = blockedEntry.blockedDuringBreak,
                                onCheckedChange = { onSetBlockedDuringBreak(app.packageName, it) }
                            )
                            Text("Keep locked even during breaks", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddAppDialog) {
        var pkg by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        var addToBlocked by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            title = { Text("Add custom app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("App name (e.g. AnkiDroid)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pkg,
                        onValueChange = { pkg = it },
                        label = { Text("Package name (e.g. com.ichi2.anki)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = addToBlocked, onClick = { addToBlocked = true }, label = { Text("Add to Blocklist") })
                        FilterChip(selected = !addToBlocked, onClick = { addToBlocked = false }, label = { Text("Add to Allowlist") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomApp(pkg, label, AppCategory.CUSTOM, addToBlocked)
                        showAddAppDialog = false
                    },
                    enabled = pkg.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAppDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun IconSectionHeader(
    icon: ImageVector,
    tint: Color,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusSummaryPill(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (granted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (granted) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = if (granted) "Enabled" else "Required",
                        tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (granted) "Manage" else buttonLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StepperField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onChange(value - 1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
                }
                Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { onChange(value + 1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $label")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetectorTestScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var jsonText by remember { mutableStateOf(YouTubeUiClassifier.SAMPLE_SHORTS_JSON) }
    var result by remember { mutableStateOf<YouTubeDetectionResult?>(YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(YouTubeUiClassifier.SAMPLE_SHORTS_JSON))) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube UI Detector Test") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detector_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Sample UI node fixtures")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            jsonText = YouTubeUiClassifier.SAMPLE_SHORTS_JSON
                            errorText = null
                            result = YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(jsonText))
                        }) { Text("Load Shorts sample") }
                        OutlinedButton(onClick = {
                            jsonText = YouTubeUiClassifier.SAMPLE_HOME_JSON
                            errorText = null
                            result = YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(jsonText))
                        }) { Text("Load Home feed sample") }
                        OutlinedButton(onClick = {
                            jsonText = YouTubeUiClassifier.SAMPLE_VIDEO_JSON
                            errorText = null
                            result = YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(jsonText))
                        }) { Text("Load Regular video") }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        label = { Text("Accessibility UiNode JSON tree") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth().testTag("detector_json_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            runCatching {
                                val parsed = YouTubeUiClassifier.parseUiNodeJson(jsonText)
                                YouTubeUiClassifier.classify(parsed)
                            }.onSuccess {
                                errorText = null
                                result = it
                            }.onFailure {
                                errorText = "Invalid JSON tree: ${it.localizedMessage}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("detector_run_button")
                    ) {
                        Text("Run classifier")
                    }
                }
            }

            errorText?.let { err ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        )
                    }
                }
            }

            result?.let { res ->
                item {
                    FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader("Classification result")
                        Text(res.kind.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(res.reason, style = MaterialTheme.typography.bodyMedium)
                        res.matchedId?.let { mid ->
                            Spacer(Modifier.height(4.dp))
                            Text("Matched ID: $mid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (res.sampledViewIds.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Sampled view IDs: ${res.sampledViewIds.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
