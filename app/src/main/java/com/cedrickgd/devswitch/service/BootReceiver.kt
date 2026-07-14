package com.cedrickgd.devswitch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cedrickgd.devswitch.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val watched = runBlocking { Prefs(context.applicationContext).watched.first() }
        if (watched.isNotEmpty()) {
            MonitorService.ensureRunning(context)
        }
    }
}
