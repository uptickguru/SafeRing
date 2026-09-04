package online.db1k.safering.android.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import online.db1k.safering.android.MainActivity

/**
 * Local tripwire alerts. Never includes the incoming number or message body.
 */
object TripwireNotifier {

    const val CHANNEL_ID = "safering.tripwire"
    const val NOTIF_UNKNOWN_CALL = 7101
    const val NOTIF_SUSPICIOUS_SMS = 7102

    const val EXTRA_HELP_REASON = "help_reason"
    const val EXTRA_SHARED_TEXT = "shared_text"
    const val EXTRA_SHOW_CHECKIN = "show_checkin"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Family tripwire",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Unknown call silenced. Get your person if it felt wrong."
                enableVibration(true)
            }
        )
    }

    fun notifyUnknownCallSilenced(context: Context) {
        ensureChannel(context)
        if (!canNotify(context)) return

        val app = context.applicationContext
        val trusted = HouseholdStore.get(app).trustedContactName.ifBlank { "your person" }

        val open = pendingActivity(
            app,
            1,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHOW_CHECKIN, true)
            }
        )
        val help = pendingBroadcast(
            app,
            2,
            Intent(app, HelpActionReceiver::class.java).setAction(HelpActionReceiver.ACTION_HELP)
                .putExtra(EXTRA_HELP_REASON, HelpReason.AFTER_CALL.name)
        )
        val ok = pendingBroadcast(
            app,
            3,
            Intent(app, HelpActionReceiver::class.java).setAction(HelpActionReceiver.ACTION_OK)
        )

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Unknown call was silenced")
            .setContentText("If anyone asked for money, passwords, or secrecy, get $trusted.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "GMG Shield silenced a number that is not in Contacts and is not your person. " +
                        "We do not show the number. If it felt wrong, get $trusted on their saved number."
                )
            )
            .setContentIntent(open)
            .addAction(0, "Get $trusted", help)
            .addAction(0, "It was fine", ok)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            NotificationManagerCompat.from(app).notify(NOTIF_UNKNOWN_CALL, notification)
        } catch (_: SecurityException) {
            // Notification permission denied.
        }
    }

    fun cancelUnknownCall(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIF_UNKNOWN_CALL)
    }


    fun notifySuspiciousSms(context: Context, hasNumber: Boolean) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val app = context.applicationContext
        val trusted = HouseholdStore.get(app).trustedContactName.ifBlank { "your person" }
        val open = pendingActivity(
            app,
            11,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        val help = pendingBroadcast(
            app,
            12,
            Intent(app, HelpActionReceiver::class.java).setAction(HelpActionReceiver.ACTION_HELP)
                .putExtra(EXTRA_HELP_REASON, HelpReason.PASTE_SCAM.name)
        )
        val body = if (hasNumber) {
            "A text looked like a scam. We saved a private fingerprint of the number for filtering. Get $trusted if money was mentioned."
        } else {
            "A text looked like a scam (no phone number in the alert). Get $trusted if money was mentioned."
        }
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Suspicious text")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(0, "Get $trusted", help)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIF_SUSPICIOUS_SMS, notification)
        } catch (_: SecurityException) {
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingBroadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
