package online.db1k.safering.android.service

import android.content.Context
import androidx.work.*
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.Logger
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
            Logger.info("Starting scam data sync...", Logger.Category.BACKGROUND)

            // Sync prefixes for offline blocking
            val prefixes = repository.fetchPrefixes()
            Logger.info("Fetched ${prefixes.size} scam prefixes", Logger.Category.BACKGROUND)

            // Clean up old call logs
            val retentionMs = AppConfig.LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L
            val cutoff = System.currentTimeMillis() - retentionMs
            AppDatabase.getInstance(applicationContext).callLogDao()
                .deleteOlderThan(cutoff)
            AppDatabase.getInstance(applicationContext).smsLogDao()
                .deleteOlderThan(cutoff)

            Logger.info("Sync completed successfully", Logger.Category.BACKGROUND)
            Result.success()
        } catch (e: Exception) {
            Logger.error("Sync failed: ${e.message}", Logger.Category.BACKGROUND, e)
            Result.retry()
        }
    }

    companion object {
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
