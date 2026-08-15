import Foundation

/// Utility for checking subscription entitlements.
///
/// # Usage
/// Use this to check if the user has a valid subscription for premium features.
/// Returns true if the user is entitled, false otherwise.
///
/// # Security
/// This class does not expose any personal data. It only checks the
/// subscription status returned by the backend.
final class EntitlementChecker {

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
        if let cached = storage.bool(forKey: "entitled") {
            return cached
        }

        // Query backend
        do {
            let response = try await apiClient.getEntitlement()
            if response.success {
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
}
