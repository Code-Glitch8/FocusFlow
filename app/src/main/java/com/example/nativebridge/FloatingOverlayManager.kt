package com.example.nativebridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.MainActivity
import com.example.data.FocusFlowRepository
import com.example.data.SeedData
import com.example.data.local.FocusFlowDatabase
import com.example.data.local.FocusSessionEntity
import com.example.data.local.SessionTaskEntity
import com.example.data.local.SettingsDataStore
import com.example.domain.EarnedBreakCalculator
import com.example.domain.SessionClock
import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Renders the system-wide draggable floating timer circle & expandable task checklist
 * over every app using WindowManager TYPE_APPLICATION_OVERLAY whenever FocusFlow is backgrounded.
 * After IDLE_BLEND_DELAY_MS without interaction, the collapsed circle smoothly dims & scales slightly
 * so it blends unobtrusively into whatever screen is open behind it.
 */
object FloatingOverlayManager {

    private const val IDLE_BLEND_DELAY_MS = 4000L
    private const val IDLE_BLEND_ALPHA = 0.30f
    private const val IDLE_BLEND_SCALE = 0.86f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var expanded: Boolean = false
    private var isBlendedIdle: Boolean = false

    private var savedX: Int = 24
    private var savedY: Int = 280

    private var lastRenderedKey: String = ""
    private var circleClockView: TextView? = null
    private var cardClockView: TextView? = null
    private var cardStatusView: TextView? = null

    private val blendIdleRunnable = Runnable {
        if (!expanded) {
            applyCircleBlendState(idle = true, animate = true)
        }
    }

