package com.safering.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /v1/report
 *
 * Zero PII guarantee: Only the SHA-256 hash of the phone number is sent,
 * never the raw number. The scam type tag is a predefined category label,
 * not free-form text.
 */
data class ReportRequest(
    @SerializedName("hash")
    val hash: String,

    @SerializedName("tag")
    val tag: String?,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
