package com.safering.android.domain.usecase

import com.safering.android.data.local.entity.ScamPrefixEntity
import com.safering.android.data.remote.ApiService
import com.safering.android.data.remote.dto.PrefixResponse
import com.safering.android.data.repository.ScamRepository
import javax.inject.Inject

/**
 * Use case for syncing scam data from the server.
 *
 * Periodically fetches:
 * - Updated scam prefixes
 * - (Future) Latest ML model weights via SyncWorker
 *
 * The sync is designed to be efficient — prefixes are replaced atomically
 * on each successful sync.
 *
 * Privacy: Only anonymous prefix patterns are fetched, no PII involved.
 */
class SyncScamDataUseCase @Inject constructor(
    private val repository: ScamRepository,
    private val apiService: ApiService
) {

    /**
     * Perform a full sync of scam data from the server.
     *
     * @return SyncResult indicating what was updated.
     */
    suspend operator fun invoke(): SyncResult {
        val errors = mutableListOf<String>()
        var prefixesUpdated = false

        // 1. Sync scam prefixes
        try {
            val response = apiService.getPrefixes()
            if (response.isSuccessful && response.body() != null) {
                val remotePrefixes = response.body()!!
                if (remotePrefixes.isNotEmpty()) {
                    val entities = remotePrefixes.map { it.toEntity() }
                    repository.syncPrefixes(entities)
                    prefixesUpdated = true
                }
            }
        } catch (e: Exception) {
            errors.add("Prefix sync failed: ${e.message}")
        }

        return SyncResult(
            prefixesUpdated = prefixesUpdated,
            errors = errors
        )
    }

    data class SyncResult(
        val prefixesUpdated: Boolean = false,
        val errors: List<String> = emptyList()
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val hasUpdates: Boolean get() = prefixesUpdated
    }
}

/**
 * Extension to convert API prefix response to local entity.
 */
private fun PrefixResponse.toEntity() = ScamPrefixEntity(
    prefix = this.prefix,
    riskWeight = this.riskWeight,
    description = this.description,
    region = this.region,
    associatedTypes = this.associatedTypes,
    reportCount = this.reportCount,
    lastUpdated = System.currentTimeMillis()
)
