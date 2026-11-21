package com.example.nativebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.FocusFlowRepository
import com.example.data.local.FocusFlowDatabase
import com.example.data.local.SettingsDataStore
import com.example.domain.BlockPolicy
import com.example.domain.SessionClock
import com.example.model.ActiveShieldAlert
import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null
    private var lastWidgetStateKey: String = ""
    private var lastUsageAlertPkg: String = ""
    private var lastUsageAlertMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tickerJob?.cancel()
            tickerJob = null
            FloatingOverlayManager.hide(applicationContext)
            FocusFlowWidgetProvider.refreshAllWidgets(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Focus session active" }
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE).orEmpty().ifBlank { "Stay focused" }
        val notification = buildNotification(title, subtitle)
        startForeground(NOTIFICATION_ID, notification)

        ensureTickerRunning()
        return START_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureTickerRunning() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            val ctx = applicationContext
            val dao = FocusFlowDatabase.getInstance(ctx).dao()
            val store = SettingsDataStore(ctx)
            val repo = FocusFlowRepository(dao, store)
            val notifMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val active = dao.getSessionsOnce().firstOrNull { it.status == SessionStatus.ACTIVE }
                if (active == null) {
                    FloatingOverlayManager.hide(ctx)
                    FocusFlowWidgetProvider.refreshAllWidgets(ctx)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                // Settle expired breaks
                if (active.breakEndsAt != null && nowMs >= active.breakEndsAt) {
                    repo.settleExpiredBreakIfNeeded(active.id, nowMs)
                }

                val refreshed = dao.getSessionById(active.id) ?: active
                val snap = SessionClock.computeSnapshot(refreshed, nowMs)

                // If app is backgrounded/closed and timed session ends, finish cleanly in Room
                if (snap.isTimedFinished && !ShieldStateBridge.appInForeground.value) {
                    val exactEnd = SessionClock.computeExactEndedAt(refreshed, nowMs)
                    repo.completeSession(refreshed.id, exactEnd, abandoned = false)
                    ShieldStateBridge.updatePolicy(
                        ShieldStateBridge.policy.value.copy(
                            sessionActive = false,
                            sessionLabel = "",
                            remainingLabel = "",
                            onBreak = false
                        )
                    )
                    FloatingOverlayManager.hide(ctx)
                    FocusFlowWidgetProvider.refreshAllWidgets(ctx)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                val clockText = when {
                    snap.isOnBreak -> SessionClock.formatClock(snap.breakRemainingMs)
                    snap.remainingMs != null -> SessionClock.formatClock(snap.remainingMs)
                    else -> SessionClock.formatClock(snap.focusElapsedMs)
                }

                val settings = store.getSettingsOnce()
                val blockedApps = dao.getBlockedAppsOnce()
                val allowedApps = dao.getAllowedAppsOnce()
                ShieldStateBridge.updatePolicy(
                    ShieldPolicySnapshot(
                        sessionActive = true,
                        sessionLabel = refreshed.label,
                        remainingLabel = clockText,
                        onBreak = snap.isOnBreak,
                        settings = settings,
                        blockedApps = blockedApps,
                        allowedApps = allowedApps
                    )
                )

                val notifTitle = if (snap.isOnBreak) {
                    "On Break · ${refreshed.label}"
                } else {
                    "Focusing · ${refreshed.label}"
                }
                val notifSub = when {
                    snap.isOnBreak -> "$clockText break remaining"
                    snap.remainingMs != null -> "$clockText remaining"
                    else -> "$clockText elapsed"
                }
                notifMgr.notify(NOTIFICATION_ID, buildNotification(notifTitle, notifSub))

                // Sync system-wide floating overlay when app is outside foreground
                FloatingOverlayManager.sync(ctx)

                // Sync home screen widgets when session/break/tasks change
                val doneTasks = dao.getSessionTasksOnce().count { it.sessionId == refreshed.id && it.completed }
                val widgetKey = "${refreshed.id}|${snap.isOnBreak}|${refreshed.plannedDurationMinutes}|$doneTasks"
                if (widgetKey != lastWidgetStateKey) {
                    lastWidgetStateKey = widgetKey
                    FocusFlowWidgetProvider.refreshAllWidgets(ctx)
                }

                // Fallback UsageStats blocker when Accessibility Service is off
                val perms = ShieldStateBridge.checkPermissions(ctx)
                if (!perms.accessibilityEnabled && perms.usageStatsGranted && !ShieldStateBridge.appInForeground.value) {
                    val fgPkg = UsageStatsHelper.queryRecentForegroundPackage(ctx)
                    if (fgPkg != null && fgPkg != ctx.packageName && fgPkg !in BlockPolicy.ESSENTIAL_PACKAGES) {
                        val blockedEntry = blockedApps.find { it.packageName == fgPkg }
                        val allowedEntry = allowedApps.find { it.packageName == fgPkg }
                        val appLabel = blockedEntry?.label ?: allowedEntry?.label
                            ?: fgPkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                        val decision = BlockPolicy.evaluateApp(
                            packageName = fgPkg,
                            appLabel = appLabel,
                            sessionActive = true,
                            onBreak = snap.isOnBreak,
                            settings = settings,
                            blockedApps = blockedApps,
                            allowedApps = allowedApps
                        )
                        if (decision.shouldBlock && (fgPkg != lastUsageAlertPkg || nowMs - lastUsageAlertMs >= 2500L)) {
                            lastUsageAlertPkg = fgPkg
                            lastUsageAlertMs = nowMs
                            ShieldOverlayManager.showShield(
                                ctx,
                                ActiveShieldAlert(
                                    packageName = fgPkg,
                                    appLabel = appLabel,
                                    reason = decision.reason,
                                    isYoutubeShorts = false,
                                    isYoutubeApp = fgPkg == BlockPolicy.YOUTUBE_PACKAGE,
                                    triggeredAt = nowMs
                                )
                            )
                        }
                    }
                }

                delay(1000L)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_timer_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_timer_desc)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, subtitle: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.notification_action_open), pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "focusflow_timer_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START_OR_UPDATE = "com.example.action.UPDATE_TIMER"
        const val ACTION_STOP = "com.example.action.STOP_TIMER"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        fun sync(context: Context, active: Boolean, title: String, subtitle: String) {
            val intent = Intent(context, FocusTimerService::class.java)
            if (active) {
                intent.action = ACTION_START_OR_UPDATE
                intent.putExtra(EXTRA_TITLE, title)
                intent.putExtra(EXTRA_SUBTITLE, subtitle)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { context.startForegroundService(intent) }
                } else {
                    runCatching { context.startService(intent) }
                }
            } else {
                intent.action = ACTION_STOP
                runCatching { context.startService(intent) }
            }
        }
    }
}
