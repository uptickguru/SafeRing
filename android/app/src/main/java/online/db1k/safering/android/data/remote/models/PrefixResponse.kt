package online.db1k.safering.android.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /v1/prefixes
 * Mirrors the iOS PrefixResponse.swift exactly.
 */
data class PrefixResponse(
    @SerializedName("prefixes") val prefixes: List<ScamPrefix>,
    @SerializedName("updated_at") val updatedAt: Long
)

data class ScamPrefix(
    @SerializedName("prefix") val prefix: String,
    @SerializedName("risk") val risk: Double,
    @SerializedName("count") val count: Int,
    @SerializedName("common_tags") val commonTags: List<String>
)
