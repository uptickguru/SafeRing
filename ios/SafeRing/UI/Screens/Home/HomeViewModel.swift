import Foundation
import SwiftUI
import Combine

/// ViewModel for the Home dashboard screen.
///
/// Manages the protection status, scam stats, and handles
/// interactions with the repository and use cases.
///
@MainActor
final class HomeViewModel: ObservableObject {

    // MARK: - Published State

    /// Whether call protection is currently active.
    @Published var protectionStatus: ProtectionStatus = .checking

    /// Number of scam calls blocked this session.
    @Published var blockedCount: Int = 0

    /// Number of scam SMS messages filtered.
    @Published var smsFilteredCount: Int = 0

    /// Number of known scam numbers in the local database.
    @Published var knownScamCount: Int = 0

    /// Date of the last data sync.
    @Published var lastSyncDate: Date?

    /// Whether a sync is currently in progress.
    @Published var isSyncing: Bool = false

    /// Whether to show any error state.
    @Published var showError: Bool = false
    @Published var errorMessage: String = ""

    /// Recent call activity (last 5 entries).
    @Published var recentCalls: [CallLog] = []

    /// The current CallDirectory extension status.
    @Published var callDirectoryStatus: String = "Checking..."

    // MARK: - Dependencies

    private let repository: ScamRepository
    private let syncUseCase: SyncScamDataUseCase
    private let callDirectoryManager = CallDirectoryManager.shared

    // MARK: - Initializer

    init(
        repository: ScamRepository? = nil,
        syncUseCase: SyncScamDataUseCase? = nil
    ) {
        // Allow dependency injection for testing
        if let repo = repository {
            self.repository = repo
        } else {
            let context = SafeRingApp.sharedModelContainer.mainContext
            self.repository = ScamRepository(
                apiClient: ApiClient(),
                scamStore: ScamStore(modelContext: context)
            )
        }

        if let sync = syncUseCase {
            self.syncUseCase = sync
        } else {
            self.syncUseCase = SyncScamDataUseCase(repository: self.repository)
        }
    }

    // MARK: - Public Methods

    /// Loads the initial dashboard state.
    func loadDashboard() async {
        await checkProtectionStatus()
        await refreshStats()
        await checkCallDirectoryStatus()
    }

    /// Refreshes scam data from the server.
    func refreshData() async {
        isSyncing = true
        showError = false

        do {
            try await syncUseCase.execute()
            lastSyncDate = Date()
            await refreshStats()
            Logger.shared.info("Dashboard data refreshed successfully", category: .ui)
        } catch {
            showError = true
            errorMessage = "Unable to update: \(error.localizedDescription)"
            Logger.shared.error("Dashboard refresh failed: \(error.localizedDescription)", category: .ui)
        }

        isSyncing = false
    }

    /// Opens the CallDirectory settings so the user can enable the extension.
    func openCallDirectorySettings() {
        callDirectoryManager.openSettings()
    }

    // MARK: - Private Methods

    /// Checks whether the extension is enabled.
    private func checkCallDirectoryStatus() async {
        let status = await callDirectoryManager.getExtensionStatus()
        callDirectoryStatus = callDirectoryManager.statusDescription(for: status)
    }

    /// Refreshes the scam statistics from local cache.
    private func refreshStats() async {
        knownScamCount = repository.cachedScamNumberCount
        lastSyncDate = repository.lastSyncDate
        recentCalls = []
    }

    /// Determines the protection status based on extension availability.
    private func checkProtectionStatus() async {
        let status = await callDirectoryManager.getExtensionStatus()
        switch status {
        case .enabled:
            protectionStatus = .active
        case .disabled:
            protectionStatus = .needsSetup
        case .unknown:
            protectionStatus = .checking
        @unknown default:
            protectionStatus = .checking
        }
    }
}

// MARK: - Protection Status

extension HomeViewModel {
    enum ProtectionStatus: String {
        case active = "Protection Active"
        case needsSetup = "Needs Setup"
        case checking = "Checking..."
    }
}
