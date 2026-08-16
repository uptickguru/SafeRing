package online.db1k.safering.android.util

import android.content.Context
import android.content.SharedPreferences
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.remote.models.Entitlement

class EntitlementMeteringChecker(
    private val context: Context,
    private val api: SafeRingApi
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "entitlement_prefs"
        const val KEY_ENTITLED = "entitled"
    }

    suspend fun isEntitled(): Boolean {
        if (prefs.getBoolean(KEY_ENTITLED, false)) {
            return true
        }
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
            Logger.warning("Entitlement check failed: ${e.message}", Logger.Category.ENTITLEMENT)
            false
        }
    }

    suspend fun isPlusTier(): Boolean = isEntitled()

    suspend fun fetchEntitlement(): Entitlement {
        return try {
            api.getEntitlement()
        } catch (e: Exception) {
            Logger.warning("Entitlement fetch failed: ${e.message}", Logger.Category.ENTITLEMENT)
            throw EntitlementError.CheckFailed(e.message ?: "unknown")
        }
    }

    suspend fun isQuotaExceeded(): Boolean = fetchEntitlement().isQuotaExceeded
    suspend fun getScanQuota(): Int = fetchEntitlement().scanQuota
    suspend fun getScanUsed(): Int = fetchEntitlement().scanUsed
}

sealed class EntitlementError(message: String) : Exception(message) {
    class CheckFailed(message: String) : EntitlementError(message)
    class NotEntitled(message: String = "not entitled") : EntitlementError(message)
    class QuotaExceeded(message: String = "quota exceeded") : EntitlementError(message)
}
