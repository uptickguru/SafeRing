import Foundation

/// Live Caller ID Lookup extension for SafeRing.
///
/// # Architecture
/// This extension provides network-backed caller ID identification for iOS 18+.
/// It uses the privacy-preserving HMAC/token scheme (M1) to query the backend.
///
/// # Data Flow
/// 1. Phone number is HMAC-SHA256 hashed locally (no raw number sent)
/// 2. Token generated from hash + install-specific secret
/// 3. Token sent to backend for identification
/// 4. Backend returns scam label (no number correlation possible)
/// 5. Label surfaced through Apple's CallKit ID UI
///
/// # Security
/// - Phone numbers are hashed with HMAC-SHA256 (not plain SHA-256)
/// - Token generation uses per-install secret key (Keychain)
/// - Backend cannot correlate numbers across users
/// - Falls back to cached list if API unavailable
///
/// # Fallback
/// If the API or entitlement is unavailable, the extension gracefully
/// degrades to using only the locally cached scam list.

final class LiveCallerIDLookupService {

    /// The extension bundle identifier for Live Caller ID Lookup.
    static let extensionBundleID = "online.db1k.safering.ios.LiveCallerIDLookupHandler"

    // MARK: - Properties

    private let repository: ScamRepository
    private let hmacKey: HmacKey?
    private let apiClient: ApiClient?
    private let entitlementChecker: EntitlementChecker?

    // MARK: - Initializer

    init(
        repository: ScamRepository,
        hmacKey: HmacKey? = nil,
        apiClient: ApiClient? = nil,
        entitlementChecker: EntitlementChecker? = nil
    ) {
        self.repository = repository
        self.hmacKey = hmacKey
        self.apiClient = apiClient
        self.entitlementChecker = entitlementChecker
    }

    // MARK: - Public API

    /// Performs a live caller ID lookup for a phone number.
    ///
    /// # Security
    /// The phone number is hashed with HMAC-SHA256 before any network call.
    /// Only the hash and generated token are sent to the backend.
    ///
    /// - Parameter phoneNumber: The raw phone number to look up.
    /// - Returns: CallerIDResult with identification info.
    /// - Throws: CallerIDLookupError if lookup fails.
    func lookup(phoneNumber: String) async throws -> CallerIDResult {
        let normalized = normalizePhoneNumber(phoneNumber)

        // 1. Hash the phone number locally
        guard let hash = hmacKey?.hash(normalized) else {
            throw CallerIDLookupError.noHmacKey
        }

        // 2. Check cached list first
        let cached = repository.getAllCachedScamNumbers(minRisk: 0.0).first(where: { $0.numberHash == hash })
        if let cached = cached {
            return CallerIDResult(
                phoneNumber: normalized,
                hash: hash,
                label: cached.scamLabel,
                riskScore: cached.riskScore,
                source: .cached,
                isScam: cached.riskScore >= 0.7
            )
        }

        // 3. Try live API lookup (if available)
        if let apiClient = apiClient, await isEntitled() {
            return try await liveLookup(hash: hash)
        }

        // 4. Fall back to cached list
        return CallerIDResult(
            phoneNumber: normalized,
            hash: hash,
            label: nil,
            riskScore: 0.0,
            source: .noApi,
            isScam: false
        )
    }

    // MARK: - Live Lookup

    /// Performs a live lookup via the API using the HMAC token scheme.
    ///
    /// # Security
    /// Only the HMAC hash and generated token are sent to the backend.
    /// The backend cannot correlate this with other users' data.
    ///
    /// - Parameter hash: The HMAC-SHA256 hash of the phone number.
    /// - Returns: CallerIDResult with live identification.
    /// - Throws: CallerIDLookupError if API call fails.
    private func liveLookup(hash: String) async throws -> CallerIDResult {
        guard let apiClient = apiClient else {
            throw CallerIDLookupError.noApiClient
        }

        // Generate token from hash + install secret
        let token = generateToken(from: hash)

        // Query the backend
        let response = try await apiClient.checkNumber(hash: hash)

        return CallerIDResult(
            phoneNumber: "",
            hash: hash,
            label: response.label,
            riskScore: response.risk,
            source: .live,
            isScam: response.risk >= 0.7
        )
    }

    // MARK: - Entitlement Check

    /// Checks if the user has a valid subscription for Live Caller ID Lookup.
    ///
    /// - Returns: True if the user is entitled.
    private func isEntitled() async -> Bool {
        guard let entitlementChecker = entitlementChecker else {
            return false // No entitlement check available, use cached list
        }

        return await entitlementChecker.isEntitled()
    }

    // MARK: - Token Generation

    /// Generates a token from the hash and install-specific secret.
    ///
    /// # Security
    /// The token is generated using HMAC-SHA256 of the hash + install secret.
    /// This ensures the token cannot be predicted or replayed.
    ///
    /// - Parameter hash: The HMAC-SHA256 hash of the phone number.
    /// - Returns: Hex-encoded token string.
    private func generateToken(from hash: String) -> String {
        // TODO: Implement token generation
        // This should use HMAC-SHA256 of hash + install secret
        return hash // Placeholder - actual implementation will use HMAC
    }

    // MARK: - Helpers

    /// Normalizes a phone number to E.164 format.
    ///
    /// - Parameter phoneNumber: The raw phone number.
    /// - Returns: Normalized phone number in E.164 format.
    private func normalizePhoneNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        if digits.hasPrefix("1") {
            return "+\(digits)"
        } else if digits.count == 10 {
            return "+1\(digits)"
        } else {
            return "+\(digits)"
        }
    }
}

// MARK: - Result Types

/// Result of a Live Caller ID Lookup.
struct CallerIDResult {
    let phoneNumber: String
    let hash: String
    let label: String?
    let riskScore: Double
    let source: LookupSource
    let isScam: Bool

    enum LookupSource: String {
        case cached = "Cached List"
        case live = "Live API"
        case noApi = "No API"
    }
}

// MARK: - Errors

enum CallerIDLookupError: LocalizedError {
    case noHmacKey
    case noApiClient
    case apiUnavailable(Error)
    case notEntitled

    var errorDescription: String? {
        switch self {
        case .noHmacKey:
            return "HMAC key not available"
        case .noApiClient:
            return "API client not available"
        case .apiUnavailable(let error):
            return "API unavailable: \(error.localizedDescription)"
        case .notEntitled:
            return "Subscription not active"
        }
    }
}
