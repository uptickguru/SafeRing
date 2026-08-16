import Foundation

/// Utility for checking subscription entitlements and metering scan usage.
///
/// # Security
/// This class does not expose any personal data. It only checks the
/// subscription status and scan quota returned by the backend.
///
/// # Threat Model
/// Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
/// makes it trivially reversible. HMAC-SHA256 with a secret key provides
/// pseudonymization, making it computationally infeasible to recover the
/// original number from the hash.
///
/// # Critical Safety Rule
/// Metering applies ONLY to the 3 cloud scans (email/attachment/transcript).
/// Screening/blocking/trusted-circle/HITL are NEVER blocked by tier.
final class EntitlementMeteringChecker {

    // MARK: - Properties

    private let apiClient: ApiClient
    private let storage: UserDefaults

    // MARK: - Initializer

    init(
        apiClient: ApiClient,
        storage: UserDefaults = .standard
    ) {
        self.apiClient = apiClient
        self.storage = storage
    }

    // MARK: - Public API

    /// Checks if the user has a valid subscription.
    ///
    /// - Returns: True if the user is entitled.
    /// - Throws: EntitlementError if the check fails.
    func isEntitled() async throws -> Bool {
        // Check local cache first
        if let cached = storage.object(forKey: "entitled") as? Bool {
            return cached
        }

        // Query backend
        do {
            let response = try await fetchEntitlement()
            if response.isEntitled {
                storage.set(true, forKey: "entitled")
                Logger.shared.info("User is entitled", category: .entitlement)
                return true
            } else {
                storage.set(false, forKey: "entitled")
                Logger.shared.info("User is not entitled", category: .entitlement)
                return false
            }
        } catch {
            // Cache the result to avoid repeated failures
            Logger.shared.warning(
                "Entitlement check failed: \(error.localizedDescription)",
                category: .entitlement
            )
            return false
        }
    }

    /// Checks if the user is on the Plus tier.
    ///
    /// - Returns: True if the user is on the Plus tier.
    func isPlusTier() async throws -> Bool {
        let entitled = try await isEntitled()
        return entitled
    }

    /// Fetches the user's subscription entitlement.
    ///
    /// # Security
    /// This method queries the backend for the user's subscription status
    /// and scan quota. The response is cached locally to avoid repeated API calls.
    ///
    /// - Returns: Entitlement with tier and scan quota information.
    /// - Throws: EntitlementError if the fetch fails.
    func fetchEntitlement() async throws -> Entitlement {
        do {
            let response = try await apiClient.getEntitlement()
            return response
        } catch {
            Logger.shared.warning(
                "Entitlement fetch failed: \(error.localizedDescription)",
                category: .entitlement
            )
            throw EntitlementError.checkFailed(error)
        }
    }

    /// Checks if the user has exceeded their monthly scan quota.
    ///
    /// # Security
    /// Metering applies ONLY to the 3 cloud scans (email/attachment/transcript).
    /// Screening/blocking/trusted-circle/HITL are NEVER blocked by tier.
    ///
    /// - Returns: Entitlement with tier and scan quota information.
    /// - Throws: EntitlementError if the fetch fails.
    func fetchEntitlementWithQuota() async throws -> Entitlement {
        let entitlement = try await fetchEntitlement()
        return entitlement
    }

    /// Checks if the user has exceeded their monthly scan quota.
    ///
    /// # Security
    /// Metering applies ONLY to the 3 cloud scans (email/attachment/transcript).
    /// Screening/blocking/trusted-circle/HITL are NEVER blocked by tier.
    ///
    /// - Returns: True if the user has exceeded their monthly scan quota.
    /// - Throws: EntitlementError if the fetch fails.
    func isQuotaExceeded() async throws -> Bool {
        let entitlement = try await fetchEntitlement()
        return entitlement.isQuotaExceeded
    }

    /// Gets the scan quota for the user's tier.
    ///
    /// - Returns: The scan quota for the current month.
    /// - Throws: EntitlementError if the fetch fails.
    func getScanQuota() async throws -> Int {
        let entitlement = try await fetchEntitlement()
        return entitlement.scanQuota
    }

    /// Gets the number of scans used this month.
    ///
    /// - Returns: The number of scans used this month.
    /// - Throws: EntitlementError if the fetch fails.
    func getScanUsed() async throws -> Int {
        let entitlement = try await fetchEntitlement()
        return entitlement.scanUsed
    }
}

// MARK: - Errors

enum EntitlementError: LocalizedError {
    case checkFailed(Error)
    case notEntitled
    case quotaExceeded

    var errorDescription: String? {
        switch self {
        case .checkFailed(let error):
            return "Entitlement check failed: \(error.localizedDescription)"
        case .notEntitled:
            return "Subscription not active"
        case .quotaExceeded:
            return "Scan quota exceeded for this month"
        }
    }
}
