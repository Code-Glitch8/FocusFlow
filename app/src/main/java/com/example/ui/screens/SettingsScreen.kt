package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode
import com.example.ui.FocusFlowUiState
import com.example.ui.FocusRoute
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingsNavRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FocusFlowUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onToggleBubble: (Boolean) -> Unit,
    onToggleStrictDefault: (Boolean) -> Unit,
    onNavigate: (FocusRoute) -> Unit
) {
    val activeAiBadge = state.activeProvider?.name ?: "Built-in Gemini"
    val blockedCount = state.blockedApps.size + if (state.settings.blockYoutubeApp) 1 else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }

        item {
            FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Appearance & behavior")
                Text("Theme", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { tm ->
                        FilterChip(
                            selected = state.settings.themeMode == tm,
                            onClick = { onSetThemeMode(tm) },
                            label = { Text(tm.label) },
                            modifier = Modifier.testTag("settings_theme_${tm.name.lowercase()}")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Floating timer assistant bubble", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.settings.assistantBubbleEnabled,
                        onCheckedChange = onToggleBubble,
                        modifier = Modifier.testTag("settings_bubble_switch")
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Strict shield by default", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.settings.strictDefault,
                        onCheckedChange = onToggleStrictDefault,
                        modifier = Modifier.testTag("settings_strict_default_switch")
                    )
                }
            }
        }

        item {
            SectionHeader("Workspace & protection", modifier = Modifier.padding(top = 4.dp))
        }

        item {
            SettingsNavRow(
                title = "App & YouTube Shield",
                subtitle = "Blocklists, YouTube Shorts, break rules, and Android permissions",
                icon = Icons.Filled.Shield,
                testTag = "settings_nav_blocking",
                badge = "$blockedCount blocked",
                onClick = { onNavigate(FocusRoute.Blocking) }
            )
        }

        item {
            SettingsNavRow(
                title = "AI, Voice & SnapGPT",
                subtitle = "Offline voice preference, auto summaries, and SnapGPT auto-launch",
                icon = Icons.Filled.AutoAwesome,
                testTag = "settings_nav_ai",
                badge = activeAiBadge,
                onClick = { onNavigate(FocusRoute.AiSettings) }
            )
        }

        item {
            SettingsNavRow(
                title = "AI Providers & Keys",
                subtitle = "Built-in Gemini AI (Firebase) plus custom OpenAI/OpenRouter profiles",
                icon = Icons.Filled.Key,
                testTag = "settings_nav_providers",
                onClick = { onNavigate(FocusRoute.AiProviders) }
            )
        }

        item {
            SettingsNavRow(
                title = "Subjects & Chapters",
                subtitle = "Customize subjects and Class 11 syllabus chapters",
                icon = Icons.Filled.FolderOpen,
                testTag = "settings_nav_subjects",
                badge = "${state.subjects.size} subjects",
                onClick = { onNavigate(FocusRoute.Subjects) }
            )
        }

        item {
            SettingsNavRow(
                title = "Edit Dashboard Widgets",
                subtitle = "Add, reorder, or customize Home dashboard tiles",
                icon = Icons.Filled.DashboardCustomize,
                testTag = "settings_nav_widgets",
                badge = "${state.widgets.size} widgets",
                onClick = { onNavigate(FocusRoute.Widgets) }
            )
        }

        item {
            SettingsNavRow(
                title = "Backup & Local Wi-Fi Sync",
                subtitle = "JSON export/import via Android Files & peer Wi-Fi sync",
                icon = Icons.Filled.CloudSync,
                testTag = "settings_nav_data",
                onClick = { onNavigate(FocusRoute.DataSettings) }
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
