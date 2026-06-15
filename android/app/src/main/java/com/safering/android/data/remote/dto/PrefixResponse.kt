package com.safering.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /v1/prefixes
 *
 * Contains a known scam caller ID prefix with its associated risk weight.
 */
data class PrefixResponse(
    @SerializedName("prefix")
    val prefix: String,

    @SerializedName("risk_weight")
    val riskWeight: Float,

    @SerializedName("description")
    val description: String?,

    @SerializedName("region")
    val region: String?,

    @SerializedName("associated_types")
    val associatedTypes: String?,

    @SerializedName("report_count")
    val reportCount: Int = 0
)
