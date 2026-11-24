package online.db1k.safering.android.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import online.db1k.safering.android.util.Logger
import online.db1k.safering.android.util.PhoneNumberUtils

/**
 * Best-effort SMS/RCS **sender number** capture from notification shade.
 *
 * - Does **not** require RECEIVE_SMS / default SMS app (Play-restricted).
 * - User must enable Notification access in system settings.
 * - Reliability varies by Messages/OEM/RCS (name-only titles = no number).
 * - Stores HMAC + risk labels only; body snippet used on-device for keywords, not uploaded.
 */
class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!HouseholdStore.get(this).smsNotificationCaptureEnabled) return
        if (sbn.packageName in IGNORE_PACKAGES) return
        if (!isMessagingPackage(sbn.packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val combined = listOf(title, text, bigText, subText, infoText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (combined.isBlank()) return

        // Prefer number from title (usually sender), else any phone in notification text
        val fromTitle = PhoneNumberUtils.extractPhones(title).firstOrNull()
            ?: extractTelUri(title)
        val fromBody = PhoneNumberUtils.extractPhones("$text\n$bigText")
        val sender = fromTitle
            ?: PhoneNumberUtils.extractPhones(combined).firstOrNull()
            ?: extractTelUri(combined)

        val snippet = (text.ifBlank { bigText }).take(280)

        SmsIntake.recordFromNotification(
            context = this,
            packageName = sbn.packageName.orEmpty(),
            senderRaw = sender,
            title = title,
            snippet = snippet,
            allText = combined
        )
    }

    private fun extractTelUri(raw: String): String? {
        val m = TEL_URI.find(raw) ?: return null
        val n = PhoneNumberUtils.normalizeToE164(m.groupValues[1])
        return n.takeIf { PhoneNumberUtils.isPlausibleE164(it) }
    }

    private fun isMessagingPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg in KNOWN_MESSAGING) return true
        // Generic: many OEM message apps include "mms" / "messaging" / "sms"
        val p = pkg.lowercase()
        return p.contains("messaging") || p.contains("mms") ||
            p.contains("sms") || p.contains("message") ||
            p.contains("telephony")
    }

    companion object {
        private val TEL_URI = Regex("""tel:([+\d().\-\s]{7,})""", RegexOption.IGNORE_CASE)

        private val KNOWN_MESSAGING = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.samsung.android.messaging.ui",
            "com.android.messaging",
            "com.motorola.messaging",
            "com.oneplus.mms",
            "com.coloros.mms",
            "com.android.mms.service",
        )

        private val IGNORE_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "online.db1k.safering.android"
        )

        fun isEnabled(context: Context): Boolean {
            val cn = ComponentName(context, SmsNotificationListener::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.split(':').any {
                ComponentName.unflattenFromString(it)?.equals(cn) == true ||
                    it.contains(context.packageName) && it.contains("SmsNotificationListener")
            }
        }

        /**
         * Opens the best available system screen for Notification access.
         * Sideload/FAD builds often need Apps → GMG Shield → ⋮ → Allow restricted settings first.
         */
        fun openSettings(context: Context) {
            val cn = ComponentName(context, SmsNotificationListener::class.java)
            val attempts = mutableListOf<android.content.Intent>()
            // API 30+: per-app notification listener detail
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    val detail = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    attempts.add(detail)
                } catch (_: Throwable) { }
            }
            attempts.add(
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            // App details — user can ⋮ Allow restricted settings here on many OEMs
            attempts.add(
                android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            for (intent in attempts) {
                try {
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Logger.debug("NLS open failed ${intent.action}: ${e.message}", Logger.Category.SMS)
                }
            }
        }

        /** App info screen — step 1 for "Allow restricted settings" on Android 13+. */
        fun openAppDetails(context: Context) {
            try {
                context.startActivity(
                    android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                Logger.debug("App details open failed: ${e.message}", Logger.Category.SMS)
            }
        }
    }
}
