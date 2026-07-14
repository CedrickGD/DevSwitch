package com.cedrickgd.devswitch.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cedrickgd.devswitch.DevSwitchApp
import com.cedrickgd.devswitch.MainActivity
import com.cedrickgd.devswitch.R
import com.cedrickgd.devswitch.data.UpdateInfo

/** Posts the "new version available" notification with a one-tap Update action. */
object UpdateNotifier {

    private const val ID = 42

    fun notifyAvailable(context: Context, info: UpdateInfo) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val updateNow = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, UpdateActionReceiver::class.java)
                .setAction(UpdateActionReceiver.ACTION_UPDATE_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, DevSwitchApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_toggle)
            .setContentTitle("DevSwitch ${info.versionName} available")
            .setContentText("Tap Update to download and install")
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_toggle, "Update", updateNow)
            .build()
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun notifyProgress(context: Context, versionName: String, percent: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, DevSwitchApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_toggle)
            .setContentTitle("Downloading DevSwitch $versionName")
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }
}
