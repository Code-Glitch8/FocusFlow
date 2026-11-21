package com.example.domain

import com.example.data.local.AllowedAppEntity
import com.example.data.local.BlockedAppEntity
import com.example.model.AppSettings
import com.example.model.BlockDecision
import com.example.model.ShortsMode

object BlockPolicy {

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    val ESSENTIAL_PACKAGES = setOf(
        "com.aistudio.focusflow.k7m2xp",
        "com.example",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.settings",
        "com.android.systemui",
        "com.android.contacts",
        "com.google.android.contacts"
    )

    fun evaluateApp(
        packageName: String,
        appLabel: String,
        sessionActive: Boolean,
        onBreak: Boolean,
        settings: AppSettings,
        blockedApps: List<BlockedAppEntity>,
        allowedApps: List<AllowedAppEntity>
    ): BlockDecision {
        if (!sessionActive) {
            return BlockDecision(
                shouldBlock = false,
                reason = "No active focus session.",
                packageName = packageName,
                appLabel = appLabel
            )
        }

        if (packageName in ESSENTIAL_PACKAGES) {
            return BlockDecision(
                shouldBlock = false,
                reason = "Essential system app is always allowed.",
                packageName = packageName,
                appLabel = appLabel
            )
        }

        val blockedEntry = blockedApps.find { it.packageName == packageName }
        val isAllowed = allowedApps.any { it.packageName == packageName }

        if (packageName == YOUTUBE_PACKAGE && settings.blockYoutubeApp) {
            val blockNow = !onBreak || settings.blockYoutubeAppDuringBreaks || (blockedEntry?.blockedDuringBreak == true)
            return BlockDecision(
                shouldBlock = blockNow,
                reason = if (blockNow) {
                    if (onBreak && (settings.blockYoutubeAppDuringBreaks || blockedEntry?.blockedDuringBreak == true)) {
                        "YouTube is locked even during breaks."
                    } else {
                        "YouTube app is blocked during focus sessions."
                    }
                } else {
                    "YouTube is temporarily allowed during your break."
                },
                packageName = packageName,
                appLabel = appLabel
            )
        }

        if (isAllowed) {
            return BlockDecision(
                shouldBlock = false,
                reason = "Listed in Allowed study apps.",
                packageName = packageName,
                appLabel = appLabel
            )
        }

        if (settings.blockAllExceptAllowed && !onBreak) {
            return BlockDecision(
                shouldBlock = true,
                reason = "Strict allowlist mode blocks non-essential apps during focus.",
                packageName = packageName,
                appLabel = appLabel
            )
        }

        if (blockedEntry != null) {
            val blockNow = !onBreak || blockedEntry.blockedDuringBreak
            return BlockDecision(
                shouldBlock = blockNow,
                reason = if (blockNow) {
                    if (onBreak) {
                        "$appLabel stays locked even during breaks."
                    } else {
                        "$appLabel is on your blocklist during focus."
                    }
                } else {
                    "$appLabel is allowed during your break."
                },
                packageName = packageName,
                appLabel = appLabel
            )
        }

        return BlockDecision(
            shouldBlock = false,
            reason = "Not on blocklist.",
            packageName = packageName,
            appLabel = appLabel
        )
    }

    fun shouldBlockYoutubeShorts(
        mode: ShortsMode,
        sessionActive: Boolean,
        onBreak: Boolean
    ): Boolean = when (mode) {
        ShortsMode.OFF -> false
        ShortsMode.ALWAYS -> true
        ShortsMode.DURING_SESSIONS -> sessionActive && !onBreak
    }
}
