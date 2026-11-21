package com.example.nativebridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.data.FocusFlowRepository
import com.example.data.local.FocusFlowDatabase
import com.example.data.local.SettingsDataStore
import com.example.domain.BlockPolicy
import com.example.domain.SessionClock
import com.example.model.ActiveShieldAlert
import com.example.model.AppCategory
import com.example.model.ShortsMode
import com.example.ui.theme.FocusFlowTheme
import com.example.ui.theme.MonoMetricLarge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShieldBlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialAlert = alertFromIntent(intent)

        setContent {
            val liveAlert by ShieldStateBridge.activeAlert.collectAsStateWithLifecycle()
            val policy by ShieldStateBridge.policy.collectAsStateWithLifecycle()
            val alert = liveAlert ?: initialAlert

            var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var liveClock by remember { mutableStateOf(policy.remainingLabel) }
            var sessionTitle by remember { mutableStateOf(policy.sessionLabel) }

            LaunchedEffect(Unit) {
                val dao = FocusFlowDatabase.getInstance(applicationContext).dao()
                while (true) {
                    nowMs = System.currentTimeMillis()
                    val active = dao.getSessionsOnce().firstOrNull { it.status == com.example.model.SessionStatus.ACTIVE }
                    if (active != null) {
                        sessionTitle = active.label
                        val snap = SessionClock.computeSnapshot(active, nowMs)
                        liveClock = when {
                            snap.isOnBreak -> SessionClock.formatClock(snap.breakRemainingMs)
                            snap.remainingMs != null -> SessionClock.formatClock(snap.remainingMs)
                            else -> SessionClock.formatClock(snap.focusElapsedMs)
                        }
                    }
                    delay(1000L)
                }
            }

            FocusFlowTheme(themeMode = policy.settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (alert.isYoutubeShorts) "SHORTS SHIELD ACTIVE" else "FOCUS SHIELD ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "${alert.appLabel} is blocked",
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "You should be focusing right now.\n${alert.reason}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(18.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = sessionTitle.ifBlank { "Active study session" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = liveClock.ifBlank { "Stay focused" },
                                            style = MonoMetricLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (policy.onBreak) MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        ShieldStateBridge.clearAlert()
                                        ShieldOverlayManager.dismissOverlay(this@ShieldBlockActivity)
                                        val openApp = Intent(this@ShieldBlockActivity, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
                                        }
                                        startActivity(openApp)
                                        finish()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("shield_activity_return"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Return to FocusFlow")
                                }

                                if (alert.isYoutubeShorts && !policy.settings.blockYoutubeApp) {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            ShieldStateBridge.clearAlert()
                                            ShieldOverlayManager.dismissOverlay(this@ShieldBlockActivity)
                                            AppLauncherHelper.openYoutube(this@ShieldBlockActivity, shorts = false)
                                            finish()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Open regular YouTube")
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        ShieldStateBridge.clearAlert()
                                        ShieldOverlayManager.dismissOverlay(this@ShieldBlockActivity)
                                        val home = Intent(Intent.ACTION_MAIN).apply {
                                            addCategory(Intent.CATEGORY_HOME)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(home)
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Close blocked app")
                                }

                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        lifecycleScope.launch {
                                            val repo = FocusFlowRepository(
                                                FocusFlowDatabase.getInstance(applicationContext).dao(),
                                                SettingsDataStore(applicationContext)
                                            )
                                            if (alert.isYoutubeShorts) {
                                                repo.updateSettings { it.copy(blockYoutubeShorts = ShortsMode.OFF) }
                                            } else {
                                                if (alert.packageName == BlockPolicy.YOUTUBE_PACKAGE) {
                                                    repo.updateSettings { it.copy(blockYoutubeApp = false, blockYoutubeAppDuringBreaks = false) }
                                                }
                                                repo.toggleBlockedApp(alert.packageName, alert.appLabel, AppCategory.CUSTOM, block = false)
                                            }
                                            ShieldStateBridge.clearAlert()
                                            ShieldOverlayManager.dismissOverlay(this@ShieldBlockActivity)
                                            finish()
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                ) {
                                    Text(if (alert.isYoutubeShorts) "Allow Shorts for now" else "Allow ${alert.appLabel} for now")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun alertFromIntent(intent: Intent?): ActiveShieldAlert {
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Distracting app" }
        val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "Blocked during active focus session." }
        val isShorts = intent?.getBooleanExtra(EXTRA_IS_SHORTS, false) ?: false
        val isYt = intent?.getBooleanExtra(EXTRA_IS_YT, false) ?: false
        return ActiveShieldAlert(
            packageName = pkg,
            appLabel = label,
            reason = reason,
            isYoutubeShorts = isShorts,
            isYoutubeApp = isYt
        )
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_shield_pkg"
        const val EXTRA_LABEL = "extra_shield_label"
        const val EXTRA_REASON = "extra_shield_reason"
        const val EXTRA_IS_SHORTS = "extra_shield_shorts"
        const val EXTRA_IS_YT = "extra_shield_yt"
    }
}
