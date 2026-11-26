package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.UpperAirApplication
import java.util.concurrent.TimeUnit

class SoundingSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? UpperAirApplication ?: return Result.failure()
        val outcomes = app.container.repository.syncAllPinnedStations(sourceTag = "DAILY_CACHE")
        val anySuccess = outcomes.any { it.observedSuccess || it.forecastSuccess || it.rrfsSuccess }
        return if (anySuccess) Result.success() else Result.retry()
    }
}

class SoundingSyncScheduler(private val context: Context) {
    companion object {
        private const val PERIODIC_WORK_NAME = "upperair_periodic_sounding_sync"
        private const val DAILY_CACHE_WORK_NAME = "upperair_guaranteed_daily_offline_cache"
        private const val IMMEDIATE_WORK_NAME = "upperair_immediate_sounding_sync"
    }

    fun configurePeriodicSync(
        dailyOfflineEnabled: Boolean,
        autoSyncEnabled: Boolean,
        intervalHours: Int,
        wifiOnly: Boolean
    ) {
        val wm = WorkManager.getInstance(context)
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        // 1. Frequent synoptic auto-sync (6h / 12h / 24h)
        if (autoSyncEnabled) {
            val safeHours = intervalHours.coerceIn(6, 24).toLong()
            val request = PeriodicWorkRequestBuilder<SoundingSyncWorker>(safeHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        // 2. Guaranteed once-per-day offline caching job (at least every 24h)
        if (dailyOfflineEnabled) {
            val dailyConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val dailyRequest = PeriodicWorkRequestBuilder<SoundingSyncWorker>(
                24,
                TimeUnit.HOURS,
                4,
                TimeUnit.HOURS
            )
                .setConstraints(dailyConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                DAILY_CACHE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyRequest
            )
        } else {
            wm.cancelUniqueWork(DAILY_CACHE_WORK_NAME)
        }
    }

    fun enqueueImmediateBackgroundSync(wifiOnly: Boolean = false) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<SoundingSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
