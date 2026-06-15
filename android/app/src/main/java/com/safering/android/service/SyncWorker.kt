package com.safering.android.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.safering.android.data.local.dao.ScamNumberDao
import com.safering.android.data.local.dao.ScamPrefixDao
import com.safering.android.data.local.entity.ScamPrefixEntity
import com.safering.android.data.remote.ApiService
import com.safering.android.util.RetrofitClient
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker for background scam data sync.
 *
 * Runs approximately every 6 hours (with flex interval) to:
 * 1. Fetch updated scam prefixes from the server
 * 2. (Future) Download latest ML model weights
 * 3. Clear stale cached data
 *
 * The sync is designed to be efficient and battery-friendly:
 * - Requires network connectivity (unmetered preferred)
 * - Small payloads (prefix list is typically <10KB)
 * - Respects Doze mode and app standby
 *
 * Privacy: No PII is transmitted during sync. Only anonymous queries
 * for public scam data are performed.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val scamNumberDao: ScamNumberDao? = null,
    private val scamPrefixDao: ScamPrefixDao? = null
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "safering_periodic_sync"
        private const val INTERVAL_HOURS = 6L
        private const val FLEX_MINUTES = 30L

        /**
         * Schedule the periodic sync work.
         * Safe to call multiple times — uses KEEP policy.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS,
                FLEX_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("safering_sync")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "Periodic sync scheduled every $INTERVAL_HOURS hours")
        }

        /**
         * Cancel the periodic sync.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic sync cancelled")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting background data sync")

        return try {
            val numberDao = scamNumberDao
                ?: com.safering.android.data.local.AppDatabase.getInstance(applicationContext)
                    .scamNumberDao()

            val prefixDao = scamPrefixDao
                ?: com.safering.android.data.local.AppDatabase.getInstance(applicationContext)
                    .scamPrefixDao()

            val apiService = RetrofitClient.apiService

            var syncSuccess = true

            // 1. Sync scam prefixes
            try {
                val response = apiService.getPrefixes()
                if (response.isSuccessful && response.body() != null) {
                    val prefixes = response.body()!!.map { prefix ->
                        ScamPrefixEntity(
                            prefix = prefix.prefix,
                            riskWeight = prefix.riskWeight,
                            description = prefix.description,
                            region = prefix.region,
                            associatedTypes = prefix.associatedTypes,
                            reportCount = prefix.reportCount,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }

                    if (prefixes.isNotEmpty()) {
                        prefixDao.clearAll()
                        prefixDao.insertAll(prefixes)
                        Log.d(TAG, "Synced ${prefixes.size} scam prefixes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync prefixes", e)
                syncSuccess = false
            }

            // 2. Clean up stale data
            val staleCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            val deletedNumbers = numberDao.deleteOlderThan(staleCutoff)
            val deletedPrefixes = prefixDao.deleteOlderThan(staleCutoff)
            Log.d(TAG, "Cleaned up $deletedNumbers stale numbers and $deletedPrefixes prefixes")

            // 3. Check for model updates (future: download new TFLite models)
            try {
                val modelResponse = apiService.getLatestModelInfo("number")
                if (modelResponse.isSuccessful && modelResponse.body() != null) {
                    val modelInfo = modelResponse.body()!!
                    Log.d(TAG, "Latest number model version: ${modelInfo.version}")
                    // In production, compare with local model version and download if newer
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check model updates", e)
            }

            if (syncSuccess) {
                Log.d(TAG, "Data sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Data sync completed with partial errors")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Data sync failed", e)
            Result.retry()
        }
    }
}
