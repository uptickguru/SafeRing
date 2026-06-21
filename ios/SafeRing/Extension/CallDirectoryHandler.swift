import Foundation
import UIKit
import CallKit

/// App-side manager for the CallKit CallDirectory extension.
///
/// This class provides methods for the main app to:
/// - Request the CallDirectory extension to reload blocked/identified numbers
/// - Check the current extension status
/// - Request user permission to enable the extension
///
/// # Zero PII
/// The CallDirectory extension works with hashed numbers only.
/// This manager does not handle any raw phone numbers.
///
final class CallDirectoryManager {

    // MARK: - Properties

    /// The bundle identifier of the CallDirectory extension target.
    static let extensionBundleID = "online.db1k.safering.ios.CallDirectoryHandler"

    private let manager = CXCallDirectoryManager.sharedInstance

    // MARK: - Singleton

    static let shared = CallDirectoryManager()

    private init() {}

    // MARK: - Public Methods

    /// Requests a reload of the CallDirectory extension.
    /// This should be called after syncing new scam data.
    /// - Throws: CallDirectoryError if the reload fails.
    func reloadExtension() async throws {
        do {
            try await manager.reloadExtension(withIdentifier: Self.extensionBundleID)
            Logger.shared.info("CallDirectory extension reload requested", category: .extension)
        } catch {
            Logger.shared.error(
                "Failed to reload CallDirectory extension: \(error.localizedDescription)",
                category: .extension
            )
            throw CallDirectoryError.reloadFailed(error)
        }
    }

    /// Returns the current enabled status of the CallDirectory extension.
    /// - Returns: The enabled status.
    func getExtensionStatus() async -> CXCallDirectoryManager.EnabledStatus {
        return await withCheckedContinuation { continuation in
            manager.getEnabledStatusForExtension(
                withIdentifier: Self.extensionBundleID
            ) { status, error in
                if let error = error {
                    Logger.shared.error(
                        "Failed to get CallDirectory status: \(error.localizedDescription)",
                        category: .extension
                    )
                    continuation.resume(returning: .unknown)
                } else {
                    continuation.resume(returning: status)
                }
            }
        }
    }

    /// Opens the system Settings app to the Call Directory extension settings.
    /// Users must manually enable the extension from there on first launch.
    @MainActor
    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    /// Returns a human-readable description of the extension status.
    /// - Parameter status: The enabled status from the system.
    /// - Returns: A localized string describing the status.
    func statusDescription(for status: CXCallDirectoryManager.EnabledStatus) -> String {
        switch status {
        case .enabled:
            return "Active ✅"
        case .disabled:
            return "Needs Setup ⚙️"
        case .unknown:
            return "Checking..."
        @unknown default:
            return "Unknown"
        }
    }
}

// MARK: - Errors

enum CallDirectoryError: LocalizedError {
    case reloadFailed(Error)

    var errorDescription: String? {
        switch self {
        case .reloadFailed(let error):
            return "Failed to update call screening: \(error.localizedDescription)"
        }
    }
}
