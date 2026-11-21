package com.example.nativebridge

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.domain.BlockPolicy
import com.example.domain.YouTubeUiClassifier
import com.example.model.ActiveShieldAlert
import com.example.model.UiNode
import com.example.model.YouTubeScreenKind

class FocusShieldAccessibilityService : AccessibilityService() {

    private var lastTriggerMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val snapshot = ShieldStateBridge.policy.value

        if (pkg == packageName || pkg in BlockPolicy.ESSENTIAL_PACKAGES) return

        // Evaluate YouTube Shorts & Home feed rules
        if (pkg == BlockPolicy.YOUTUBE_PACKAGE) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val tree = toUiNode(rootNode, depth = 0)
                val detection = YouTubeUiClassifier.classify(tree)
                val shortsBlocked = BlockPolicy.shouldBlockYoutubeShorts(
                    mode = snapshot.settings.blockYoutubeShorts,
                    sessionActive = snapshot.sessionActive,
                    onBreak = snapshot.onBreak
                )

                if (detection.kind == YouTubeScreenKind.SHORTS_PLAYER && shortsBlocked) {
                    redirectFromBlockedTarget(
                        pkg = pkg,
                        label = "YouTube Shorts",
                        reason = detection.reason,
                        isShorts = true,
                        isYoutubeApp = true
                    )
                    return
                }

                if (detection.kind == YouTubeScreenKind.HOME_FEED &&
                    snapshot.settings.skipYoutubeHomeFeed &&
                    shortsBlocked
                ) {
                    if (tryClickSubscriptionsTab(rootNode)) {
                        return
                    }
                }
            }
        }

        // Evaluate whole-app blocking rules
        val appLabel = snapshot.blockedApps.find { it.packageName == pkg }?.label
            ?: snapshot.allowedApps.find { it.packageName == pkg }?.label
            ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        val decision = BlockPolicy.evaluateApp(
            packageName = pkg,
            appLabel = appLabel,
            sessionActive = snapshot.sessionActive,
            onBreak = snapshot.onBreak,
            settings = snapshot.settings,
            blockedApps = snapshot.blockedApps,
            allowedApps = snapshot.allowedApps
        )

        if (decision.shouldBlock) {
            redirectFromBlockedTarget(
                pkg = pkg,
                label = appLabel,
                reason = decision.reason,
                isShorts = false,
                isYoutubeApp = pkg == BlockPolicy.YOUTUBE_PACKAGE
            )
        }
    }

    override fun onInterrupt() = Unit

    private fun redirectFromBlockedTarget(
        pkg: String,
        label: String,
        reason: String,
        isShorts: Boolean,
        isYoutubeApp: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < 1200L) return
        lastTriggerMs = now

        val alert = ActiveShieldAlert(
            packageName = pkg,
            appLabel = label,
            reason = reason,
            isYoutubeShorts = isShorts,
            isYoutubeApp = isYoutubeApp,
            triggeredAt = now
        )
        performGlobalAction(GLOBAL_ACTION_HOME)
        ShieldOverlayManager.showShield(this, alert)
    }

    private fun tryClickSubscriptionsTab(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 120) {
            val node = queue.removeFirst()
            visited++
            val label = (node.contentDescription ?: node.text)?.toString()?.trim()?.lowercase().orEmpty()
            if (label == "subscriptions" && node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return false
    }

    private fun toUiNode(node: AccessibilityNodeInfo, depth: Int): UiNode {
        if (depth >= 8) {
            return UiNode(
                viewId = node.viewIdResourceName,
                contentDescription = node.contentDescription?.toString(),
                text = node.text?.toString(),
                selected = node.isSelected
            )
        }
        val children = buildList {
            val limit = minOf(node.childCount, 24)
            for (i in 0 until limit) {
                node.getChild(i)?.let { add(toUiNode(it, depth + 1)) }
            }
        }
        return UiNode(
            viewId = node.viewIdResourceName,
            contentDescription = node.contentDescription?.toString(),
            text = node.text?.toString(),
            selected = node.isSelected,
            children = children
        )
    }

    companion object {
        const val EXTRA_SHIELD_TRIGGERED = "extra_shield_triggered"
    }
}
