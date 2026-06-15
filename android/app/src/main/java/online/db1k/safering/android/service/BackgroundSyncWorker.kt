package online.db1k.safering.android.service

import android.content.Context
import android.util.Log
import androidx.work.*
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.util.AppConfig
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based background worker for syncing scam data.
 * Mirrors the iOS BGTaskScheduler functionality.
 */
class BackgroundSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = ScamRepository(
        api = SafeRingApi.create(),
        db = AppDatabase.getInstance(context)
    )

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting scam data sync...")

            // Sync prefixes for offline blocking
            val prefixes = repository.fetchPrefixes()
            Log.d(TAG, "Fetched ${prefixes.size} scam prefixes")

            // Clean up old call logs
            val retentionMs = AppConfig.LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L
            val cutoff = System.currentTimeMillis() - retentionMs
            AppDatabase.getInstance(applicationContext).callLogDao()
                .deleteOlderThan(cutoff)
            AppDatabase.getInstance(applicationContext).smsLogDao()
                .deleteOlderThan(cutoff)

            Log.d(TAG, "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        private const val WORK_NAME = "scam_data_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                AppConfig.SYNC_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
