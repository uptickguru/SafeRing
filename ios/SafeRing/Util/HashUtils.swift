import UIKit
import Foundation
import CryptoKit

/// ⚠️ DEPRECATED — Use HmacHashUtils instead.
///
/// Raw SHA-256 is NOT suitable for phone number privacy.
/// The search space (~10^10 US numbers) makes SHA-256(number) trivially
/// reversible via precomputed rainbow tables.
///
/// This file is kept ONLY for backward compatibility with any
/// non-phone-number hashing that may still reference it (e.g.,
/// generic data hashing). For phone numbers, use HmacHashUtils.
///
/// Will be removed in a future release.
///
@available(*, deprecated, renamed: "HmacHashUtils", message: "Raw SHA-256 is insecure for phone numbers. Use HmacHashUtils.hmacSHA256() instead.")
enum HashUtils {

    @available(*, deprecated, message: "Use HmacHashUtils.hmacSHA256() for phone numbers")
    static func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashed = SHA256.hash(data: inputData)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }

    @available(*, deprecated, message: "Use HmacHashUtils for sensitive data")
    static func sha256(data: Data) -> String {
        let hashed = SHA256.hash(data: data)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }
}

// MARK: - String Extension

extension String {
    @available(*, deprecated, message: "Use HmacHashUtils.hmacSHA256() for phone numbers")
    var sha256Hash: String {
        HashUtils.sha256(self)
    }
}

