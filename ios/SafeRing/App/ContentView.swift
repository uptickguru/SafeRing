import SwiftUI
import SwiftData
import BackgroundTasks

/// Root content view for SafeRing app.
/// Manages tab-based navigation between the main dashboard, call history,
/// settings, and scam reporting.
struct ContentView: View {

    // MARK: - Properties

    @State private var selectedTab: Tab = .home
    @State private var showScamAlert: ScamAlertInfo? = nil

    /// Shared scam repository for cross-view access.
    @StateObject private var homeViewModel = HomeViewModel()

    // MARK: - Tab Definition

    enum Tab: String, CaseIterable {
        case home = "Home"
        case history = "History"
        case report = "Report"
        case settings = "Settings"

        var icon: String {
            switch self {
            case .home: return "shield.fill"
            case .history: return "phone.fill"
            case .report: return "exclamationmark.bubble.fill"
            case .settings: return "gearshape.fill"
            }
        }

        var label: String { rawValue }
    }

    // MARK: - Body

    var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                NavigationStack {
                    HomeView(viewModel: homeViewModel)
                }
                .tabItem {
                    Label(Tab.home.label, systemImage: Tab.home.icon)
                }
                .tag(Tab.home)

                NavigationStack {
                    CallHistoryView()
                }
                .tabItem {
                    Label(Tab.history.label, systemImage: Tab.history.icon)
                }
                .tag(Tab.history)

                NavigationStack {
                    ReportView()
                }
                .tabItem {
                    Label(Tab.report.label, systemImage: Tab.report.icon)
                }
                .tag(Tab.report)

                NavigationStack {
                    SettingsView()
                }
                .tabItem {
                    Label(Tab.settings.label, systemImage: Tab.settings.icon)
                }
                .tag(Tab.settings)
            }
            .tint(AppTheme.accentColor)

            // Full-screen scam alert overlay
            if let alert = showScamAlert {
                ScamAlertView(
                    riskLabel: alert.label,
                    riskScore: alert.score,
                    callerName: alert.callerName,
                    scamType: alert.scamType,
                    onDismiss: {
                        withAnimation(.easeOut(duration: 0.3)) {
                            showScamAlert = nil
                        }
                    },
                    onBlock: {
                        // Block would update CallDirectory extension
                        withAnimation(.easeOut(duration: 0.3)) {
                            showScamAlert = nil
                        }
                    }
                )
                .transition(.opacity.combined(with: .scale))
                .zIndex(100)
            }
        }
    }
}

// MARK: - Scam Alert Info

/// Transient payload for showing full-screen scam alerts from any tab.
struct ScamAlertInfo: Identifiable {
    let id = UUID()
    let label: String
    let score: Double
    let callerName: String
    let scamType: String
}

// MARK: - Preview

#Preview {
    ContentView()
}
