package com.example.nativebridge

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.FocusFlowRepository
import com.example.data.local.DashboardWidgetEntity
import com.example.data.local.FocusFlowDatabase
import com.example.data.local.SettingsDataStore
import com.example.model.AppSettings
import com.example.model.FocusMode
import com.example.model.WidgetType
import com.example.ui.components.FocusFlowCard
import com.example.ui.theme.FocusFlowTheme
import kotlinx.coroutines.launch

class FocusFlowWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val dao = FocusFlowDatabase.getInstance(applicationContext).dao()
        val store = SettingsDataStore(applicationContext)
        val repo = FocusFlowRepository(dao, store)

        setContent {
            var widgets by remember { mutableStateOf<List<DashboardWidgetEntity>>(emptyList()) }
            var settings by remember { mutableStateOf(AppSettings()) }

            LaunchedEffect(Unit) {
                repo.ensureSeeded()
                settings = store.getSettingsOnce()
                widgets = dao.getWidgetsOnce()
                    .filter { it.type != WidgetType.PROGRESS && it.type != WidgetType.QUOTE }
            }

            FocusFlowTheme(themeMode = settings.themeMode) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Choose session for widget") })
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Tapping Start on this home screen widget will automatically start the selected session and tasks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(widgets, key = { it.id }) { widget ->
                            val tasks = settings.widgetPresetTasks[widget.id].orEmpty()
                            val dur = if (widget.mode == FocusMode.OPEN_ENDED) "Open-ended"
                            else "${widget.durationMinutes ?: 25} min"

                            FocusFlowCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { bindAndFinish(widget.id) }
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(widget.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = if (tasks.isNotEmpty()) "$dur · ${tasks.size} preset tasks" else dur,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (tasks.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = tasks.joinToString(", ") { it.title },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindAndFinish(widgetId: Long) {
        lifecycleScope.launch {
            val store = SettingsDataStore(applicationContext)
            store.updateSettings { s ->
                s.copy(androidWidgetBindings = s.androidWidgetBindings + (appWidgetId to widgetId))
            }
            val mgr = AppWidgetManager.getInstance(applicationContext)
            FocusFlowWidgetProvider.updateWidgetsInternal(applicationContext, mgr, intArrayOf(appWidgetId))
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}
