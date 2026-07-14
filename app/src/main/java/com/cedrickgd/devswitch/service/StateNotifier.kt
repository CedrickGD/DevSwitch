package com.cedrickgd.devswitch.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cedrickgd.devswitch.DevSwitchApp
import com.cedrickgd.devswitch.MainActivity
import com.cedrickgd.devswitch.R
import com.cedrickgd.devswitch.data.DevSetting
import com.cedrickgd.devswitch.data.DevSettingsController
import com.cedrickgd.devswitch.data.SettingsRegistry

/**
 * Status notifications for toggles made through DevSwitch itself:
 * turning a setting ON posts a persistent (configurable) notification that
 * stays while the setting is on; turning it OFF swaps it for a short-lived
 * confirmation that dismisses itself.
 */
object StateNotifier {

    private const val TAG_STATE = "state"
    private const val OFF_TIMEOUT_MS = 5_000L

    fun onAppToggle(context: Context, setting: DevSetting, on: Boolean, persistent: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val id = setting.key.hashCode()
        if (on) {
            val turnOff = PendingIntent.getBroadcast(
                context,
                id xor 0x0FF0,
                ToggleReceiver.intent(context, setting.key, false),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, DevSwitchApp.CHANNEL_STATE)
                .setSmallIcon(R.drawable.ic_stat_toggle)
                .setContentTitle("${setting.title} is ON")
                .setContentText("Enabled via DevSwitch")
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(persistent)
                .setSilent(true)
                .setContentIntent(contentIntent(context))
                .addAction(R.drawable.ic_stat_toggle, "Turn off", turnOff)
                .build()
            try {
                manager.notify(TAG_STATE, id, notification)
            } catch (_: SecurityException) {
            }
        } else {
            manager.cancel(TAG_STATE, id)
            val notification = NotificationCompat.Builder(context, DevSwitchApp.CHANNEL_STATE)
                .setSmallIcon(R.drawable.ic_stat_toggle)
                .setContentTitle("${setting.title} turned OFF")
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setSilent(true)
                .setTimeoutAfter(OFF_TIMEOUT_MS)
                .setContentIntent(contentIntent(context))
                .build()
            try {
                manager.notify(TAG_STATE, id, notification)
            } catch (_: SecurityException) {
            }
        }
    }

    /** Removes ON-state notifications whose setting is no longer on. */
    fun sync(context: Context, controller: DevSettingsController) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val active = try {
            manager.activeNotifications
        } catch (_: Exception) {
            return
        }
        val compat = NotificationManagerCompat.from(context)
        for (sbn in active) {
            if (sbn.tag != TAG_STATE || !sbn.isOngoing) continue
            val setting = SettingsRegistry.all.firstOrNull { it.key.hashCode() == sbn.id }
                ?: continue
            if (!controller.isOn(setting)) {
                compat.cancel(TAG_STATE, sbn.id)
            }
        }
    }

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
