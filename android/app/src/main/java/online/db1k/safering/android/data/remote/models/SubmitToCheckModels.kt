package online.db1k.safering.android.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a request to check an email for scam content.
 *
 * # Security
 * The email text is submitted as-is. The API analyzes it for known scam
 * patterns, phishing links, and social engineering tactics.
 */
data class EmailCheckRequest(
    @SerializedName("text") val text: String,
    @SerializedName("source") val source: String = "email"
)

/**
 * Response from POST /v1/email.
 */
data class EmailCheckResponse(
    @SerializedName("text") val text: String? = null,
    @SerializedName("is_scam") val isScam: Boolean,
    @SerializedName("scam_type") val scamType: String? = null,
    @SerializedName("risk_score") val riskScore: Double
)

/**
 * Represents a request to scan an attachment (image/document) for scam content.
 *
 * # Security
 * EXIF/location metadata is stripped client-side before upload.
 * The file is analyzed only for scam content and not retained.
 */
data class AttachmentScanRequest(
    @SerializedName("file_data") val fileData: String, // base64 encoded
    @SerializedName("file_name") val fileName: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("source") val source: String = "attachment"
)

/**
 * Response from POST /v1/attachment.
 */
data class AttachmentScanResponse(
    @SerializedName("is_scam") val isScam: Boolean,
    @SerializedName("scam_type") val scamType: String? = null,
    @SerializedName("risk_score") val riskScore: Double,
    @SerializedName("exif_stripped") val exifStripped: Boolean
)

/**
 * Represents a request to check a call transcript for scam content.
 *
 * # Security
 * The transcript is submitted as-is. The user must only submit conversations
 * they are lawfully permitted to share.
 */
data class TranscriptCheckRequest(
    @SerializedName("text") val text: String,
    @SerializedName("source") val source: String = "transcript"
)

/**
 * Response from POST /v1/call.
 */
data class TranscriptCheckResponse(
    @SerializedName("text") val text: String? = null,
    @SerializedName("is_scam") val isScam: Boolean,
    @SerializedName("scam_type") val scamType: String? = null,
    @SerializedName("risk_score") val riskScore: Double
)
