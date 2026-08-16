import Foundation
import Security

/// Bridge to the backend key server for HMAC key provisioning.
///
/// # Architecture
/// The HMAC-SHA256 keys used for phone number pseudonymization are
/// provisioned by the Go backend key server (HSM-backed). This bridge
/// handles provisioning, rotation, and secure storage (Keychain).
///
/// # Current State
/// This is a **bridge stub**. The Go backend key server endpoints
/// (`/v1/keys/provision`, `/v1/keys/rotate`, `/v1/keys/status`) are
/// not yet implemented. Until then, a development fallback key is used.
///
/// See: docs/KEY_SERVER_BRIDGE.md
///
final class KeyServerBridge {

    static let shared = KeyServerBridge()

    private let apiClient: ApiClient
    private let keychainService = "online.db1k.SafeRing"
    private let keychainAccount = "hmac-key"

    init(apiClient: ApiClient? = nil) {
        self.apiClient = apiClient ?? ApiClient()
    }

    // MARK: - Public API

    /// Provision a new HMAC key from the backend key server.
    /// Stores in Keychain on success.
    ///
    /// - Returns: The provisioned HmacKey.
    /// - Throws: KeyServerError if provisioning fails.
    func provisionKey() async throws -> HmacKey {
        // TODO: Call POST /v1/keys/provision when Go backend is ready
        // For now, use development fallback
        Logger.shared.warning("KeyServerBridge.provisionKey() — using dev fallback (backend not ready)", category: .security)

        let devKey = Data("dev-hmac-key-do-not-use-in-production".utf8)
        let key = HmacKey(provisionedKey: devKey)
        try storeInKeychain(key: devKey)
        return key
    }

    /// Rotate the current HMAC key.
    ///
    /// - Returns: The new HmacKey.
    /// - Throws: KeyServerError if rotation fails.
    func rotateKey() async throws -> HmacKey {
        // TODO: Call POST /v1/keys/rotate when Go backend is ready
        Logger.shared.warning("KeyServerBridge.rotateKey() — not yet implemented", category: .security)
        throw KeyServerError.notImplemented
    }

    /// Check key status with the backend.
    ///
    /// - Returns: Key status information.
    func checkKeyStatus() async throws -> KeyStatus {
        // TODO: Call GET /v1/keys/status when Go backend is ready
        Logger.shared.warning("KeyServerBridge.checkKeyStatus() — not yet implemented", category: .security)
        throw KeyServerError.notImplemented
    }

    /// Load key from Keychain, provisioning if absent.
    ///
    /// This is the main entry point for getting a usable HMAC key.
    /// It first tries Keychain, then provisions from the backend.
    ///
    /// - Returns: A usable HmacKey.
    func loadOrProvisionKey() async throws -> HmacKey {
        // Try Keychain first
        if let keyData = loadFromKeychain() {
            Logger.shared.info("HMAC key loaded from Keychain", category: .security)
            return HmacKey(provisionedKey: keyData)
        }

        // Provision from backend
        return try await provisionKey()
    }

    // MARK: - Keychain Operations

    /// Store key data in iOS Keychain.
    private func storeInKeychain(key: Data) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecValueData as String: key,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]

        // Delete existing key first
        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeyServerError.keychainWriteFailed(status)
        }

        Logger.shared.info("HMAC key stored in Keychain", category: .security)
    }

    /// Load key data from iOS Keychain.
    private func loadFromKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }

        return data
    }

    /// Delete key from Keychain (for testing/rotation).
    func deleteKey() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount
        ]
        SecItemDelete(query as CFDictionary)
    }
}

// MARK: - Models

struct KeyStatus: Codable {
    let keyId: String
    let algorithm: String
    let expiresAt: String
    let status: String // "active", "expired", "revoked"
}

// MARK: - Errors

enum KeyServerError: LocalizedError {
    case notImplemented
    case keychainWriteFailed(OSStatus)
    case provisioningFailed(Error)
    case invalidKeyMaterial

    var errorDescription: String? {
        switch self {
        case .notImplemented:
            return "Key server bridge not yet implemented — backend endpoints pending"
        case .keychainWriteFailed(let status):
            return "Keychain write failed (OSStatus: \(status))"
        case .provisioningFailed(let error):
            return "Key provisioning failed: \(error.localizedDescription)"
        case .invalidKeyMaterial:
            return "Invalid key material received from server"
        }
    }
}
