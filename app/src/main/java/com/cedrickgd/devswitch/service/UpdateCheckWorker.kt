package com.cedrickgd.devswitch.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cedrickgd.devswitch.data.UpdateManager
import java.util.concurrent.TimeUnit

/** Periodically checks GitHub for a new release and posts a notification. */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = runCatching { UpdateManager.checkForUpdate(applicationContext) }
            .getOrNull() ?: return Result.success()
        UpdateNotifier.notifyAvailable(applicationContext, info)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "devswitch-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
