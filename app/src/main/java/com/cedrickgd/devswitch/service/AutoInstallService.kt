package com.cedrickgd.devswitch.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cedrickgd.devswitch.data.AutoInstall

/**
 * When "seamless mode" is on and an update is installing, this taps the
 * positive button on the system installer / Play Protect dialogs so the
 * update goes through without the user touching anything. It only reacts
 * inside [AutoInstall]'s armed window and only to installer/Play packages.
 */
class AutoInstallService : AccessibilityService() {

    private var lastClickAt = 0L

    private val installerPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.google.android.gms", // Play Protect scan dialog
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AutoInstall.isArmed()) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in installerPackages) return
        val root = rootInActiveWindow ?: return
        try {
            clickPositive(root)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}

    private fun clickPositive(root: AccessibilityNodeInfo) {
        // Debounce so a single dialog isn't hammered as it animates.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickAt < 700) return

        val candidate = findPositiveButton(root) ?: return
        var node: AccessibilityNodeInfo? = candidate
        while (node != null && !node.isClickable) {
            node = node.parent
        }
        if (node != null && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            lastClickAt = now
        }
    }

    private fun findPositiveButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val label = node.text?.toString()?.trim()?.lowercase()
        if (!label.isNullOrEmpty()) {
            if (label in AutoInstall.negativeLabels) return null
            if (label in AutoInstall.positiveLabels) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findPositiveButton(child)
            if (found != null) return found
        }
        return null
    }
}
