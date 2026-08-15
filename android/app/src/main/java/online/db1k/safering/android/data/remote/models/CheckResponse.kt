package online.db1k.safering.android.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /v1/check?hash=<hmac-sha256>
 * Mirrors the iOS CheckResponse.swift exactly.
 * The hash field is an HMAC-SHA256 hash, not plain SHA-256.
 */
data class CheckResponse(
    @SerializedName("hash") val hash: String,
    @SerializedName("risk") val risk: Double,
    @SerializedName("label") val label: String?,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("tags") val tags: List<String>,
    @SerializedName("first_reported_at") val firstReportedAt: Long?,
    @SerializedName("report_count") val reportCount: Int,
    @SerializedName("is_confirmed") val isConfirmed: Boolean,
    @SerializedName("suggested_action") val suggestedAction: String?
)
