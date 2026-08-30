package online.db1k.safering.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.db1k.safering.android.MainActivity
import online.db1k.safering.android.R
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.local.models.SmsLogEntity
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.remote.models.EventRequest
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.HmacHashUtils
import online.db1k.safering.android.util.Logger

/**
 * SMS BroadcastReceiver — intercepts incoming SMS messages for scam detection.
 *
 * # Architecture
 * 1. Receives SMS_RECEIVED_ACTION broadcast
 * 2. Extracts sender number and message body
 * 3. HMAC-hashes the sender number (zero PII)
 * 4. Classifies message using SmsClassificationService
 * 5. Takes action based on confidence:
 *    - SCAM (≥0.85): Abort broadcast (blocks from inbox) + notify user
 *    - SPAM (0.65-0.85): Allow delivery + show warning notification
 *    - SUSPICIOUS (0.40-0.65): Allow delivery + log
 *    - SAFE (<0.40): Allow delivery silently
 * 6. Reports event to backend (fire-and-forget)
 * 7. Stores in Room database for history
 *
 * # Security
 * - Sender number is HMAC-SHA256 hashed immediately
 * - Message body processed on-device only
 * - Raw text never leaves the device
 * - Only hash prefix sent to backend
 *
 * # Blocking Mechanism
 * - Uses abortBroadcast() to prevent delivery to the default SMS app
 * - Only works if SafeRing has RECEIVE_SMS permission and higher priority
 * - User sees a notification instead of the message in their inbox
 *
 * # Limitations
 * - Cannot block if user hasn't granted RECEIVE_SMS permission
 * - Google Play requires justification for SMS permissions
 * - abortBroadcast() only works for ordered broadcasts
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val classifier = SmsClassificationService()

    companion object {
        private const val TAG = "SmsReceiver"
        private const val NOTIFICATION_CHANNEL_ID = "safering_sms_alerts"
        private const val BLOCKED_NOTIFICATION_ID_BASE = 2000
        private const val WARNING_NOTIFICATION_ID_BASE = 3000
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Logger.info("SMS received, processing...", Logger.Category.SMS)

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Logger.debug("No messages in intent", Logger.Category.SMS)
            return
        }

        // Group by sender (multi-part SMS)
        val groupedMessages = messages.groupBy { it.originatingAddress ?: "unknown" }

        for ((sender, parts) in groupedMessages) {
            val fullBody = parts.joinToString("") { it.messageBody ?: "" }
            if (fullBody.isBlank()) continue

            processMessage(context, sender, fullBody)
        }
    }

    private fun processMessage(context: Context, sender: String, body: String) {
        // Hash sender immediately
        val normalizedNumber = normalizePhoneNumber(sender)
        val senderHash = HmacHashUtils.hmacSHA256(normalizedNumber, key = AppConfig.HMAC_KEY)
        val hashPrefix = senderHash.take(8)

        Logger.info("Processing SMS from hash: $hashPrefix...", Logger.Category.SMS)

        // Classify
        val result = classifier.classify(body)

        // Determine action
        val action = when (result.classification) {
            SmsClassificationService.Classification.SCAM -> "block"
            SmsClassificationService.Classification.SPAM -> "warn"
            SmsClassificationService.Classification.SUSPICIOUS -> "monitor"
            SmsClassificationService.Classification.SAFE -> "monitor"
        }

        // Store in database
        val db = AppDatabase.getInstance(context)
        scope.launch {
            try {
                db.smsLogDao().insert(
                    SmsLogEntity(
                        numberHash = senderHash,
                        messageBody = body,  // Store locally for user review
                        riskScore = result.riskScore,
                        riskLabel = result.classification.name.lowercase(),
                        scamType = result.scamType,
                        timestamp = System.currentTimeMillis(),
                        wasBlocked = result.classification == SmsClassificationService.Classification.SCAM
                    )
                )
                Logger.info("SMS logged: $hashPrefix score=${result.riskScore}", Logger.Category.SMS)
            } catch (e: Exception) {
                Logger.debug("Failed to store SMS log: ${e.message}", Logger.Category.SMS)
            }
        }

        // Report event to backend (fire-and-forget)
        val api = SafeRingApi.create()
        scope.launch {
            try {
                api.postEvent(
                    EventRequest(
                        platform = "android",
                        action = action,
                        event_type = "sms",
                        hash_prefix = hashPrefix,
                        risk_score = result.riskScore,
                        scam_type = result.scamType ?: "",
                        source = "keyword"
                    )
                )
            } catch (e: Exception) {
                Logger.debug("Event send failed (non-critical): ${e.message}", Logger.Category.NETWORK)
            }
        }

        // Take action based on classification
        when (result.classification) {
            SmsClassificationService.Classification.SCAM -> {
                // Block: abort broadcast so message doesn't reach inbox
                if (isOrderedBroadcast) {
                    resultCode = android.app.Activity.RESULT_CANCELED
                    abortBroadcast()
                    Logger.info("SMS BLOCKED: $hashPrefix score=${result.riskScore} type=${result.scamType}", Logger.Category.SMS)
                }

                // Show notification to user
                showBlockedNotification(context, hashPrefix, result.scamType, body)
            }

            SmsClassificationService.Classification.SPAM -> {
                // Warn: allow delivery but show warning notification
                Logger.info("SMS WARNING: $hashPrefix score=${result.riskScore} type=${result.scamType}", Logger.Category.SMS)
                showWarningNotification(context, hashPrefix, result.scamType, body)
            }

            SmsClassificationService.Classification.SUSPICIOUS -> {
                // Monitor: allow delivery, log only
                Logger.info("SMS SUSPICIOUS: $hashPrefix score=${result.riskScore}", Logger.Category.SMS)
            }

            SmsClassificationService.Classification.SAFE -> {
                // Safe: allow delivery silently
                Logger.debug("SMS SAFE: $hashPrefix score=${result.riskScore}", Logger.Category.SMS)
            }
        }
    }

    // MARK: - Notifications

    private fun showBlockedNotification(context: Context, hashPrefix: String, scamType: String?, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🚫 Scam Blocked"
        val text = buildString {
            append(scamType ?: "Scam")
            append(" message blocked from hash ")
            append(hashPrefix)
            append("\n\n")
            append(body.take(100))
            if (body.length > 100) append("...")
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = BLOCKED_NOTIFICATION_ID_BASE + System.currentTimeMillis().toInt() % 1000
        notificationManager.notify(notificationId, notification)
    }

    private fun showWarningNotification(context: Context, hashPrefix: String, scamType: String?, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Suspicious Message"
        val text = buildString {
            append("Possible ")
            append(scamType ?: "scam")
            append(" detected (hash ")
            append(hashPrefix)
            append(")\n\n")
            append(body.take(100))
            if (body.length > 100) append("...")
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = WARNING_NOTIFICATION_ID_BASE + System.currentTimeMillis().toInt() % 1000
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SMS Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for blocked and suspicious SMS messages"
            }
            manager.createNotificationChannel(channel)
        }
    }

    // MARK: - Helpers

    private fun normalizePhoneNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return when {
            digits.startsWith("1") -> "+$digits"
            digits.length == 10 -> "+1$digits"
            else -> "+$digits"
        }
    }
}
