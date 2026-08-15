import Foundation
import CryptoKit
import Security

/// HMAC-SHA256 hashing utility for phone numbers.
///
/// # Security
/// Phone numbers are hashed with HMAC-SHA256 using a per-install secret key.
/// This key is provisioned by the backend at enrollment and stored securely:
/// - iOS: Keychain
/// - Android: Keystore
///
/// # Threat Model
/// Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
/// makes it trivially reversible. HMAC-SHA256 with a secret key provides
/// pseudonymization, making it computationally infeasible to recover the
/// original number from the hash.
///
/// # Long-Term Path (TODO)
/// This approach uses a server-provisioned key, meaning the server can still
/// correlate phone numbers with their hashes. The preferred long-term solution
/// is iOS Live Caller ID Lookup over oblivious HTTP (see M2), where the number
/// never leaves the device in a correlatable form.
///
/// # Usage
/// ```swift
/// // Key provisioned at enrollment by backend
/// let hmacKey = HmacKey(provisionedKey: "your-provisioned-key")
/// let hash = hmacKey.hash("+15551234567")
/// // hash = "a3b5c7d9..." (hex-encoded HMAC-SHA256)
/// ```
///
final class HmacHashUtils {

    /// Computes the HMAC-SHA256 hash of a phone number string.
    ///
    /// - Parameter input: The phone number string to hash (normalized E.164 format).
    /// - Parameter key: The HMAC key to use (must be provisioned by backend).
    /// - Returns: Hex-encoded HMAC-SHA256 digest string.
    static func hmacSHA256(_ input: String, key: Data) -> String {
        let inputData = Data(input.utf8)
        let hmac = HMAC<SHA256>.init(key: key)
        let hashed = hmac.calculate(for: inputData)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }

    /// Computes the HMAC-SHA256 hash of a Data buffer.
    /// Useful for hashing binary data (not currently used for phone numbers).
    ///
    /// - Parameter data: The data to hash.
    /// - Parameter key: The HMAC key to use.
    /// - Returns: Hex-encoded HMAC-SHA256 digest string.
    static func hmacSHA256(data: Data, key: Data) -> String {
        let hmac = HMAC<SHA256>.init(key: key)
        let hashed = hmac.calculate(for: data)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }

    /// Verifies that a string matches a known HMAC hash.
    /// Used for testing and validation.
    ///
    /// - Parameters:
    ///   - input: The original string.
    ///   - hash: The expected HMAC hash.
    ///   - key: The HMAC key to use.
    /// - Returns: True if the input hashes to the expected value.
    static func verify(_ input: String, hash: String, key: Data) -> Bool {
        return hmacSHA256(input, key: key) == hash
    }
}

// MARK: - HMAC Key Management

/// Represents an HMAC key for phone number hashing.
/// Keys are provisioned by the backend at enrollment and stored in Keychain/Keystore.
final class HmacKey {

    /// The provisioned key bytes.
    private let keyData: Data

    /// Whether this key is valid and ready to use.
    private var isValid: Bool = false

    /// Whether this key has been validated.
    private var hasValidated: Bool = false

    /// Initialize with a provisioned key.
    ///
    /// - Parameter provisionedKey: The raw key bytes from the backend.
    init(provisionedKey: Data) {
        self.keyData = provisionedKey
        self.isValid = true
        self.hasValidated = false
    }

    /// Initialize with a Keychain-stored key (iOS only).
    ///
    /// - Parameter serviceName: The Keychain service name.
    /// - Parameter accountName: The Keychain account name.
    /// - Returns: True if the key was loaded successfully.
    static func loadFromKeychain(serviceName: String, accountName: String) -> HmacKey? {
        // TODO: Implement Keychain loading
        // This is a placeholder for the actual Keychain implementation
        return nil
    }

    /// Initialize with a Keystore-stored key (Android only).
    ///
    /// - Parameter alias: The Keystore alias.
    /// - Returns: True if the key was loaded successfully.
    static func loadFromKeystore(alias: String) -> HmacKey? {
        // TODO: Implement Keystore loading
        // This is a placeholder for the actual Keystore implementation
        return nil
    }

    /// Hash a phone number using this key.
    ///
    /// - Parameter input: The phone number string to hash.
    /// - Returns: Hex-encoded HMAC-SHA256 digest string.
    func hash(_ input: String) -> String {
        HmacHashUtils.hmacSHA256(input, key: keyData)
    }

    /// Hash a phone number using this key.
    ///
    /// - Parameter data: The data to hash.
    /// - Returns: Hex-encoded HMAC-SHA256 digest string.
    func hash(_ data: Data) -> String {
        HmacHashUtils.hmacSHA256(data: data, key: keyData)
    }
}

// MARK: - String Extension

extension String {
    /// Returns the HMAC-SHA256 hash of this string using the given key.
    ///
    /// Example:
    /// ```swift
    /// let key = HmacKey(provisionedKey: Data(hex: "your-provisioned-key"))
    /// let hash = key.hash("+15551234567")
    /// ```
    func hmacHash(key: HmacKey) -> String {
        key.hash(self)
    }
}