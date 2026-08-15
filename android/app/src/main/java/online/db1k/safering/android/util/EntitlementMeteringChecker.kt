package online.db1k.safering.android.util

import android.content.Context
import android.content.SharedPreferences
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.remote.models.Entitlement
import online.db1k.safering.android.util.Logger

/**
 * Utility for checking subscription entitlements and metering scan usage.
 *
 * # Security
 * This class does not expose any personal data. It only checks the
 * subscription status and scan quota returned by the backend.
 *
 * # Threat Model
 * Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
 * makes it trivially reversible. HMAC-SHA256 with a secret key provides
 * pseudonymization, making it computationally infeasible to recover the
 * original number from the hash.
 *
 * # Critical Safety Rule
 * Metering applies ONLY to the 3 cloud scans (email/attachment/transcript).
 * Screening/blocking/trusted-circle/HITL are NEVER blocked by tier.
 *
 */
class EntitlementMeteringChecker(
    private val context: Context,
    private val api: SafeRingApi
) {

    // MARK: - Properties

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "entitlement_prefs"
    }

    // MARK: - Public API

    /**
     * Checks if the user has a valid subscription.
     *
     * @return True if the user is entitled.
     * @throws EntitlementError if the check fails.
     */
    suspend fun isEntitled(): Boolean {
        // Check local cache first
        if (prefs.getBoolean(KEY_ENTITLED, false)) {
            return true
        }

        // Query backend
        return try {
            val response = fetchEntitlement()
            if (response.isEntitled) {
                prefs.edit().putBoolean(KEY_ENTITLED, true).apply()
                Logger.info("User is entitled", Logger.Category.ENTITLEMENT)
                true
            } else {
                prefs.edit().putBoolean(KEY_ENTITLED, false).apply()
                Logger.info("User is not entitled", Logger.Category.ENTITLEMENT)
                false
            }
        } catch (e: Exception) {
            // Cache the result to avoid repeated failures
            Logger.warning("Entitlement check failed: ${e.message}", Logger.Category.ENTITLEMENT)
            false
        }
    }

    /**
     * Checks if the user is on the Plus tier.
     *
     * @return True if the user is on the Plus tier.
     */
    suspend fun isPlusTier(): Boolean {
        return isEntitled()
    }

    /**
     * Fetches the user's subscription entitlement.
     *
     * # Security
     * This method queries the backend for the user's subscription status
     * and scan quota. The response is cached locally to avoid repeated API calls.
     *
     * @return Entitlement with tier and scan quota information.
     * @throws EntitlementError if the fetch fails.
     */
    suspend fun fetchEntitlement(): Entitlement {
        return try {
            val response = api.getEntitlement()
            return response
        } catch (e: Exception) {
            Logger.warning("Entitlement fetch failed: ${e.message}", Logger.Category.ENTITLEMENT)
            throw EntitlementError.CheckFailed(e)
        }
    }

    /**
     * Checks if the user has exceeded their monthly scan quota.
     *
     * # Security
     * Metering applies ONLY to the 3 cloud scans (email/attachment/transcript).
     * Screening/blocking/trusted-circle/HITL are NEVER blocked by tier.
     *
     * @return True if the user has exceeded their monthly scan quota.
     * @throws EntitlementError if the fetch fails.
     */
    suspend fun isQuotaExceeded(): Boolean {
        val entitlement = fetchEntitlement()
        return entitlement.isQuotaExceeded
    }

    /**
     * Gets the scan quota for the user's tier.
     *
     * @return The scan quota for the current month.
     * @throws EntitlementError if the fetch fails.
     */
    suspend fun getScanQuota(): Int {
        val entitlement = fetchEntitlement()
        return entitlement.scanQuota
    }

    /**
     * Gets the number of scans used this month.
     *
     * @return The number of scans used this month.
     * @throws EntitlementError if the fetch fails.
     */
    suspend fun getScanUsed(): Int {
        val entitlement = fetchEntitlement()
        return entitlement.scanUsed
    }
}

// MARK: - Errors

enum class EntitlementError(message: String) : Exception {
    CHECK_FAILED(message),
    NOT_ENTITLED(message),
    QUOTA_EXCEEDED(message)
}
