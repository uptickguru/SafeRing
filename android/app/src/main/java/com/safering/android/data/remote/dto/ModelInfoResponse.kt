package com.safering.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /v1/model/latest?type=number|sms
 *
 * Contains info about the latest available ML model for download.
 */
data class ModelInfoResponse(
    @SerializedName("version")
    val version: Int,

    @SerializedName("type")
    val type: String,

    @SerializedName("url")
    val url: String,

    @SerializedName("sha256")
    val sha256: String,

    @SerializedName("size_bytes")
    val sizeBytes: Long,

    @SerializedName("min_app_version")
    val minAppVersion: String?,

    @SerializedName("released_at")
    val releasedAt: Long
)
