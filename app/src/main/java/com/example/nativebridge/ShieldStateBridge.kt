package com.example.nativebridge

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.data.local.AllowedAppEntity
import com.example.data.local.BlockedAppEntity
import com.example.model.ActiveShieldAlert
import com.example.model.AppSettings
import com.example.model.PermissionHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShieldPolicySnapshot(
    val sessionActive: Boolean = false,
    val sessionLabel: String = "",
    val remainingLabel: String = "",
    val onBreak: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val blockedApps: List<BlockedAppEntity> = emptyList(),
    val allowedApps: List<AllowedAppEntity> = emptyList()
)

object ShieldStateBridge {
    private val _policy = MutableStateFlow(ShieldPolicySnapshot())
    val policy: StateFlow<ShieldPolicySnapshot> = _policy.asStateFlow()

    private val _activeAlert = MutableStateFlow<ActiveShieldAlert?>(null)
    val activeAlert: StateFlow<ActiveShieldAlert?> = _activeAlert.asStateFlow()

    private val _appInForeground = MutableStateFlow(false)
    val appInForeground: StateFlow<Boolean> = _appInForeground.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _appInForeground.value = inForeground
    }

    fun updatePolicy(snapshot: ShieldPolicySnapshot) {
        _policy.value = snapshot
    }

    fun triggerAlert(alert: ActiveShieldAlert) {
        _activeAlert.value = alert
    }

    fun clearAlert() {
        _activeAlert.value = null
    }

    fun checkPermissions(context: Context): PermissionHealth {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        val accessibilityEnabled = enabledServices.any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val usageMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        val usageGranted = usageMode == AppOpsManager.MODE_ALLOWED

        val overlayGranted = Settings.canDrawOverlays(context)

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val microphoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        return PermissionHealth(
            accessibilityEnabled = accessibilityEnabled,
            usageStatsGranted = usageGranted,
            overlayGranted = overlayGranted,
            notificationsGranted = notificationsGranted,
            microphoneGranted = microphoneGranted
        )
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
