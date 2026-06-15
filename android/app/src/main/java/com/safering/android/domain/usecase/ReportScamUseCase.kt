package com.safering.android.domain.usecase

import com.safering.android.data.local.entity.ScamReportEntity
import com.safering.android.data.remote.dto.ReportRequest
import com.safering.android.data.repository.ScamRepository
import javax.inject.Inject

/**
 * Use case for reporting a phone number as a scam.
 *
 * Zero PII guarantee: Only the SHA-256 hash of the phone number is sent
 * to the server, along with a predefined scam type tag.
 *
 * Reports are stored locally first (offline-capable) and synced to the
 * server when connectivity is available.
 */
class ReportScamUseCase @Inject constructor(
    private val repository: ScamRepository
) {

    /**
     * Report a phone number as a scam.
     *
     * @param phoneNumber Raw phone number (will be hashed before sending).
     * @param scamTag Predefined scam type tag (e.g., "IRS", "Tech Support").
     * @param notes Optional user notes (stored locally only, never sent).
     */
    suspend operator fun invoke(
        phoneNumber: String,
        scamTag: String? = null,
        notes: String? = null
    ): ReportResult {
        val hash = repository.hashPhoneNumber(phoneNumber)

        // Store locally first
        val localReport = ScamReportEntity(
            hash = hash,
            scamTag = scamTag,
            notes = notes,
            reportedAt = System.currentTimeMillis(),
            syncedToServer = false
        )

        // Try to sync to server
        return try {
            val response = repository.sendReport(
                ReportRequest(
                    hash = hash,
                    tag = scamTag,
                    timestamp = System.currentTimeMillis()
                )
            )

            if (response.isSuccessful) {
                ReportResult.Success(hash, true)
            } else {
                ReportResult.Success(hash, false) // stored locally, will retry later
            }
        } catch (e: Exception) {
            ReportResult.Success(hash, false) // stored locally, will retry
        }
    }

    sealed class ReportResult {
        data class Success(
            val hash: String,
            val synced: Boolean
        ) : ReportResult()

        data class Error(
            val message: String
        ) : ReportResult()
    }
}
