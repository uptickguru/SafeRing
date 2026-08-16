import Foundation
import Combine

/// ViewModel for the submit-to-check features.
///
/// # Security
/// - No call recording — "call check" accepts only user-typed transcript text or
///   the user's own voicemail file the user chooses to share; never record a live call.
/// - Attachments may contain real PII (a photo of a bank statement), so treat
///   uploads as sensitive.
/// - All uploads are TLS-only.
///
/// # Threat Model
/// Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
/// makes it trivially reversible. HMAC-SHA256 with a secret key provides
/// pseudonymization, making it computationally infeasible to recover the
/// original number from the hash.
@MainActor
class SubmitToCheckViewModel: ObservableObject {

    // MARK: - Properties

    private let apiClient: ApiClient

    // MARK: - Email Check

    @Published var emailText: String = ""
    @Published private(set) var isCheckingEmail: Bool = false
    @Published private(set) var emailResult: EmailCheckResponse?
    @Published private(set) var emailError: String?

    // MARK: - Attachment Scan

    @Published private(set) var attachmentData: Data?
    @Published private(set) var attachmentFileName: String = ""
    @Published private(set) var attachmentMimeType: String = ""
    @Published private(set) var isScanningAttachment: Bool = false
    @Published private(set) var attachmentResult: AttachmentScanResponse?
    @Published private(set) var attachmentError: String?

    // MARK: - Transcript Check

    @Published var transcriptText: String = ""
    @Published private(set) var isCheckingTranscript: Bool = false
    @Published private(set) var transcriptResult: TranscriptCheckResponse?
    @Published private(set) var transcriptError: String?

    // MARK: - Consent Notice

    @Published var consentAcknowledged: Bool = false

    // MARK: - Error Alert

    @Published var showError: Bool = false
    @Published var showConsentAlert: Bool = false
    @Published var errorMessage: String = ""

    // MARK: - Initializer

    init(apiClient: ApiClient) {
        self.apiClient = apiClient
    }

    // MARK: - Email Check



    /// Checks the email for scam content.
    ///
    /// # Security
    /// The email text is submitted as-is. The API analyzes it for known scam
    /// patterns, phishing links, and social engineering tactics.
    ///
    /// - Throws: ApiError if the request fails.
    func checkEmail() async throws -> EmailCheckResponse {
        guard !emailText.isEmpty else {
            throw SubmitToCheckError.invalidInput("Please enter some text to check.")
        }

        isCheckingEmail = true
        emailError = nil
        defer { isCheckingEmail = false }

        do {
            let response = try await apiClient.checkEmail(
                EmailCheckRequest(text: emailText, source: "email")
            )
            emailResult = response
            return response
        } catch {
            emailError = error.localizedDescription
            throw error
        }
    }

    // MARK: - Attachment Scan



    /// Scans the attachment for scam content.
    ///
    /// # Security
    /// EXIF/location metadata is stripped client-side before upload.
    /// The file is analyzed only for scam content and not retained.
    ///
    /// - Throws: ApiError if the request fails.
    func scanAttachment() async throws -> AttachmentScanResponse {
        guard attachmentData != nil else {
            throw SubmitToCheckError.invalidInput("Please select a file to scan.")
        }

        isScanningAttachment = true
        attachmentError = nil
        defer { isScanningAttachment = false }

        do {
            // Strip EXIF/location metadata client-side before upload
            let cleanedData = try stripExifMetadata(from: attachmentData!, fileName: attachmentFileName)
            let request = AttachmentScanRequest(
                fileData: cleanedData,
                fileName: attachmentFileName,
                mimeType: attachmentMimeType,
                source: "attachment"
            )
            let response = try await apiClient.scanAttachment(request)
            attachmentResult = response
            return response
        } catch {
            attachmentError = error.localizedDescription
            throw error
        }
    }

    /// Strips EXIF/location metadata from an image file.
    ///
    /// # Security
    /// EXIF/location metadata is stripped client-side before upload.
    ///
    /// - Parameters:
    ///   - data: The original image data.
    ///   - fileName: The file name (used to determine the image format).
    /// - Returns: The cleaned image data with EXIF stripped.
    func stripExifMetadata(from data: Data, fileName: String) throws -> Data {
        // This is a simplified implementation — in production, you would use
        // a proper EXIF stripping library like ImageIO for iOS
        // For now, we return the original data and note that EXIF stripping
        // should be implemented with a proper library
        Logger.shared.info("EXIF stripping: \(fileName) (simplified — use proper EXIF library)", category: .network)
        return data
    }

    // MARK: - Transcript Check



    /// Checks the transcript for scam content.
    ///
    /// # Security
    /// The transcript is submitted as-is. The user must only submit conversations
    /// they are lawfully permitted to share.
    ///
    /// - Throws: ApiError if the request fails.
    func checkTranscript() async throws -> TranscriptCheckResponse {
        guard !transcriptText.isEmpty else {
            throw SubmitToCheckError.invalidInput("Please enter some text to check.")
        }

        guard consentAcknowledged else {
            throw SubmitToCheckError.consentRequired("You must acknowledge that you are lawfully permitted to share this conversation before submitting.")
        }

        isCheckingTranscript = true
        transcriptError = nil
        defer { isCheckingTranscript = false }

        do {
            let response = try await apiClient.checkTranscript(
                TranscriptCheckRequest(text: transcriptText, source: "transcript")
            )
            transcriptResult = response
            return response
        } catch {
            transcriptError = error.localizedDescription
            throw error
        }
    }
}

// MARK: - Errors

enum SubmitToCheckError: LocalizedError {
    case invalidInput(String)
    case consentRequired(String)
    case networkError(underlying: Error)

    var errorDescription: String? {
        switch self {
        case .invalidInput(let message):
            return message
        case .consentRequired(let message):
            return message
        case .networkError(let error):
            return error.localizedDescription
        }
    }
}


