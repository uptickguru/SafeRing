package online.db1k.safering.android.service

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.remote.models.EventRequest
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.HashUtils
import online.db1k.safering.android.util.Logger

/**
 * Android CallScreeningService — the equivalent of iOS CallDirectoryHandler.
 *
 * This is MORE powerful than iOS CallDirectory because it:
 * 1. Runs in real-time (not just on reload)
 * 2. Can block calls BEFORE they ring (not just identify)
 * 3. Can respond to user whitelist/blocklist changes immediately
 *
 * Reports every action (block/warn/monitor) to POST /v1/event for
 * operational visibility on the server side.
 *
 * # Zero PII
 * Phone numbers are hashed before any network call.
 * Only the first 8 hex chars of the hash are sent in events.
 */
@RequiresApi(Build.VERSION_CODES.N)
class SafeRingCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ScamRepository
    private lateinit var api: SafeRingApi

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        api = SafeRingApi.create()
        repository = ScamRepository(api, db)
    }

    /** Fire-and-forget: send a device event, don't block on failure. */
    private fun reportEvent(event: EventRequest) {
        scope.launch {
            try {
                api.postEvent(event)
            } catch (e: Exception) {
                Logger.debug("Event send failed (non-critical): ${e.message}", Logger.Category.NETWORK)
            }
        }
    }

    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart ?: return

        scope.launch {
            val hash = HashUtils.sha256(phoneNumber)
            val hashPrefix = hash.take(8)

            // Check local blocked numbers first (instant, no network)
            val blockedNumbers = repository.getBlockedNumbersOnce()
            val isLocallyBlocked = blockedNumbers.any { it.numberHash == hash }

            if (isLocallyBlocked) {
                respondToCall(details, CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                )

                reportEvent(EventRequest(
                    platform = "android",
                    action = "block",
                    event_type = "call",
                    hash_prefix = hashPrefix,
                    source = "local_cache"
                ))
                Logger.info("Call blocked (local cache): $hashPrefix", Logger.Category.CALL)
                return@launch
            }

            // Check against API
            val result = repository.checkNumber(phoneNumber)

            if (result.isScam) {
                // Block the call
                respondToCall(details, CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                )

                reportEvent(EventRequest(
                    platform = "android",
                    action = "block",
                    event_type = "call",
                    hash_prefix = hashPrefix,
                    risk_score = result.risk,
                    scam_type = result.label ?: "scam",
                    source = "api"
                ))
                Logger.info("Call blocked (api): $hashPrefix risk=${result.risk}", Logger.Category.CALL)

            } else if (result.isAlert) {
                // Allow but show warning
                respondToCall(details, CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                )
                showScamAlertNotification(this@SafeRingCallScreeningService, phoneNumber, result)

                reportEvent(EventRequest(
                    platform = "android",
                    action = "warn",
                    event_type = "call",
                    hash_prefix = hashPrefix,
                    risk_score = result.risk,
                    scam_type = result.label ?: "suspicious",
                    source = "api"
                ))
                Logger.info("Call warned: $hashPrefix risk=${result.risk}", Logger.Category.CALL)
            } else {
                // Allow the call
                respondToCall(details, CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .build()
                )

                reportEvent(EventRequest(
                    platform = "android",
                    action = "monitor",
                    event_type = "call",
                    hash_prefix = hashPrefix,
                    risk_score = result.risk,
                    source = "api"
                ))
                Logger.debug("Call allowed: $hashPrefix risk=${result.risk}", Logger.Category.CALL)
            }
        }
    }

    private fun showScamAlertNotification(
        context: Context,
        phoneNumber: String,
        result: online.db1k.safering.android.data.repository.CheckResult
    ) {
        // Create a notification to alert the user about a potential scam call
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        val channelId = "scam_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Scam Call Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected scam calls"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Suspected Scam Call")
            .setContentText("${result.label ?: "Scam Risk"}: ${"%.0f".format(result.risk * 100)}% confidence")
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(phoneNumber.hashCode(), notification)
    }
}
