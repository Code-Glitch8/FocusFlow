package com.example.nativebridge

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.data.FocusFlowRepository
import com.example.data.local.FocusFlowDatabase
import com.example.data.local.SettingsDataStore
import com.example.domain.BlockPolicy
import com.example.model.ActiveShieldAlert
import com.example.model.AppCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Displays a full-screen Focus Shield overlay immediately over any blocked app
 * using WindowManager TYPE_APPLICATION_OVERLAY when Display Over Other Apps is granted,
 * and falls back/reinforces with ShieldBlockActivity.
 */
object ShieldOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var tickRunnable: Runnable? = null

    fun showShield(context: Context, alert: ActiveShieldAlert) {
        val appContext = context.applicationContext
        ShieldStateBridge.triggerAlert(alert)

        if (Settings.canDrawOverlays(appContext)) {
            mainHandler.post {
                attachOverlay(appContext, alert)
            }
        }

        // Launch dedicated ShieldBlockActivity so the Focus Shield screen is shown
        // even if overlay permission was not granted yet.
        val shieldIntent = Intent(appContext, ShieldBlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(ShieldBlockActivity.EXTRA_PACKAGE, alert.packageName)
            putExtra(ShieldBlockActivity.EXTRA_LABEL, alert.appLabel)
            putExtra(ShieldBlockActivity.EXTRA_REASON, alert.reason)
            putExtra(ShieldBlockActivity.EXTRA_IS_SHORTS, alert.isYoutubeShorts)
            putExtra(ShieldBlockActivity.EXTRA_IS_YT, alert.isYoutubeApp)
        }
        runCatching { appContext.startActivity(shieldIntent) }
    }

    fun dismissOverlay(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            stopTick()
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            overlayView?.let { v ->
                runCatching { wm?.removeViewImmediate(v) }
            }
            overlayView = null
            timerTextView = null
        }
    }

    private fun attachOverlay(context: Context, alert: ActiveShieldAlert) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        overlayView?.let { existing ->
            runCatching { wm.removeViewImmediate(existing) }
            overlayView = null
        }
        stopTick()

        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#EE0E1116"))
            setPadding(dp(24), dp(36), dp(24), dp(36))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151A21"))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#FF8A80"))
            }
        }

        val badge = TextView(context).apply {
            text = if (alert.isYoutubeShorts) "SHORTS SHIELD ACTIVE" else "FOCUS SHIELD ACTIVE"
            textSize = 11f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#FF8A80"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A181A"))
                cornerRadius = dp(999).toFloat()
            }
        }
        card.addView(badge)

        val title = TextView(context).apply {
            text = "${alert.appLabel} is blocked"
            textSize = 22f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#F1F4F8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(6))
        }
        card.addView(title)

        val subtitle = TextView(context).apply {
            text = "You should be focusing right now.\n${alert.reason}"
            textSize = 14f
            setTextColor(Color.parseColor("#9AA4B2"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }
        card.addView(subtitle)

        val policy = ShieldStateBridge.policy.value
        val sessionBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2530"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }
        val sessLabel = TextView(context).apply {
            text = policy.sessionLabel.ifBlank { "Focus session in progress" }
            textSize = 13f
            setTextColor(Color.parseColor("#9AA4B2"))
        }
        val timerTv = TextView(context).apply {
            text = policy.remainingLabel.ifBlank { "Stay focused" }
            textSize = 28f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(
                if (policy.onBreak) Color.parseColor("#7FD6C2")
                else Color.parseColor("#6EA8FF")
            )
            setPadding(0, dp(4), 0, 0)
        }
        timerTextView = timerTv
        sessionBox.addView(sessLabel)
        sessionBox.addView(timerTv)
        card.addView(sessionBox)

        val returnBtn = Button(context).apply {
            text = "Return to FocusFlow"
            isAllCaps = false
            setTextColor(Color.parseColor("#0E1116"))
            setTypeface(Typeface.DEFAULT_BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6EA8FF"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { bottomMargin = dp(10) }
            setOnClickListener {
                ShieldStateBridge.clearAlert()
                dismissOverlay(context)
                val openApp = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
                }
                runCatching { context.startActivity(openApp) }
            }
        }
        card.addView(returnBtn)

        if (alert.isYoutubeShorts && !policy.settings.blockYoutubeApp) {
            val regularYtBtn = Button(context).apply {
                text = "Open regular YouTube"
                isAllCaps = false
                setTextColor(Color.parseColor("#7FD6C2"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E2530"))
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply { bottomMargin = dp(10) }
                setOnClickListener {
                    ShieldStateBridge.clearAlert()
                    dismissOverlay(context)
                    AppLauncherHelper.openYoutube(context, shorts = false)
                }
            }
            card.addView(regularYtBtn)
        }

        val closeBtn = Button(context).apply {
            text = "Close blocked app"
            isAllCaps = false
            setTextColor(Color.parseColor("#F1F4F8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2530"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener {
                ShieldStateBridge.clearAlert()
                dismissOverlay(context)
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(homeIntent) }
            }
        }
        card.addView(closeBtn)

        val unblockBtn = Button(context).apply {
            text = if (alert.isYoutubeShorts) "Allow Shorts for now" else "Allow ${alert.appLabel} for now"
            isAllCaps = false
            setTextColor(Color.parseColor("#9AA4B2"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                ioScope.launch {
                    val repo = FocusFlowRepository(
                        FocusFlowDatabase.getInstance(context).dao(),
                        SettingsDataStore(context)
                    )
                    if (alert.isYoutubeShorts) {
                        repo.updateSettings { it.copy(blockYoutubeShorts = com.example.model.ShortsMode.OFF) }
                    } else {
                        if (alert.packageName == BlockPolicy.YOUTUBE_PACKAGE) {
                            repo.updateSettings { it.copy(blockYoutubeApp = false, blockYoutubeAppDuringBreaks = false) }
                        }
                        repo.toggleBlockedApp(alert.packageName, alert.appLabel, AppCategory.CUSTOM, block = false)
                    }
                }
                ShieldStateBridge.clearAlert()
                dismissOverlay(context)
            }
        }
        card.addView(unblockBtn)

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            typeFlag,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        runCatching {
            wm.addView(root, params)
            overlayView = root
            startTick()
        }
    }

    private fun startTick() {
        val r = object : Runnable {
            override fun run() {
                val snap = ShieldStateBridge.policy.value
                timerTextView?.text = snap.remainingLabel.ifBlank { "Stay focused" }
                mainHandler.postDelayed(this, 1000L)
            }
        }
        tickRunnable = r
        mainHandler.postDelayed(r, 1000L)
    }

    private fun stopTick() {
        tickRunnable?.let { mainHandler.removeCallbacks(it) }
        tickRunnable = null
    }
}
