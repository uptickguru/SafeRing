package com.safering.android

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.safering.android.service.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

/**
 * SafeRing application entry point.
 *
 * SafeRing is an AI-powered scam call & SMS detection app designed for seniors.
 * Core philosophy: Protect without complexity. Zero PII off-device.
 *
 * This app has NO account creation, NO login, and NO personal data collection.
 * Phone numbers are SHA-256 hashed before any network transmission.
 * Audio analysis runs entirely on-device and is never recorded or transmitted.
 */
@HiltAndroidApp
class SafeRingApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeWorkManager()
    }

    private fun initializeWorkManager() {
        // WorkManager is initialized manually (not via AndroidX Startup)
        // to ensure Hilt injection is ready before any workers run.
        WorkManager.initialize(this, workManagerConfiguration)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(HiltWorkerFactory)
            .build()

    companion object {
        lateinit var instance: SafeRingApp
            private set

        val appContext: Context
            get() = instance.applicationContext
    }
}

/**
 * Placeholder HiltWorkerFactory — in production, use @HiltWorker with
 * Hilt's built-in HiltWorkerFactory from hilt-work library.
 *
 * For MVP, SyncWorker uses manual Hilt entry point injection.
 */
object HiltWorkerFactory : androidx.work.WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: androidx.work.WorkerParameters
    ): androidx.work.ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> {
                SyncWorker(
                    appContext,
                    workerParameters,
                    com.safering.android.data.local.AppDatabase.getInstance(appContext)
                        .scamNumberDao(),
                    com.safering.android.data.local.AppDatabase.getInstance(appContext)
                        .scamPrefixDao()
                )
            }
            else -> null
        }
    }
}
