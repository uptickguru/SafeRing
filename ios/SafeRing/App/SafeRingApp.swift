import SwiftUI
import BackgroundTasks
import SwiftData

/// SafeRing — AI-powered scam call/sms detection for seniors.
///
/// # Zero PII Policy
/// Phone numbers are **never** sent over the network in plain text.
/// They are hashed using SHA-256 before any API call.
/// Audio processing is entirely on-device and never transmitted.
/// No accounts, no login, no personal data collection.
///
/// # Architecture
/// - SwiftUI with SwiftData for local persistence
/// - CallKit for incoming call screening
/// - CoreML for on-device number and SMS text classification
/// - BackgroundTasks for periodic data sync
/// - URLSession for anonymous API queries (hashed numbers only)
///
@main
struct SafeRingApp: App {

    // MARK: - SwiftData Container

    /// The shared model container for all local persistence.
    /// Stores scam numbers, call logs, and SMS logs.
    /// Public access allows view models and other components to create
    /// their own contexts for background operations.
    static let sharedModelContainer: ModelContainer = {
        let schema = Schema([
            ScamNumber.self,
            CallLog.self,
            SmsLog.self,
        ])
        let modelConfiguration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: false,
            allowsSave: true
        )
        do {
            return try ModelContainer(
                for: schema,
                configurations: [modelConfiguration]
            )
        } catch {
            // Fatal: app cannot function without data persistence.
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }()

    // MARK: - App Storage

    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @AppStorage("protectionEnabled") private var protectionEnabled = true

    @Environment(\.scenePhase) private var scenePhase

    // MARK: - Initializer

    init() {
        registerBackgroundTasks()
        // Schedule weekly retention summary if user completed onboarding
        if UserDefaults.standard.bool(forKey: "hasCompletedOnboarding") {
            WeeklySummaryManager.schedule()
        }
    }

    // MARK: - Body

    var body: some Scene {
        WindowGroup {
            if hasCompletedOnboarding {
                ContentView()
                    .modelContainer(Self.sharedModelContainer)
                    
                    .onChange(of: scenePhase) { _, newPhase in
                        handleScenePhaseChange(newPhase)
                    }
            } else {
                OnboardingView(onComplete: {
                    hasCompletedOnboarding = true
                })
            }
        }
    }

    // MARK: - Background Tasks

    /// Registers the background task identifiers declared in Info.plist.
    /// Called once on app launch so the system knows about them.
    private func registerBackgroundTasks() {
        let registered = BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "online.db1k.safering.ios.sync-scam-data",
            using: nil
        ) { task in
            handleSyncTask(task as! BGProcessingTask)
        }
        Logger.shared.info(
            "Background sync task registered: \(registered)",
            category: .background
        )
    }

    /// Schedules the next background sync and handles expiration.
    private func handleSyncTask(_ task: BGProcessingTask) {
        let repo = ScamRepository(
            apiClient: ApiClient(),
            scamStore: ScamStore(modelContext: Self.sharedModelContainer.mainContext)
        )
        let syncUseCase = SyncScamDataUseCase(repository: repo)

        let cancellable = Task {
            do {
                try await syncUseCase.execute()
                task.setTaskCompleted(success: true)
            } catch {
                Logger.shared.error(
                    "Background sync failed: \(error.localizedDescription)",
                    category: .background
                )
                task.setTaskCompleted(success: false)
            }
            scheduleNextSync()
        }

        task.expirationHandler = {
            cancellable.cancel()
            Logger.shared.warning(
                "Background sync expired before completion",
                category: .background
            )
        }
    }

    /// Schedules the next background sync for 6 hours from now.
    private func scheduleNextSync() {
        let request = BGProcessingTaskRequest(
            identifier: "online.db1k.safering.ios.sync-scam-data"
        )
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            Logger.shared.error(
                "Failed to schedule background sync: \(error.localizedDescription)",
                category: .background
            )
        }
    }

    /// Handles app lifecycle for background task scheduling.
    private func handleScenePhaseChange(_ phase: ScenePhase) {
        switch phase {
        case .active:
            // Schedule the first sync if not already scheduled
            Task { @MainActor in
                scheduleNextSync()
            }
        case .background:
            scheduleNextSync()
        default:
            break
        }
    }
}
