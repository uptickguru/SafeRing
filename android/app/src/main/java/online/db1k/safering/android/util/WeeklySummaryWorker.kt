package online.db1k.safering.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.*
import online.db1k.safering.android.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * Weekly summary notification worker.
 * Scheduled to run every Monday at 10 AM to show scam protection stats.
 * Improves retention by proving the app is working even when zero scams are detected.
 */
class WeeklySummaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.getInstance(context)

        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        val blockedCalls = db.callLogDao().getRecentCount(weekAgo)
        val filteredSms = db.smsLogDao().getRecentCount(weekAgo)
        val blockedCount = db.callLogDao().getBlockedCount(weekAgo)

        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📊 SafeRing Weekly Summary")
            .setContentText("$blockedCalls calls screened · $filteredSms SMS analyzed · $blockedCount blocked")
            .setAutoCancel(true)
            .setStyle(android.app.Notification.BigTextStyle()
                .bigText("This week:\n" +
                    "• $blockedCalls calls screened\n" +
                    "• $filteredSms SMS messages analyzed\n" +
                    "• $blockedCount calls blocked\n" +
                    "SafeRing is keeping you protected."))
            .build()

        notificationManager.notify(1001, notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "weekly_summary"
        private const val WORK_NAME = "weekly_summary"

        private fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Weekly Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Weekly protection summary from SafeRing"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val dailyTrigger = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(
                7, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.DAYS) // Start after first day
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    dailyTrigger
                )
        }
    }
}
