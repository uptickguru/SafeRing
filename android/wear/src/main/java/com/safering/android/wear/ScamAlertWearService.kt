package com.safering.android.wear

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class ScamAlertWearService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.safering.android") {
            val notification = sbn.notification
            val extras = notification.extras
            val riskScore = extras.getDouble("risk_score", 0.0)
            val scamType = extras.getString("scam_type", "unknown")

            if (riskScore > 0.5) {
                // Alert the user on their watch about a scam call/SMS
                sendScamAlert(riskScore, scamType)
            }
        }
    }

    private fun sendScamAlert(riskScore: Double, scamType: String) {
        val alert = mapOf(
            "action" to "scam_alert",
            "risk_score" to riskScore,
            "scam_type" to scamType
        )
        // Forward to phone app via Wearable Data Layer
    }
}
