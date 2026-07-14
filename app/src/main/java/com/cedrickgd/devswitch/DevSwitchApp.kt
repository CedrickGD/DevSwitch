package com.cedrickgd.devswitch

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class DevSwitchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                "Background monitor",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Quiet, persistent status while DevSwitch watches settings"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Setting change alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Fires when a watched developer setting changes outside DevSwitch"
            }
        )
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERTS = "alerts"
    }
}
