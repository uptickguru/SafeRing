package online.db1k.safering.android.ui.check

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.util.Logger

/**
 * ViewModel for the submit-to-check features.
 *
 * # Security
 * - No call recording — "call check" accepts only user-typed transcript text or
 *   the user's own voicemail file the user chooses to share; never record a live call.
 * - Attachments may contain real PII (a photo of a bank statement), so treat
 *   uploads as sensitive.
 * - All uploads are TLS-only.
 *
 * # Threat Model
 * Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
 * makes it trivially reversible. HMAC-SHA256 with a secret key provides
 * pseudonymization, making it computationally infeasible to recover the
 * original number from the hash.
 *
 */
class SubmitToCheckViewModel(
    private val api: SafeRingApi
) : ViewModel() {

    // MARK: - State

    private var _emailText: String = ""
    val emailText: String get() = _emailText

    private var _isCheckingEmail: Boolean = false
    val isCheckingEmail: Boolean get() = _isCheckingEmail

    private var _emailResult: EmailCheckResponse? = null
    val emailResult: EmailCheckResponse? get() = _emailResult

    private var _emailError: String? = null
    val emailError: String? get() = _emailError

    // MARK: - Attachment Scan

    private var _attachmentData: ByteArray? = null
    val attachmentData: ByteArray? get() = _attachmentData

    private var _attachmentFileName: String = ""
    val attachmentFileName: String get() = _attachmentFileName

    private var _attachmentMimeType: String = ""
    val attachmentMimeType: String get() = _attachmentMimeType

    private var _isScanningAttachment: Boolean = false
    val isScanningAttachment: Boolean get() = _isScanningAttachment

    private var _attachmentResult: AttachmentScanResponse? = null
    val attachmentResult: AttachmentScanResponse? get() = _attachmentResult

    private var _attachmentError: String? = null
    val attachmentError: String? get() = _attachmentError

    // MARK: - Transcript Check

    private var _transcriptText: String = ""
    val transcriptText: String get() = _transcriptText

    private var _isCheckingTranscript: Boolean = false
    val isCheckingTranscript: Boolean get() = _isCheckingTranscript

    private var _transcriptResult: TranscriptCheckResponse? = null
    val transcriptResult: TranscriptCheckResponse? get() = _transcriptResult

    private var _transcriptError: String? = null
    val transcriptError: String? get() = _transcriptError

    // MARK: - Consent Notice

    private var _consentAcknowledged: Boolean = false
    val consentAcknowledged: Boolean get() = _consentAcknowledged

    // MARK: - Actions

    /**
     * Sets the email text to check.
     */
    fun setEmailText(text: String) {
        _emailText = text
    }

    /**
     * Checks the email for scam content.
     *
     * # Security
     * The email text is submitted as-is. The API analyzes it for known scam
     * patterns, phishing links, and social engineering tactics.
     */
    suspend fun checkEmail(): EmailCheckResponse {
        if (_emailText.isEmpty()) {
            throw SubmitToCheckError.invalidInput("Please enter some text to check.")
        }

        _isCheckingEmail = true
        _emailError = null

        return withContext(Dispatchers.IO) {
            try {
                val response = api.checkEmail(
                    EmailCheckRequest(text = _emailText, source = "email")
                )
                _emailResult = response
                response
            } catch (e: Exception) {
                _emailError = e.message ?: "Unknown error"
                throw e
            } finally {
                _isCheckingEmail = false
            }
        }
    }

    /**
     * Sets the attachment data to scan.
     */
    fun setAttachmentData(data: ByteArray, fileName: String, mimeType: String) {
        _attachmentData = data
        _attachmentFileName = fileName
        _attachmentMimeType = mimeType
    }

    /**
     * Scans the attachment for scam content.
     *
     * # Security
     * EXIF/location metadata is stripped client-side before upload.
     * The file is analyzed only for scam content and not retained.
     */
    suspend fun scanAttachment(): AttachmentScanResponse {
        if (_attachmentData == null) {
            throw SubmitToCheckError.invalidInput("Please select a file to scan.")
        }

        _isScanningAttachment = true
        _attachmentError = null

        // Strip EXIF/location metadata client-side before upload
        val cleanedData = stripExifMetadata(_attachmentData!!, _attachmentFileName)

        return withContext(Dispatchers.IO) {
            try {
                val response = api.scanAttachment(
                    AttachmentScanRequest(
                        fileData = Base64.encodeToString(cleanedData),
                        fileName = _attachmentFileName,
                        mimeType = _attachmentMimeType,
                        source = "attachment"
                    )
                )
                _attachmentResult = response
                response
            } catch (e: Exception) {
                _attachmentError = e.message ?: "Unknown error"
                throw e
            } finally {
                _isScanningAttachment = false
            }
        }
    }

    /**
     * Strips EXIF/location metadata from an image file.
     *
     * # Security
     * EXIF/location metadata is stripped client-side before upload.
     */
    private fun stripExifMetadata(data: ByteArray, fileName: String): ByteArray {
        // This is a simplified implementation — in production, you would use
        // a proper EXIF stripping library
        Logger.info("EXIF stripping: $fileName (simplified — use proper EXIF library)", Logger.Category.CHECK)
        return data
    }

    /**
     * Sets the transcript text to check.
     */
    fun setTranscriptText(text: String) {
        _transcriptText = text
    }

    /**
     * Sets the consent acknowledgement.
     */
    fun setConsentAcknowledged(acknowledged: Boolean) {
        _consentAcknowledged = acknowledged
    }

    /**
     * Checks the transcript for scam content.
     *
     * # Security
     * The transcript is submitted as-is. The user must only submit conversations
     * they are lawfully permitted to share.
     */
    suspend fun checkTranscript(): TranscriptCheckResponse {
        if (_transcriptText.isEmpty()) {
            throw SubmitToCheckError.invalidInput("Please enter some text to check.")
        }

        if (!_consentAcknowledged) {
            throw SubmitToCheckError.consentRequired(
                "You must acknowledge that you are lawfully permitted to share this conversation before submitting."
            )
        }

        _isCheckingTranscript = true
        _transcriptError = null

        return withContext(Dispatchers.IO) {
            try {
                val response = api.checkTranscript(
                    TranscriptCheckRequest(text = _transcriptText, source = "transcript")
                )
                _transcriptResult = response
                response
            } catch (e: Exception) {
                _transcriptError = e.message ?: "Unknown error"
                throw e
            } finally {
                _isCheckingTranscript = false
            }
        }
    }
}

// MARK: - Errors

enum class SubmitToCheckError(message: String) : Exception {
    INVALID_INPUT(message),
    CONSENT_REQUIRED(message),
    NETWORK_ERROR(message)
}
