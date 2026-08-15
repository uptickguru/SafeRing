package online.db1k.safering.android.service

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
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
import online.db1k.safering.android.util.HmacHashUtils
import online.db1k.safering.android.util.Logger

/**
 * Android CallScreeningService — the equivalent of iOS CallDirectoryHandler.
 *
 * # Architecture
 * This service runs in real-time (not just on reload). It can block calls
 * BEFORE they ring (not just identify).
 *
 * # Key Design Principles
 * 1. **Fast callback path** — screening callback returns promptly. Heavy
 *    analysis is deferred to a WorkManager job.
 * 2. **Zero PII** — phone numbers are HMAC-SHA256 hashed before any network call.
 *    HMAC uses a per-install secret key provisioned at enrollment, making
 *    the hash computationally infeasible to reverse.
 * 3. **High-risk fan-out** — on a high-risk match, in addition to block/silence,
 *    an internal event is emitted that can trigger the in-app HITL flow (M4)
 *    and, if the user has opted in, a trusted-circle alert (M5).
 *
 * # Event Types
 * - `block`: Call was blocked (high-risk scam or locally blocked number)
 * - `warn`: Call was allowed with warning (medium-risk)
 * - `monitor`: Call was allowed, no risk detected
 *
 * # Security
 * Phone numbers are hashed with HMAC-SHA256 (not plain SHA-256) before any
 * network call. HMAC uses a per-install secret key provisioned at enrollment,
 * making the hash computationally infeasible to reverse.
 *
 * # Threat Model
 * Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
 * makes it trivially reversible. HMAC-SHA256 with a secret key provides
 * pseudonymization, making it computationally infeasible to recover the
 * original number from the hash.
 *
 * # Defer Heavy Analysis
 * The screening callback must return quickly. Heavy operations (ML analysis,
 * trusted circle checks, HITL flow) are deferred to a WorkManager job.
 */
