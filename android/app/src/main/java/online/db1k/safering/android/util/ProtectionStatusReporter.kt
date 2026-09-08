package online.db1k.safering.android.util

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import online.db1k.safering.android.service.FilterRulesStore
import online.db1k.safering.android.service.SmsNotificationListener

/**
 * Snapshot of protection toggles we can read — posted to edge (+ Firebase via ShieldAnalytics).
 */
object ProtectionStatusReporter {
    fun reportOnLaunch(context: Context) {
        Thread {
            try {
                val app = context.applicationContext
                val meta = mutableMapOf<String, Any?>(
                    "platform" to "android",
                    "model" to Build.MODEL,
                    "os" to "Android ${Build.VERSION.RELEASE}"
                )

                // Caller ID & spam / call screening role
                meta["call_screening_role"] = hasCallScreeningRole(app)

                // Notification listener (optional SMS trap)
                meta["notification_listener"] = isNotificationListenerEnabled(app)

                // Post notifications permission (tripwire alerts)
                meta["notifications_enabled"] = NotificationManagerCompat.from(app).areNotificationsEnabled()

                // Local filter inventory
                meta["keyword_count"] = FilterRulesStore.keywords(app).size
                meta["block_count"] = FilterRulesStore.blockDigits(app).size
                meta["allow_count"] = FilterRulesStore.allowDigits(app).size

                DeviceComms.log(
                    app,
                    entryPoint = "protection/status",
                    channel = "app",
                    action = "snapshot",
                    meta = meta
                )
                ShieldAnalytics.event(
                    app,
                    "protection_status",
                    meta.mapValues { it.value?.toString() ?: "" }
                )
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun hasCallScreeningRole(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                when {
                    rm == null -> "unknown"
                    rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) -> "held"
                    else -> "not_held"
                }
            } else {
                "unsupported"
            }
        } catch (_: Exception) {
            "error"
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                val cn = "${context.packageName}/${SmsNotificationListener::class.java.name}"
                flat.split(':').any { it.equals(cn, ignoreCase = true) || it.contains(context.packageName) }
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
