package com.safering.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.safering.android.data.local.AppDatabase
import com.safering.android.data.repository.ScamRepository
import com.safering.android.domain.ml.SmsClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for incoming SMS messages.
 *
 * Intercepts incoming SMS and runs the message body through the on-device
 * SMS classifier. If a scam is detected, the user receives an alert.
 *
 * Privacy: ALL SMS classification happens on-device. The message body
 * is NEVER transmitted over the network. Only the SHA-256 hash of the
 * sender number is stored locally.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // Combine multipart SMS messages
        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        val senderNumber = messages.first().originatingAddress ?: return

        Log.d(TAG, "SMS received from: ${maskNumber(senderNumber)}")

        scope.launch {
            try {
                val repository = ScamRepository(
                    com.safering.android.util.RetrofitClient.apiService,
                    AppDatabase.getInstance(context).scamNumberDao(),
                    AppDatabase.getInstance(context).scamPrefixDao(),
                    AppDatabase.getInstance(context).callLogDao(),
                    AppDatabase.getInstance(context).smsLogDao()
                )

                val classifier = SmsClassifier(context)
                classifier.loadModel()

                val result = classifier.classify(fullMessage)

                if (result.isScam) {
                    Log.w(TAG, "SCAM SMS detected: type=${result.scamType} " +
                            "confidence=${result.confidence} keywords=${result.matchedKeywords}")

                    // Show notification
                    showScamSmsNotification(context, result)
                } else if (result.isSpam) {
                    Log.d(TAG, "SPAM SMS detected (confidence=${result.confidence})")
                    showSpamNotification(context)
                }

                // Log the message (sender hash + body stored locally only)
                val senderHash = repository.hashPhoneNumber(senderNumber)
                repository.logSms(
                    com.safering.android.data.local.entity.SmsLogEntity(
                        senderHash = senderHash,
                        messageBody = fullMessage,
                        classification = result.label,
                        scamType = result.scamType,
                        confidence = result.confidence,
                        timestamp = System.currentTimeMillis()
                    )
                )

                classifier.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            }
        }
    }

    private fun showScamSmsNotification(context: Context, result: SmsClassifier.ClassificationResult) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "sms_scam_alert"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "SMS Scam Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected scam SMS messages"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val scamType = result.scamType ?: "Suspicious"
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Scam SMS Detected")
            .setContentText("$scamType scam detected (${result.confidence.toString().take(4)})")
            .setStyle(android.app.Notification.BigTextStyle()
                .bigText("SafeRing detected a $scamType scam message. " +
                        "Do not reply, do not click any links."))
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun showSpamNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "sms_spam_alert"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "SMS Spam Alerts",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for detected spam SMS messages"
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Spam Message")
            .setContentText("This message appears to be spam")
            .setPriority(android.app.Notification.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1003, notification)
    }

    private fun maskNumber(number: String): String {
        if (number.length <= 5) return "****"
        return number.takeLast(2).let { last2 ->
            number.take(3) + "****" + last2
        }
    }
}
