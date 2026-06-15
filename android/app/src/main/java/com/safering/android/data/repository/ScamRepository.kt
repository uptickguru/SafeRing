package com.safering.android.data.repository

import com.safering.android.data.local.dao.CallLogDao
import com.safering.android.data.local.dao.ScamNumberDao
import com.safering.android.data.local.dao.ScamPrefixDao
import com.safering.android.data.local.dao.SmsLogDao
import com.safering.android.data.local.entity.CallLogEntity
import com.safering.android.data.local.entity.ScamNumberEntity
import com.safering.android.data.local.entity.ScamPrefixEntity
import com.safering.android.data.local.entity.ScamReportEntity
import com.safering.android.data.local.entity.SmsLogEntity
import com.safering.android.data.remote.ApiService
import com.safering.android.data.remote.dto.CheckResponse
import com.safering.android.data.remote.dto.ReportRequest
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import retrofit2.Response

/**
 * Repository with offline-first logic for scam data.
 *
 * Implements the following strategy:
 * 1. Check local cache first (Room DB)
 * 2. If miss, fetch from server
 * 3. Cache server response locally
 * 4. Return result
 *
 * Zero PII guarantee: Phone numbers are SHA-256 hashed before any
 * network call. The raw number is never transmitted.
 */
class ScamRepository(
    private val apiService: ApiService,
    private val scamNumberDao: ScamNumberDao,
    private val scamPrefixDao: ScamPrefixDao,
    private val callLogDao: CallLogDao,
    private val smsLogDao: SmsLogDao
) {

    /**
     * SHA-256 hash the raw phone number for privacy.
     * Input: E.164 format number (e.g., "15551234567")
     * Output: lowercase hex SHA-256 string
     */
    fun hashPhoneNumber(phoneNumber: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(phoneNumber.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Check a phone number against local and remote scam databases.
     *
     * @param phoneNumber Raw phone number (E.164 format).
     * @return Pair of (risk score, scam label) or (0f, null) if unknown.
     */
    suspend fun checkNumber(phoneNumber: String): CheckResult {
        val hash = hashPhoneNumber(phoneNumber)

        // 1. Check local cache
        val cached = scamNumberDao.getByHash(hash)
        if (cached != null) {
            return CheckResult(
                risk = cached.riskScore,
                scamType = cached.scamType,
                scamLabel = cached.scamLabel,
                reportCount = cached.reportCount,
                isBlocked = cached.isBlocked,
                fromCache = true
            )
        }

        // 2. Check local prefix match
        val prefixMatch = findMatchingPrefix(phoneNumber)
        if (prefixMatch != null) {
            return CheckResult(
                risk = prefixMatch.riskWeight.coerceIn(0f, 0.6f),
                scamType = prefixMatch.associatedTypes,
                scamLabel = prefixMatch.description,
                reportCount = prefixMatch.reportCount,
                fromCache = true
            )
        }

        // 3. Try remote lookup
        return try {
            val response = apiService.checkNumber(hash)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Cache the result locally
                scamNumberDao.insert(
                    ScamNumberEntity(
                        hash = body.hash,
                        riskScore = body.risk,
                        scamType = body.scamType,
                        scamLabel = body.scamLabel,
                        reportCount = body.reportCount,
                        isBlocked = body.isBlocked,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                CheckResult(
                    risk = body.risk,
                    scamType = body.scamType,
                    scamLabel = body.scamLabel,
                    reportCount = body.reportCount,
                    isBlocked = body.isBlocked,
                    fromCache = false
                )
            } else {
                CheckResult(risk = 0f, fromCache = true)
            }
        } catch (e: Exception) {
            // Network error — return safe default (no risk assessment possible)
            CheckResult(risk = 0f, fromCache = true, error = e.message)
        }
    }

    /**
     * Find a matching scam prefix for the given phone number.
     */
    private suspend fun findMatchingPrefix(phoneNumber: String): ScamPrefixEntity? {
        // Try progressively shorter prefixes (max 6 digits for area codes)
        for (len in minOf(6, phoneNumber.length) downTo 2) {
            val prefix = phoneNumber.substring(0, len)
            val match = scamPrefixDao.findMatchingPrefix(prefix)
            if (match != null) return match
        }
        return null
    }

    /**
     * Get recent call log entries as a reactive Flow.
     */
    fun getRecentCallLogs(limit: Int = 50): Flow<List<CallLogEntity>> {
        return callLogDao.getRecent(limit)
    }

    /**
     * Get scam call entries as a reactive Flow.
     */
    fun getScamCalls(limit: Int = 50): Flow<List<CallLogEntity>> {
        return callLogDao.getScamCalls(limit)
    }

    /**
     * Log an incoming or outgoing call.
     */
    suspend fun logCall(callLog: CallLogEntity) {
        callLogDao.insert(callLog)
    }

    /**
     * Log a classified SMS message.
     */
    suspend fun logSms(smsLog: SmsLogEntity) {
        smsLogDao.insert(smsLog)
    }

    /**
     * Get all scam prefixes.
     */
    suspend fun getAllPrefixes(): List<ScamPrefixEntity> {
        return scamPrefixDao.getAll()
    }

    /**
     * Replace the entire scam numbers cache with fresh server data.
     */
    suspend fun syncScamNumbers(numbers: List<ScamNumberEntity>) {
        scamNumberDao.clearAll()
        scamNumberDao.insertAll(numbers)
    }

    /**
     * Replace the entire scam prefixes cache with fresh server data.
     */
    suspend fun syncPrefixes(prefixes: List<ScamPrefixEntity>) {
        scamPrefixDao.clearAll()
        scamPrefixDao.insertAll(prefixes)
    }

    /**
     * Scam call count as a reactive Flow.
     */
    fun scamCallCount(): Flow<Int> = callLogDao.scamCallCount()

    /**
     * Scam message count as a reactive Flow.
     */
    fun scamMessageCount(): Flow<Int> = smsLogDao.scamMessageCount()

    /**
     * Send a scam report to the server.
     * Only the SHA-256 hash and a predefined tag are transmitted.
     */
    suspend fun sendReport(request: ReportRequest): Response<Unit> {
        return apiService.reportNumber(request)
    }

    data class CheckResult(
        val risk: Float,
        val scamType: String? = null,
        val scamLabel: String? = null,
        val reportCount: Int = 0,
        val isBlocked: Boolean = false,
        val fromCache: Boolean = true,
        val error: String? = null
    ) {
        val isHighRisk: Boolean get() = risk >= 0.7f
        val isMediumRisk: Boolean get() = risk in 0.4f until 0.7f
        val isLowRisk: Boolean get() = risk < 0.4f
    }
}
