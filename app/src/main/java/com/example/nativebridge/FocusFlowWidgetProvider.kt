package com.example.nativebridge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.data.FocusFlowRepository
import com.example.data.local.FocusFlowDatabase
import com.example.data.local.SettingsDataStore
import com.example.domain.SessionClock
import com.example.model.FocusMode
import com.example.model.SessionStatus
import com.example.model.WidgetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusFlowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetsInternal(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetsInternal(context.applicationContext, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val store = SettingsDataStore(context.applicationContext)
                store.updateSettings { s ->
                    val next = s.androidWidgetBindings.toMutableMap()
                    appWidgetIds.forEach { next.remove(it) }
                    s.copy(androidWidgetBindings = next)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_START_WIDGET_SESSION -> {
                val boundWidgetId = intent.getLongExtra(EXTRA_WIDGET_ID, -1L).takeIf { it > 0L }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val dao = FocusFlowDatabase.getInstance(appContext).dao()
                        val store = SettingsDataStore(appContext)
                        val repo = FocusFlowRepository(dao, store)
                        val sessionId = repo.startSessionFromWidget(boundWidgetId)
                        val session = dao.getSessionById(sessionId)
                        if (session != null) {
                            val snap = SessionClock.computeSnapshot(session, System.currentTimeMillis())
                            val sub = if (snap.remainingMs != null) {
                                "${SessionClock.formatClock(snap.remainingMs)} remaining"
                            } else {
                                "${SessionClock.formatClock(snap.focusElapsedMs)} elapsed"
                            }
                            FocusTimerService.sync(appContext, true, "Focusing · ${session.label}", sub)
                            FloatingOverlayManager.sync(appContext)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(appContext, "Started ${session.label}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        val mgr = AppWidgetManager.getInstance(appContext)
                        val ids = mgr.getAppWidgetIds(ComponentName(appContext, FocusFlowWidgetProvider::class.java))
                        updateWidgetsInternal(appContext, mgr, ids)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_REFRESH_WIDGETS -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val mgr = AppWidgetManager.getInstance(appContext)
                        val ids = mgr.getAppWidgetIds(ComponentName(appContext, FocusFlowWidgetProvider::class.java))
                        updateWidgetsInternal(appContext, mgr, ids)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_WIDGET_PINNED -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val boundWidgetId = intent.getLongExtra(EXTRA_WIDGET_ID, -1L)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && boundWidgetId > 0L) {
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val store = SettingsDataStore(appContext)
                            store.updateSettings { s ->
                                s.copy(androidWidgetBindings = s.androidWidgetBindings + (appWidgetId to boundWidgetId))
                            }
                            val mgr = AppWidgetManager.getInstance(appContext)
                            updateWidgetsInternal(appContext, mgr, intArrayOf(appWidgetId))
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_START_WIDGET_SESSION = "com.example.action.START_WIDGET_SESSION"
        const val ACTION_REFRESH_WIDGETS = "com.example.action.REFRESH_WIDGETS"
        const val ACTION_WIDGET_PINNED = "com.example.action.WIDGET_PINNED"
        const val EXTRA_WIDGET_ID = "extra_widget_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                val mgr = AppWidgetManager.getInstance(appContext)
                val ids = mgr.getAppWidgetIds(ComponentName(appContext, FocusFlowWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    updateWidgetsInternal(appContext, mgr, ids)
                }
            }
        }

        suspend fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return
            val dao = FocusFlowDatabase.getInstance(context).dao()
            val store = SettingsDataStore(context)
            val repo = FocusFlowRepository(dao, store)
            repo.ensureSeeded()

            val settings = store.getSettingsOnce()
            val widgets = dao.getWidgetsOnce()
            val active = dao.getSessionsOnce().firstOrNull { it.status == SessionStatus.ACTIVE }
            val activeTasks = active?.let { sess ->
                dao.getSessionTasksOnce().filter { it.sessionId == sess.id }
            }.orEmpty()
            val nowMs = System.currentTimeMillis()
            val snap = active?.let { SessionClock.computeSnapshot(it, nowMs) }
            val density = context.resources.displayMetrics.density

            for (appWidgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

                val isCompact = minWidthDp < 175 || minHeightDp < 92
                val isTall = minHeightDp >= 165

                val views = RemoteViews(context.packageName, R.layout.widget_focusflow)
                val padPx = ((if (isCompact) 10 else 14) * density).toInt()
                views.setViewPadding(R.id.widget_root, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_header_row, if (minHeightDp < 78) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_meta_right, if (minWidthDp < 160) View.GONE else View.VISIBLE)
                views.setTextViewTextSize(
                    R.id.widget_title,
                    TypedValue.COMPLEX_UNIT_SP,
                    when {
                        isCompact -> 13f
                        isTall -> 18f
                        else -> 16f
                    }
                )
                views.setTextViewTextSize(
                    R.id.widget_chronometer,
                    TypedValue.COMPLEX_UNIT_SP,
                    when {
                        isCompact -> 20f
                        isTall -> 34f
                        else -> 28f
                    }
                )

                val openFocusIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_FOCUS, active != null)
                }
                val openPending = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    openFocusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openPending)
                views.setOnClickPendingIntent(R.id.widget_open_btn, openPending)

                if (active != null && snap != null) {
                    views.setViewVisibility(R.id.widget_active_container, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_idle_container, View.GONE)

                    views.setTextViewText(
                        R.id.widget_status_badge,
                        if (snap.isOnBreak) "ON BREAK" else "SESSION IN PROGRESS"
                    )
                    views.setTextColor(
                        R.id.widget_status_badge,
                        if (snap.isOnBreak) 0xFF7FD6C2.toInt() else 0xFF6EA8FF.toInt()
                    )
                    val doneTasks = activeTasks.count { it.completed }
                    views.setTextViewText(
                        R.id.widget_meta_right,
                        if (activeTasks.isNotEmpty()) "$doneTasks/${activeTasks.size} tasks" else active.mode.label
                    )
                    views.setTextViewText(R.id.widget_title, active.label)
                    views.setViewVisibility(R.id.widget_open_btn, if (isCompact) View.GONE else View.VISIBLE)

                    if (isTall && activeTasks.isNotEmpty()) {
                        views.setViewVisibility(R.id.widget_active_tasks_preview, View.VISIBLE)
                        val taskLines = activeTasks.take(5).joinToString("\n") { t ->
                            val mark = if (t.completed) "✓" else "•"
                            "$mark ${t.title} (${t.estimatedMinutes}m)"
                        }
                        views.setTextViewText(R.id.widget_active_tasks_preview, taskLines)
                    } else {
                        views.setViewVisibility(R.id.widget_active_tasks_preview, View.GONE)
                    }

                    val elapsedBase = SystemClock.elapsedRealtime()
                    if (snap.isOnBreak) {
                        val base = elapsedBase + snap.breakRemainingMs
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setChronometerCountDown(R.id.widget_chronometer, true)
                        }
                        views.setChronometer(R.id.widget_chronometer, base, "%s", true)
                        views.setTextColor(R.id.widget_chronometer, 0xFF7FD6C2.toInt())
                    } else if (snap.remainingMs != null) {
                        val base = elapsedBase + snap.remainingMs
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setChronometerCountDown(R.id.widget_chronometer, true)
                        }
                        views.setChronometer(R.id.widget_chronometer, base, "%s", true)
                        views.setTextColor(R.id.widget_chronometer, 0xFF6EA8FF.toInt())
                    } else {
                        val base = elapsedBase - snap.focusElapsedMs
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setChronometerCountDown(R.id.widget_chronometer, false)
                        }
                        views.setChronometer(R.id.widget_chronometer, base, "%s", true)
                        views.setTextColor(R.id.widget_chronometer, 0xFF6EA8FF.toInt())
                    }
                } else {
                    views.setViewVisibility(R.id.widget_active_container, View.GONE)
                    views.setViewVisibility(R.id.widget_idle_container, View.VISIBLE)

                    val boundId = settings.androidWidgetBindings[appWidgetId]
                    val target = widgets.find { it.id == boundId }
                        ?: widgets.firstOrNull { it.type != WidgetType.PROGRESS && it.type != WidgetType.QUOTE }
                    val presetTasks = target?.id?.let { id -> settings.widgetPresetTasks[id] }.orEmpty()

                    views.setTextViewText(R.id.widget_status_badge, "FOCUSFLOW")
                    views.setTextColor(R.id.widget_status_badge, 0xFF6EA8FF.toInt())
                    val durLabel = if (target?.mode == FocusMode.OPEN_ENDED) {
                        "Open-ended"
                    } else {
                        "${target?.durationMinutes ?: 25} min"
                    }
                    views.setTextViewText(
                        R.id.widget_meta_right,
                        if (presetTasks.isNotEmpty()) "${presetTasks.size} tasks" else "Tap Start"
                    )
                    views.setTextViewText(R.id.widget_title, target?.title ?: "Focus 25")
                    views.setTextViewText(
                        R.id.widget_subtitle,
                        if (presetTasks.isNotEmpty()) "$durLabel · ${presetTasks.size} queued" else "$durLabel · Ready"
                    )

                    if (!isCompact && presetTasks.isNotEmpty()) {
                        views.setViewVisibility(R.id.widget_tasks_preview, View.VISIBLE)
                        val maxItems = if (isTall) 5 else 2
                        val separator = if (isTall) "\n" else "  •  "
                        val preview = if (isTall) {
                            presetTasks.take(maxItems).joinToString(separator) { "• ${it.title} (~${it.estimatedMinutes}m)" }
                        } else {
                            "• " + presetTasks.take(maxItems).joinToString(separator) { it.title }
                        }
                        views.setTextViewText(R.id.widget_tasks_preview, preview)
                    } else {
                        views.setViewVisibility(R.id.widget_tasks_preview, View.GONE)
                    }

                    val startIntent = Intent(context, FocusFlowWidgetProvider::class.java).apply {
                        action = ACTION_START_WIDGET_SESSION
                        putExtra(EXTRA_WIDGET_ID, target?.id ?: -1L)
                    }
                    val startPending = PendingIntent.getBroadcast(
                        context,
                        10_000 + appWidgetId,
                        startIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_start_btn, startPending)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
