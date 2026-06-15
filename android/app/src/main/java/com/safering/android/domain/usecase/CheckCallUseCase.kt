package com.safering.android.domain.usecase

import com.safering.android.data.local.entity.CallLogEntity
import com.safering.android.data.repository.ScamRepository
import com.safering.android.domain.model.CallRisk
import javax.inject.Inject

/**
 * Use case for checking an incoming or outgoing call against the scam database.
 *
 * Steps:
 * 1. Hash the phone number (SHA-256)
 * 2. Check local Room DB cache
 * 3. If miss, check remote API
 * 4. Log the call to call_logs table
 * 5. Return CallRisk domain model
 *
 * Zero PII guarantee: Only the hash leaves the device.
 */
class CheckCallUseCase @Inject constructor(
    private val repository: ScamRepository
) {

    /**
     * Check a phone number and log the result.
     *
     * @param phoneNumber Raw phone number in E.164 format (e.g., "15551234567").
     * @param direction INCOMING or OUTGOING.
     * @param wasBlocked Whether the system blocked this call.
     * @return CallRisk assessment.
     */
    suspend operator fun invoke(
        phoneNumber: String,
        direction: String = CallLogEntity.DIRECTION_INCOMING,
        wasBlocked: Boolean = false
    ): CallRisk {
        val hash = repository.hashPhoneNumber(phoneNumber)

        val result = repository.checkNumber(phoneNumber)

        // Log the call
        repository.logCall(
            CallLogEntity(
                hash = hash,
                rawPrefix = phoneNumber.take(6),
                direction = direction,
                riskScore = result.risk,
                scamLabel = result.scamLabel,
                isScam = result.isHighRisk || result.isMediumRisk,
                wasBlocked = wasBlocked,
                timestamp = System.currentTimeMillis()
            )
        )

        return CallRisk(
            riskScore = result.risk,
            scamType = result.scamType,
            scamLabel = result.scamLabel,
            reportCount = result.reportCount,
            wasBlocked = wasBlocked,
            fromCache = result.fromCache,
            error = result.error
        )
    }
}