    fun sync(context: Context) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext) || ShieldStateBridge.appInForeground.value) {
            hide(appContext)
            return
        }
        ioScope.launch {
            val dao = FocusFlowDatabase.getInstance(appContext).dao()
            val settings = SettingsDataStore(appContext).getSettingsOnce()
            val active = dao.getSessionsOnce().firstOrNull { it.status == SessionStatus.ACTIVE }
            if (active == null || !settings.assistantBubbleEnabled) {
                mainHandler.post { hide(appContext) }
                return@launch
            }
            val tasks = dao.getSessionTasksOnce().filter { it.sessionId == active.id }
            val breakConfig = dao.getBreakConfigOnce() ?: SeedData.defaultBreakConfig()
            val nowMs = System.currentTimeMillis()
            val snap = SessionClock.computeSnapshot(active, nowMs)
            val earned = EarnedBreakCalculator.compute(
                completedTasks = tasks.count { it.completed },
                shortBreaksUsed = active.shortBreaksUsed,
                longBreaksUsed = active.longBreaksUsed,
                config = breakConfig
            )

            mainHandler.post {
                if (ShieldStateBridge.appInForeground.value || !Settings.canDrawOverlays(appContext)) {
                    hide(appContext)
                    return@post
                }
                val renderKey = buildString {
                    append(expanded).append('|')
                    append(active.id).append('|')
                    append(active.label).append('|')
                    append(snap.isOnBreak).append('|')
                    append(earned.shortAvailable).append('|').append(earned.longAvailable).append('|')
                    tasks.forEach { t -> append(t.id).append(':').append(t.completed).append(',') }
                }

                val clockText = when {
                    snap.isOnBreak -> SessionClock.formatClock(snap.breakRemainingMs)
                    snap.remainingMs != null -> SessionClock.formatClock(snap.remainingMs)
                    else -> SessionClock.formatClock(snap.focusElapsedMs)
                }

                if (currentView == null || renderKey != lastRenderedKey) {
                    lastRenderedKey = renderKey
                    if (expanded) {
                        cancelIdleBlendTimer()
                        isBlendedIdle = false
                        showExpandedCard(
                            context = appContext,
                            session = active,
                            tasks = tasks,
                            clockText = clockText,
                            isOnBreak = snap.isOnBreak,
                            shortAvailable = earned.shortAvailable,
                            shortMinutes = breakConfig.shortBreakMinutes,
                            longAvailable = earned.longAvailable,
                            longMinutes = breakConfig.longBreakMinutes
                        )
                    } else {
                        showCompactCircle(appContext, clockText, snap.isOnBreak)
                    }
                } else {
                    // Refresh clock text smoothly every tick without rebuilding views or interrupting fade
                    circleClockView?.text = clockText
                    cardClockView?.text = clockText
                    cardStatusView?.text = if (snap.isOnBreak) "ON BREAK · $clockText" else "FOCUSING · $clockText"
                }
            }
        }
    }

    fun hide(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            cancelIdleBlendTimer()
            isBlendedIdle = false
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            currentView?.let { view ->
                view.animate().cancel()
                runCatching { wm?.removeViewImmediate(view) }
            }
            currentView = null
            currentParams = null
            circleClockView = null
            cardClockView = null
            cardStatusView = null
            lastRenderedKey = ""
        }
    }

    private fun scheduleIdleBlendTimer() {
        mainHandler.removeCallbacks(blendIdleRunnable)
        mainHandler.postDelayed(blendIdleRunnable, IDLE_BLEND_DELAY_MS)
    }

    private fun cancelIdleBlendTimer() {
        mainHandler.removeCallbacks(blendIdleRunnable)
    }

    private fun wakeCircleAndScheduleBlend() {
        applyCircleBlendState(idle = false, animate = true)
        scheduleIdleBlendTimer()
    }

    private fun applyCircleBlendState(idle: Boolean, animate: Boolean) {
        val view = currentView ?: return
        if (expanded) return
        if (isBlendedIdle == idle && animate) return
        isBlendedIdle = idle
        val targetAlpha = if (idle) IDLE_BLEND_ALPHA else 1.0f
        val targetScale = if (idle) IDLE_BLEND_SCALE else 1.0f
        view.animate().cancel()
        if (animate) {
            view.animate()
                .alpha(targetAlpha)
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(if (idle) 420L else 140L)
                .start()
        } else {
            view.alpha = targetAlpha
            view.scaleX = targetScale
            view.scaleY = targetScale
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCompactCircle(context: Context, clockText: String, isOnBreak: Boolean) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val wasCircleShowing = currentView != null && !expanded
        removeExisting(wm)

        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isOnBreak) Color.parseColor("#7FD6C2") else Color.parseColor("#6EA8FF"))
                setStroke(dp(2), Color.parseColor("#181E28"))
            }
            elevation = dp(8).toFloat()
            alpha = if (wasCircleShowing && isBlendedIdle) IDLE_BLEND_ALPHA else 1.0f
            scaleX = if (wasCircleShowing && isBlendedIdle) IDLE_BLEND_SCALE else 1.0f
            scaleY = if (wasCircleShowing && isBlendedIdle) IDLE_BLEND_SCALE else 1.0f
        }

        val clockTv = TextView(context).apply {
            text = clockText
            textSize = 13f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0E1116"))
            gravity = Gravity.CENTER
        }
        circleClockView = clockTv
        container.addView(clockTv)

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            typeFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = savedX
            y = savedY
        }

        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wakeCircleAndScheduleBlend()
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = (startX - dx.toInt()).coerceAtLeast(0)
                    params.y = (startY + dy.toInt()).coerceAtLeast(0)
                    savedX = params.x
                    savedY = params.y
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        cancelIdleBlendTimer()
                        isBlendedIdle = false
                        expanded = true
                        sync(context)
                    } else {
                        scheduleIdleBlendTimer()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    scheduleIdleBlendTimer()
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(container, params)
            currentView = container
            currentParams = params
            if (!wasCircleShowing || !isBlendedIdle) {
                isBlendedIdle = false
                scheduleIdleBlendTimer()
            }
        }
    }

    private fun showExpandedCard(
        context: Context,
        session: FocusSessionEntity,
        tasks: List<SessionTaskEntity>,
        clockText: String,
        isOnBreak: Boolean,
        shortAvailable: Int,
        shortMinutes: Int,
        longAvailable: Int,
        longMinutes: Int
    ) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        removeExisting(wm)

        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151A21"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (isOnBreak) Color.parseColor("#7FD6C2") else Color.parseColor("#6EA8FF"))
            }
            elevation = dp(12).toFloat()
        }

        // Top row: Status + Minimize
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val statusTv = TextView(context).apply {
            text = if (isOnBreak) "ON BREAK · $clockText" else "FOCUSING · $clockText"
            textSize = 11f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(if (isOnBreak) Color.parseColor("#7FD6C2") else Color.parseColor("#6EA8FF"))
        }
        cardStatusView = statusTv
        val titleTv = TextView(context).apply {
            text = session.label
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#F1F4F8"))
            maxLines = 1
        }
        titleCol.addView(statusTv)
        titleCol.addView(titleTv)
        header.addView(titleCol)

        val minimizeBtn = Button(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.parseColor("#9AA4B2"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                expanded = false
                isBlendedIdle = false
                sync(context)
            }
        }
        header.addView(minimizeBtn)
        card.addView(header)

        // Large live clock + task progress summary
        val clockRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        val bigClock = TextView(context).apply {
            text = clockText
            textSize = 28f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(if (isOnBreak) Color.parseColor("#7FD6C2") else Color.parseColor("#F1F4F8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        cardClockView = bigClock
        val doneCount = tasks.count { it.completed }
        val progressTv = TextView(context).apply {
            text = "$doneCount/${tasks.size} tasks"
            textSize = 12f
            setTextColor(Color.parseColor("#9AA4B2"))
        }
        clockRow.addView(bigClock)
        clockRow.addView(progressTv)
        card.addView(clockRow)

        // Scrollable tasks checklist
        if (tasks.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "No tasks added to this session."
                textSize = 13f
                setTextColor(Color.parseColor("#9AA4B2"))
                setPadding(0, 0, 0, dp(10))
            }
            card.addView(emptyTv)
        } else {
            val scroll = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    minOf(dp(180), tasks.size * dp(46) + dp(8))
                )
            }
            val taskList = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            tasks.forEach { task ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                val cb = CheckBox(context).apply {
                    isChecked = task.completed
                    setOnCheckedChangeListener { _, _ ->
                        ioScope.launch {
                            val repo = FocusFlowRepository(
                                FocusFlowDatabase.getInstance(context).dao(),
                                SettingsDataStore(context)
                            )
                            repo.toggleSessionTask(task)
                            FocusFlowWidgetProvider.refreshAllWidgets(context)
                            sync(context)
                        }
                    }
                }
                val label = TextView(context).apply {
                    text = "${task.title} (~${task.estimatedMinutes}m)"
                    textSize = 13f
                    setTextColor(if (task.completed) Color.parseColor("#7C8594") else Color.parseColor("#F1F4F8"))
                    if (task.completed) {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { cb.performClick() }
                }
                row.addView(cb)
                row.addView(label)
                taskList.addView(row)
            }
            scroll.addView(taskList)
            card.addView(scroll)
        }

        // Actions Row
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        if (isOnBreak) {
            val endBreakBtn = Button(context).apply {
                text = "End break"
                isAllCaps = false
                setTextColor(Color.parseColor("#0E1116"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#7FD6C2"))
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    ioScope.launch {
                        val repo = FocusFlowRepository(
                            FocusFlowDatabase.getInstance(context).dao(),
                            SettingsDataStore(context)
                        )
                        repo.endBreakEarly(session.id)
                        FocusFlowWidgetProvider.refreshAllWidgets(context)
                        sync(context)
                    }
                }
            }
            actions.addView(endBreakBtn)
        } else if (shortAvailable > 0 || longAvailable > 0) {
            val useLong = longAvailable > 0
            val mins = if (useLong) longMinutes else shortMinutes
            val breakBtn = Button(context).apply {
                text = if (useLong) "Long break (${mins}m)" else "Break (${mins}m)"
                isAllCaps = false
                setTextColor(Color.parseColor("#0E1116"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#7FD6C2"))
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    ioScope.launch {
                        val repo = FocusFlowRepository(
                            FocusFlowDatabase.getInstance(context).dao(),
                            SettingsDataStore(context)
                        )
                        repo.startBreak(session.id, isLong = useLong)
                        FocusFlowWidgetProvider.refreshAllWidgets(context)
                        sync(context)
                    }
                }
            }
            actions.addView(breakBtn)
        }

        val openBtn = Button(context).apply {
            text = "Open FocusFlow"
            isAllCaps = false
            setTextColor(Color.parseColor("#0E1116"))
            setTypeface(Typeface.DEFAULT_BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6EA8FF"))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener {
                expanded = false
                hide(context)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
                }
                runCatching { context.startActivity(openIntent) }
            }
        }
        actions.addView(openBtn)
        card.addView(actions)

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val widthPx = (context.resources.displayMetrics.widthPixels * 0.90f).toInt()
        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            typeFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = dp(110)
        }

        runCatching {
            wm.addView(card, params)
            currentView = card
            currentParams = params
        }
    }

    private fun removeExisting(wm: WindowManager) {
        currentView?.let { v ->
            v.animate().cancel()
            runCatching { wm.removeViewImmediate(v) }
        }
        currentView = null
        circleClockView = null
        cardClockView = null
        cardStatusView = null
    }
}
