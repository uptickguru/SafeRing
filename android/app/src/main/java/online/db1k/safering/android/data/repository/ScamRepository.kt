package online.db1k.safering.android.data.repository

import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.local.models.ScamNumberEntity
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.remote.models.ReportRequest
import online.db1k.safering.android.data.remote.models.ReportResponse
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.HashUtils
import kotlinx.coroutines.flow.Flow

/**
 * Repository bridging the remote API and local database.
 * Mirrors the iOS ScamRepository.swift.
 */
class ScamRepository(
    private val api: SafeRingApi,
    private val db: AppDatabase
) {
    private val scamDao = db.scamNumberDao()
    private val callLogDao = db.callLogDao()
    private val smsLogDao = db.smsLogDao()

    // ─── Scam Number Queries ─────────────────────────────────────

    fun getAllScamNumbers(): Flow<List<ScamNumberEntity>> =
        scamDao.getAllScamNumbers()

    fun getBlockedNumbers(): Flow<List<ScamNumberEntity>> =
        scamDao.getBlockedNumbers()

    suspend fun getBlockedNumbersOnce(): List<ScamNumberEntity> =
        scamDao.getBlockedNumbersOnce()

    // ─── Check Number ────────────────────────────────────────────

    /**
     * Checks a phone number against the scam database.
     * Only sends the SHA-256 hash — never the plain number.
     */
    suspend fun checkNumber(phoneNumber: String): CheckResult {
        val hash = HashUtils.sha256(phoneNumber)

        // Check local cache first
        val cached = scamDao.getByHash(hash)
        if (cached != null) {
            return CheckResult(
                hash = hash,
                risk = cached.riskScore,
                label = cached.scamLabel,
                confidence = cached.confidence,
                isLocalOnly = false
            )
        }

        // Query remote API
        return try {
            val response = api.checkNumber(hash)
            val entity = ScamNumberEntity(
                numberHash = response.hash,
                riskScore = response.risk,
                scamLabel = response.label ?: "Unknown",
                confidence = response.confidence,
                firstReportedAt = response.firstReportedAt,
                reportCount = response.reportCount
            )
            scamDao.upsert(entity)

            CheckResult(
                hash = response.hash,
                risk = response.risk,
                label = response.label,
                confidence = response.confidence,
                isLocalOnly = false
            )
        } catch (e: Exception) {
            CheckResult(
                hash = hash,
                risk = 0.0,
                label = null,
                confidence = 0.0,
                isLocalOnly = true,
                error = e.message
            )
        }
    }

    // ─── Fetch Prefixes ──────────────────────────────────────────

    /**
     * Fetches known scam prefixes for offline prefix-based blocking.
     */
    suspend fun fetchPrefixes(): List<ScamPrefixResult> {
        return try {
            val response = api.fetchPrefixes()
            response.prefixes.map { prefix ->
                ScamPrefixResult(
                    prefix = prefix.prefix,
                    risk = prefix.risk,
                    count = prefix.count,
                    commonTags = prefix.commonTags
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── Submit Report ───────────────────────────────────────────

    /**
     * Submits a user report for a scam number.
     */
    suspend fun submitReport(
        phoneNumber: String,
        scamType: String
    ): ReportResponse {
        val hash = HashUtils.sha256(phoneNumber)
        val request = ReportRequest(
            hash = hash,
            tag = scamType,
            timestamp = System.currentTimeMillis() / 1000,
            deviceModel = android.os.Build.MODEL,
            osVersion = "${android.os.Build.VERSION.SDK_INT}"
        )
        return api.submitReport(request)
    }

    // ─── Rate Limit Status ───────────────────────────────────────

    /**
     * Returns whether the local cache is fresh enough to use.
     */
    fun isCacheFresh(lastUpdated: Long): Boolean {
        val age = System.currentTimeMillis() - lastUpdated
        return age < AppConfig.CACHE_MAX_AGE_HOURS * 60 * 60 * 1000
    }

    // ─── Whitelist Operations ────────────────────────────────────

    /**
     * Adds a phone number hash to the whitelist.
     */
    suspend fun addToWhitelist(phoneNumber: String) {
        val hash = HashUtils.sha256(phoneNumber)
        val existing = scamDao.getByHash(hash)
        if (existing != null) {
            scamDao.upsert(existing.copy(shouldBlock = false, riskScore = 0.0))
        }
    }

    /**
     * Blocks a phone number hash.
     */
    suspend fun addToBlocklist(phoneNumber: String) {
        val hash = HashUtils.sha256(phoneNumber)
        val existing = scamDao.getByHash(hash)
        if (existing != null) {
            scamDao.upsert(existing.copy(shouldBlock = true))
        } else {
            scamDao.upsert(
                ScamNumberEntity(
                    numberHash = hash,
                    riskScore = 1.0,
                    scamLabel = "User Blocked",
                    confidence = 1.0,
                    shouldBlock = true
                )
            )
        }
    }

    /**
     * Removes a hash from both whitelist and blocklist.
     */
    suspend fun removeFromList(hash: String) {
        scamDao.deleteByHash(hash)
    }
}

// ─── Result Types ────────────────────────────────────────────────

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

data class ScamPrefixResult(
    val prefix: String,
    val risk: Double,
    val count: Int,
    val commonTags: List<String>
)
