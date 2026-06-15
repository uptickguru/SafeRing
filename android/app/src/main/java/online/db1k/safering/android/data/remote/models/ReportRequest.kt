package online.db1k.safering.android.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Request model for POST /v1/report
 * Mirrors the iOS ReportRequest.swift exactly.
 */
data class ReportRequest(
    @SerializedName("hash") val hash: String,
    @SerializedName("tag") val tag: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("device_model") val deviceModel: String?,
    @SerializedName("os_version") val osVersion: String?
)

data class ReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("total_reports") val totalReports: Int?
)
