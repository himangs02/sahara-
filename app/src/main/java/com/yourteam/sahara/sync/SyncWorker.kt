package com.yourteam.sahara.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yourteam.sahara.SaharaApplication

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SaharaApplication ?: return Result.failure()
        val syncManager = app.syncManager

        val success = syncManager.processPendingSyncQueue()

        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
