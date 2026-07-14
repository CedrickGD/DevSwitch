package com.cedrickgd.devswitch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.cedrickgd.devswitch.data.DevSettingsController
import com.cedrickgd.devswitch.data.Prefs
import com.cedrickgd.devswitch.data.SettingsRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Handles "Turn back on" / "Turn off" actions from notifications. */
class ToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val on = intent.getBooleanExtra(EXTRA_ON, true)
        val setting = SettingsRegistry.byKey(key) ?: return
        val result = DevSettingsController(context).setEnabled(setting, on)
        NotificationManagerCompat.from(context).cancel(key.hashCode())
        if (result.isFailure) {
            Toast.makeText(context, "Couldn't change ${setting.title}", Toast.LENGTH_SHORT).show()
            return
        }
        val persistent = runBlocking {
            Prefs(context.applicationContext).persistentStateNotifications.first()
        }
        StateNotifier.onAppToggle(context, setting, on, persistent)
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_ON = "on"

        fun intent(context: Context, key: String, on: Boolean): Intent =
            Intent(context, ToggleReceiver::class.java)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_ON, on)
    }
}
