package com.safering.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /v1/check?hash=<sha256>
 *
 * Contains the scam risk assessment for a queried phone number hash.
 */
data class CheckResponse(
    @SerializedName("hash")
    val hash: String,

    @SerializedName("risk")
    val risk: Float,

    @SerializedName("scam_type")
    val scamType: String?,

    @SerializedName("scam_label")
    val scamLabel: String?,

    @SerializedName("report_count")
    val reportCount: Int = 0,

    @SerializedName("is_blocked")
    val isBlocked: Boolean = false,

    @SerializedName("source")
    val source: String?,

    @SerializedName("checked_at")
    val checkedAt: Long = System.currentTimeMillis()
)
