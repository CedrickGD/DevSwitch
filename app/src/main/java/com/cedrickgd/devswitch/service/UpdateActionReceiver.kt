package com.cedrickgd.devswitch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cedrickgd.devswitch.data.DevSettingsController
import com.cedrickgd.devswitch.data.Prefs
import com.cedrickgd.devswitch.data.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Handles the "Update" action from the new-version notification. */
class UpdateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_NOW) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = UpdateManager.checkForUpdate(app) ?: return@launch
                UpdateNotifier.notifyProgress(app, info.versionName, 0)
                val file = UpdateManager.downloadApk(app, info) { progress ->
                    UpdateNotifier.notifyProgress(app, info.versionName, (progress * 100).toInt())
                }
                if (Prefs(app).skipPlayProtect.first()) {
                    DevSettingsController(app).setPlayProtectScan(false)
                }
                UpdateNotifier.cancel(app)
                UpdateManager.installApk(app, file)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_NOW = "com.cedrickgd.devswitch.UPDATE_NOW"
    }
}
