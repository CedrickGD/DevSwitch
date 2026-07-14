package com.cedrickgd.devswitch.data

import android.os.SystemClock

/**
 * Coordinates the optional "seamless mode": while an update we started is
 * installing, [AutoInstallService] auto-taps the system installer / Play
 * Protect confirmation buttons so the user never sees them. It only acts
 * inside the short armed window to avoid touching unrelated dialogs.
 */
object AutoInstall {

    @Volatile
    private var armedUntil = 0L

    fun arm(windowMs: Long = 180_000L) {
        armedUntil = SystemClock.elapsedRealtime() + windowMs
    }

    fun disarm() {
        armedUntil = 0L
    }

    fun isArmed(): Boolean = SystemClock.elapsedRealtime() < armedUntil

    /** Button labels that advance an install (English + German). */
    val positiveLabels = setOf(
        "update", "aktualisieren",
        "install", "installieren",
        "app scannen", "scan app", "scan", "scannen",
        "continue", "weiter", "fortfahren",
        "allow", "erlauben", "zulassen",
        "ok",
    )

    /** Labels we must never click. */
    val negativeLabels = setOf(
        "cancel", "abbrechen",
        "don't install", "nicht installieren",
        "don't scan", "nicht scannen", "nicht senden",
        "deny", "ablehnen",
    )
}
