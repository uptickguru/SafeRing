import Foundation
import CryptoKit

/// Utility for SHA-256 hashing of phone numbers.
///
/// # Zero PII Policy
/// This is the core of SafeRing's privacy guarantee.
/// Phone numbers are hashed with SHA-256 before any network call.
/// The hash is one-way — the original number cannot be recovered.
///
/// # Usage
/// ```swift
/// let hash = HashUtils.sha256("+15551234567")
/// // hash = "e3b0c44298fc1c149afbf4c8996fb924..."
/// ```
///
enum HashUtils {

    /// Computes the SHA-256 hash of a phone number string.
    ///
    /// The input should be a normalized E.164 phone number
    /// (e.g., "+15551234567") before hashing.
    ///
    /// - Parameter input: The phone number string to hash.
    /// - Returns: Hex-encoded SHA-256 digest string.
    static func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashed = SHA256.hash(data: inputData)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }

    /// Computes the SHA-256 hash of a Data buffer.
    /// Useful for hashing binary data (not currently used for phone numbers).
    ///
    /// - Parameter data: The data to hash.
    /// - Returns: Hex-encoded SHA-256 digest string.
    static func sha256(data: Data) -> String {
        let hashed = SHA256.hash(data: data)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }

    /// Verifies that a string matches a known hash.
    /// Used for testing and validation.
    ///
    /// - Parameters:
    ///   - input: The original string.
    ///   - hash: The expected SHA-256 hash.
    /// - Returns: True if the input hashes to the expected value.
    static func verify(_ input: String, matches hash: String) -> Bool {
        return sha256(input) == hash
    }
}

// MARK: - String Extension

extension String {
    /// Returns the SHA-256 hash of this string.
    /// Convenience for hashing phone number strings.
    ///
    /// Example:
    /// ```swift
    /// let hash = "+15551234567".sha256Hash
    /// ```
    var sha256Hash: String {
        HashUtils.sha256(self)
    }
}
