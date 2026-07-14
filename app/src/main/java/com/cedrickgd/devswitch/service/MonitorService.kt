package com.cedrickgd.devswitch.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cedrickgd.devswitch.DevSwitchApp
import com.cedrickgd.devswitch.MainActivity
import com.cedrickgd.devswitch.R
import com.cedrickgd.devswitch.data.DevSetting
import com.cedrickgd.devswitch.data.DevSettingsController
import com.cedrickgd.devswitch.data.Prefs
import com.cedrickgd.devswitch.data.SelfChangeTracker
import com.cedrickgd.devswitch.data.SettingsRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: DevSettingsController
    private lateinit var prefs: Prefs
    private val observers = mutableMapOf<String, ContentObserver>()
    private val lastKnown = linkedMapOf<String, Boolean>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = DevSettingsController(this)
        prefs = Prefs(applicationContext)
        startInForeground()
        scope.launch {
            prefs.watched.collect { keys -> syncObservers(keys) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        observers.values.forEach { contentResolver.unregisterContentObserver(it) }
        observers.clear()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildStatusNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, STATUS_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(STATUS_ID, notification)
        }
    }

    private fun syncObservers(keys: Set<String>) {
        if (keys.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        (observers.keys - keys).toList().forEach { key ->
            observers.remove(key)?.let { contentResolver.unregisterContentObserver(it) }
            lastKnown.remove(key)
        }
        (keys - observers.keys).forEach { key ->
            val setting = SettingsRegistry.byKey(key) ?: return@forEach
            if (!controller.isSupported(setting)) return@forEach
            lastKnown[key] = controller.isOn(setting)
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    handleChange(setting)
                }
            }
            contentResolver.registerContentObserver(controller.uriFor(setting), false, observer)
            observers[key] = observer
        }
        updateStatusNotification()
    }

    private fun handleChange(setting: DevSetting) {
        val on = controller.isOn(setting)
        val previous = lastKnown[setting.key]
        if (previous == on) return
        lastKnown[setting.key] = on
        updateStatusNotification()
        if (SelfChangeTracker.wasRecentSelfChange(setting.key)) return
        sendAlert(setting, on)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun sendAlert(setting: DevSetting, on: Boolean) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(this, DevSwitchApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_toggle)
            .setContentTitle("${setting.title} turned ${if (on) "ON" else "OFF"}")
            .setContentText("Changed outside DevSwitch just now.")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
        if (!on) {
            val revert = PendingIntent.getBroadcast(
                this,
                setting.key.hashCode(),
                ToggleReceiver.intent(this, setting.key, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_stat_toggle, "Turn back on", revert)
        }
        try {
            manager.notify(setting.key.hashCode(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun buildStatusNotification(): Notification {
        val parts = lastKnown.entries.mapNotNull { (key, on) ->
            SettingsRegistry.byKey(key)?.let { "${it.title}: ${if (on) "On" else "Off"}" }
        }
        val title = when {
            parts.isEmpty() -> "Monitoring developer settings"
            parts.size == 1 -> "Watching 1 setting"
            else -> "Watching ${parts.size} settings"
        }
        return NotificationCompat.Builder(this, DevSwitchApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_toggle)
            .setContentTitle(title)
            .setContentText(parts.joinToString(" · ").ifEmpty { "No settings selected" })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(parts.joinToString("\n").ifEmpty { "No settings selected" })
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun updateStatusNotification() {
        try {
            NotificationManagerCompat.from(this).notify(STATUS_ID, buildStatusNotification())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val STATUS_ID = 1

        fun ensureRunning(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitorService::class.java),
                )
            }
        }
    }
}
