import Foundation
import BackgroundTasks

/// Background task for syncing scam data to the shared container.
///
/// # Architecture
/// This background task periodically syncs scam data from the local SwiftData store
/// to the shared app group container. The CallDirectory extension reads from the
/// shared container, not the local store.
///
/// # Schedule
/// - Initial sync: On app launch
/// - Periodic sync: Every 6 hours (configurable)
/// - Triggered sync: When new scam data is added
///
/// # Security
/// - Only HMAC-SHA256 hashed numbers are synced
/// - No raw phone numbers leave the device
/// - Sync happens in the background, not at call time
final class CallDirectoryBackgroundSync {

    // MARK: - Constants

    /// The background task identifier for the sync task.
    static let backgroundTaskIdentifier = "online.db1k.safering.ios.sync-scam-data"

    /// The shared container file path for scam data.
    static let sharedContainerPath = "scam_cache.json"

    // MARK: - Properties

    private let repository: ScamRepository
    private let callDirectoryManager: CallDirectoryManager
    private let entitlementChecker: EntitlementChecker?

    // MARK: - Initializer

    init(
        repository: ScamRepository,
        callDirectoryManager: CallDirectoryManager,
        entitlementChecker: EntitlementChecker? = nil
    ) {
        self.repository = repository
        self.callDirectoryManager = callDirectoryManager
        self.entitlementChecker = entitlementChecker
    }

    // MARK: - Public API

    /// Triggers an immediate sync of scam data to the shared container.
    ///
    /// # Security
    /// Only HMAC-SHA256 hashed numbers are synced.
    /// No raw phone numbers leave the device.
    func sync() async {
        Logger.shared.info("Starting scam data sync", category: .background)

        // 1. Get all scam numbers from the local store (MainActor-isolated)
        let scamNumbers = await MainActor.run {
            repository.getAllCachedScamNumbers(minRisk: 0.3)
        }

        // 2. Convert to shared container format
        let sharedNumbers = scamNumbers.map { scamNumber in
            ExtensionScamNumber(
                numberHash: scamNumber.numberHash,
                riskScore: scamNumber.riskScore,
                scamType: scamNumber.scamLabel
            )
        }

        // 3. Save to shared container
        try? saveToSharedContainer(sharedNumbers)

        // 4. Reload the CallDirectory extension
        try? await callDirectoryManager.reloadExtension()

        Logger.shared.info("Scam data sync complete: \(sharedNumbers.count) numbers synced", category: .background)
    }

    /// Schedules a periodic background sync task.
    ///
    /// This should be called once during app initialization.
    /// Registration is handled in SafeRingApp.registerBackgroundTasks().
    func schedulePeriodicSync() {
        let request = BGProcessingTaskRequest(identifier: Self.backgroundTaskIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)

        do {
            try BGTaskScheduler.shared.submit(request)
            Logger.shared.info("Background sync task scheduled (next run in 6h)", category: .background)
        } catch {
            Logger.shared.error("Failed to schedule background sync: \(error.localizedDescription)", category: .background)
        }
    }

    // MARK: - Private Methods

    /// Saves scam numbers to the shared container.
    ///
    /// # Security
    /// Only HMAC-SHA256 hashed numbers are synced.
    /// No raw phone numbers leave the device.
    private func saveToSharedContainer(_ numbers: [ExtensionScamNumber]) {
        // TODO: Implement shared container save
        // This should use FileManager to write to the shared app group container
    }
}

// MARK: - Extension Types

struct ExtensionScamNumber: Codable {
    let numberHash: String
    let riskScore: Double
    let scamType: String
}