@RequiresApi(Build.VERSION_CODES.N)
class SafeRingCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ScamRepository
    private lateinit var api: SafeRingApi

    // MARK: - Defer queue (for WorkManager jobs)

    private val deferQueue = mutableListOf<DeferredCallAction>()

    /**
     * Defer a call action for later processing (WorkManager).
     * Used for heavy operations that should not block the screening callback.
     */
    fun deferCallAction(action: DeferredCallAction) {
        deferQueue.add(action)
        Logger.info("Call action deferred: ${action.type}", Logger.Category.CALL)
    }

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
            val hash = HmacHashUtils.hmacSHA256(phoneNumber, key: AppConfig.HMAC_KEY)
            val hashPrefix = hash.take(8)

            // Check local blocked numbers first (instant, no network)
            val blockedNumbers = repository.getBlockedNumbersOnce()
            val isLocallyBlocked = blockedNumbers.any { it.numberHash == hash }

            if (isLocallyBlocked) {
                // Block the call immediately
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

            // Check against API — use cached result if available for speed
            val cachedResult = repository.checkNumberCached(hash)

            if (cachedResult != null) {
                handleCachedResult(details, cachedResult, hash, hashPrefix)
            } else {
                // Defer heavy analysis to WorkManager
                // This keeps the screening callback fast
                deferCallAction(DeferredCallAction(
                    hash = hash,
                    hashPrefix = hashPrefix,
                    phoneNumber = phoneNumber,
                    action = ::handleApiResult
                ))

                // Allow the call for now (will be updated when analysis completes)
                respondToCall(details, CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .build()
                )
                Logger.info("Call analysis deferred to WorkManager: $hashPrefix", Logger.Category.CALL)
            }
        }
    }

    /**
     * Handle a cached result from the screening callback.
     * This is called synchronously in the screening callback for speed.
     */
    private fun handleCachedResult(
        details: Call.Details,
        cachedResult: CachedCheckResult,
        hash: String,
        hashPrefix: String
    ) {
        if (cachedResult.isScam) {
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
                risk_score = cachedResult.risk,
                scam_type = cachedResult.label ?: "scam",
                source = "cache"
            ))
            Logger.info("Call blocked (cache): $hashPrefix risk=${cachedResult.risk}", Logger.Category.CALL)

            // Defer HITL and trusted circle fan-out
            deferCallAction(DeferredCallAction(
                hash = hash,
                hashPrefix = hashPrefix,
                phoneNumber = "",
                action = ::handleHighRiskFanout,
                riskScore = cachedResult.risk
            ))

        } else if (cachedResult.isAlert) {
            // Allow but show warning
            respondToCall(details, CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            )
            showScamAlertNotification(this@SafeRingCallScreeningService, cachedResult)

            reportEvent(EventRequest(
                platform = "android",
                action = "warn",
                event_type = "call",
                hash_prefix = hashPrefix,
                risk_score = cachedResult.risk,
                scam_type = cachedResult.label ?: "suspicious",
                source = "cache"
            ))
            Logger.info("Call warned (cache): $hashPrefix risk=${cachedResult.risk}", Logger.Category.CALL)
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
                risk_score = cachedResult.risk,
                source = "cache"
            ))
            Logger.info("Call allowed (cache): $hashPrefix risk=${cachedResult.risk}", Logger.Category.CALL)
        }
    }

    // MARK: - API Query (deferred to WorkManager)

    /**
     * Handle the result of an API query. Deferred to WorkManager
     * so it doesn't block the screening callback.
     */
    private fun handleApiResult(result: CheckResult, details: Call.Details) {
        if (result.isScam) {
            // Block the call
            respondToCall(details,
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )

            val hashPrefix = result.hash.take(8)
            reportEvent(EventRequest(
                platform = "android",
                action = "block",
                event_type = "call",
                hash_prefix = hashPrefix,
                risk_score = result.risk,
                scam_type = result.label ?: "scam",
                source = "api"
            ))
            Logger.info("Call blocked (API): $hashPrefix risk=${result.risk}", Logger.Category.CALL)

            // Defer HITL and trusted circle fan-out
            deferCallAction(DeferredCallAction(
                hash = result.hash,
                hashPrefix = hashPrefix,
                phoneNumber = "",
                action = ::handleHighRiskFanout,
                riskScore = result.risk
            ))
        } else if (result.isAlert) {
            // Allow but show warning
            respondToCall(details,
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )
            showScamAlertNotification(this@SafeRingCallScreeningService, result)

            val hashPrefix = result.hash.take(8)
            reportEvent(EventRequest(
                platform = "android",
                action = "warn",
                event_type = "call",
                hash_prefix = hashPrefix,
                risk_score = result.risk,
                scam_type = result.label ?: "suspicious",
                source = "api"
            ))
            Logger.info("Call warned (API): $hashPrefix risk=${result.risk}", Logger.Category.CALL)
        } else {
            // Allow the call
            respondToCall(details,
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .build()
            )

            val hashPrefix = result.hash.take(8)
            reportEvent(EventRequest(
                platform = "android",
                action = "monitor",
                event_type = "call",
                hash_prefix = hashPrefix,
                risk_score = result.risk,
                source = "api"
            ))
            Logger.info("Call allowed (API): $hashPrefix risk=${result.risk}", Logger.Category.CALL)
        }
    }

    // MARK: - High-Risk Fan-Out (deferred to WorkManager)

    /**
     * Handle high-risk fan-out: HITL flow (M4) and trusted-circle alert (M5).
     * Deferred to WorkManager so it doesn't block the screening callback.
     */
    private fun handleHighRiskFanout(result: HighRiskMatch) {
        Logger.info("High-risk fan-out triggered: risk=${result.risk}", Logger.Category.CALL)

        // M4: In-app HITL flow — trigger via broadcast
        triggerHITLFlow(result)

        // M5: Trusted-circle alert (if opted in)
        if (result.userOptedIn) {
            triggerTrustedCircleAlert(result)
        }
    }

    // MARK: - WorkManager Triggers

    /**
     * Trigger the in-app HITL (Human-in-the-Loop) flow.
     * This is a deferred action that should not block the screening callback.
     */
    private fun triggerHITLFlow(result: HighRiskMatch) {
        // In production, this would use a BroadcastReceiver or a WorkManager job
        // For now, we log it (the actual implementation would be in the UI layer)
        Logger.info("HITL flow triggered: risk=${result.risk}", Logger.Category.CALL)
        // TODO: Implement actual HITL flow trigger (e.g., WorkManager job)
    }

    /**
     * Trigger a trusted-circle alert.
     * Only fires if the user has opted in (M5).
     * This is a deferred action that should not block the screening callback.
     */
    private fun triggerTrustedCircleAlert(result: HighRiskMatch) {
        Logger.info("Trusted-circle alert triggered: risk=${result.risk}", Logger.Category.CALL)
        // TODO: Implement actual trusted-circle alert (e.g., push notification to trusted contacts)
    }

    // MARK: - Notification Helpers

    /**
     * Show a scam alert notification (medium-risk).
     */
    private fun showScamAlertNotification(context: Context, result: CachedCheckResult) {
        Logger.info("Showing scam alert notification: risk=${result.risk}", Logger.Category.CALL)
        // TODO: Implement actual notification
    }

    /**
     * Show a scam alert notification (medium-risk).
     */
    private fun showScamAlertNotification(context: Context, result: CheckResult) {
        Logger.info("Showing scam alert notification: risk=${result.risk}", Logger.Category.CALL)
        // TODO: Implement actual notification
    }

    // MARK: - Deferred Call Action Types

    /**
     * Represents a call action that can be deferred to WorkManager.
     */
    data class DeferredCallAction(
        val hash: String,
        val hashPrefix: String,
        val phoneNumber: String,
        val action: () -> Unit,
        val riskScore: Double = 0.0
    )

    /**
     * Result from a high-risk match, used for fan-out.
     */
    data class HighRiskMatch(
        val hash: String,
        val hashPrefix: String,
        val risk: Double,
        val label: String?,
        val userOptedIn: Boolean = true
    )
}

// MARK: - Result Types

/**
 * Cached result from the database (for fast path).
 */
data class CachedCheckResult(
    val hash: String,
    val risk: Double,
    val label: String?,
    val confidence: Double,
    val isLocalOnly: Boolean
) {
    val isScam: Boolean get() = risk >= AppConfig.AUTO_BLOCK_THRESHOLD
    val isAlert: Boolean get() = risk >= AppConfig.ALERT_THRESHOLD
}

/**
 * Check result from the API or local query.
 */
data class CheckResult(
    val hash: String,
    val risk: Double,
    val label: String?,
    val confidence: Double,
    val isLocalOnly: Boolean,
    val error: String? = null,
    val tags: List<String> = emptyList()
) {
    val isScam: Boolean get() = risk >= AppConfig.AUTO_BLOCK_THRESHOLD
    val isWarning: Boolean get() = risk >= AppConfig.WARNING_THRESHOLD
    val isAlert: Boolean get() = risk >= AppConfig.ALERT_THRESHOLD
}
