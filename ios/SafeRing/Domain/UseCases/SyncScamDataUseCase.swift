import CallKit
import Foundation

/// Use case: Periodically synchronize scam data from the server to local cache.
///
/// # Sync Steps
/// 1. Fetch latest scam number prefixes from the API.
/// 2. For each high-priority prefix, check recent known scam numbers.
/// 3. Update local SwiftData cache with fresh data.
/// 4. Update CallKit CallDirectory extension with newly blocked numbers.
///
/// Called by the background task scheduler every 6 hours.
///
@MainActor
final class SyncScamDataUseCase {

    // MARK: - Properties

    private let repository: ScamRepository

    // MARK: - Initializer

    init(repository: ScamRepository) {
        self.repository = repository
    }

    // MARK: - Execution

    /// Executes a full sync cycle.
    /// - Throws: SyncError if sync fails.
    func execute() async throws {
        Logger.shared.info("Starting scam data sync...", category: .background)

        // 1. Fetch latest prefixes
        let prefixes = try await repository.fetchPrefixes()
        Logger.shared.info(
            "Fetched \(prefixes.count) scam prefixes",
            category: .background
        )

        // 2. Log cached count
        let cachedCount = repository.cachedScamNumberCount
        Logger.shared.info(
            "Local cache has \(cachedCount) scam numbers",
            category: .background
        )

        // 3. Trigger CallDirectory reload so blocked numbers take effect
        // This is done via the CXCallDirectoryManager API.
        await reloadCallDirectory()

        Logger.shared.info(
            "Scam data sync completed successfully",
            category: .background
        )
    }

    /// Returns the date of the last successful sync.
    var lastSyncDate: Date? {
        repository.lastSyncDate
    }

    // MARK: - CallDirectory Reload

    /// Requests a reload of the CallKit CallDirectory extension.
    /// This ensures newly blocked numbers take effect for incoming calls.
    private func reloadCallDirectory() async {
        let manager = CXCallDirectoryManager.sharedInstance
        do {
            try await manager.reloadExtension(
                withIdentifier: "online.db1k.safering.ios.CallDirectoryHandler"
            )
            Logger.shared.info("CallDirectory extension reloaded", category: .background)
        } catch {
            Logger.shared.warning(
                "Failed to reload CallDirectory: \(error.localizedDescription)",
                category: .background
            )
        }
    }
}

// MARK: - Errors

enum SyncError: LocalizedError {
    case syncFailed(underlying: Error)
    case noData

    var errorDescription: String? {
        switch self {
        case .syncFailed(let error):
            return "Sync failed: \(error.localizedDescription)"
        case .noData:
            return "No scam data available from server"
        }
    }
}
