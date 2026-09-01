import Foundation
import SwiftUI

// MARK: - Email Check

/// Request to check an email address for scam content.
///
/// # Security
/// The email text is submitted as-is. The API analyzes it for known scam
/// patterns, phishing links, and social engineering tactics.
struct EmailCheckRequest: Encodable {
    let text: String
    let source: String // "email"
}

/// Response from POST /v1/email.
struct EmailCheckResponse: Decodable {
    let text: String?
    let isScam: Bool
    let scamType: String?
    let riskScore: Double
}

// MARK: - Attachment Scan

/// Request to scan an attachment (image/document) for scam content.
///
/// # Security
/// EXIF/location metadata is stripped client-side before upload.
/// The file is analyzed only for scam content and not retained.
struct AttachmentScanRequest: Encodable {
    let fileData: Data
    let fileName: String
    let mimeType: String
    let source: String // "attachment"
}

/// Response from POST /v1/attachment.
struct AttachmentScanResponse: Decodable {
    let isScam: Bool
    let scamType: String?
    let riskScore: Double
    let exifStripped: Bool
}

// MARK: - Transcript Check

/// Request to check a call transcript for scam content.
///
/// # Security
/// The transcript is submitted as-is. The user must only submit conversations
/// they are lawfully permitted to share.
struct TranscriptCheckRequest: Encodable {
    let text: String
    let source: String // "transcript"
}

/// Response from POST /v1/call.
struct TranscriptCheckResponse: Decodable {
    let text: String?
    let isScam: Bool
    let scamType: String?
    let riskScore: Double
}

// MARK: - URL Check

/// Request to check a URL for phishing/scam content.
struct URLCheckRequest: Encodable {
    let url: String
}

/// Response from POST /v1/check/url.
struct URLCheckResponse: Decodable {
    let url: String?
    let isScam: Bool
    let scamType: String?
    let risk_score: Double
}

// MARK: - Consent Notice

/// Notice displayed before the user submits a transcript.
///
/// # Security
/// The user must acknowledge that they are lawfully permitted to share
/// the conversation before submitting.
struct ConsentNotice {
    static let consentNotice: LocalizedStringKey = "You must only submit conversations you are lawfully permitted to share. By submitting, you confirm you have the right to do so."
}
