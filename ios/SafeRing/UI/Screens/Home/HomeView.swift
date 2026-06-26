import SwiftUI
import SwiftData

/// Main dashboard view showing protection status and scam statistics.
///
/// # Senior-Friendly Design
/// - Large, clear status indicator at the top
/// - Big touch targets for all actions
/// - High contrast colors for risk indicators
/// - Simple, scannable statistics
///
struct HomeView: View {

    // MARK: - Properties

    @StateObject var viewModel: HomeViewModel
    @Environment(\.modelContext) private var modelContext

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: AppTheme.spacingLG) {
                // Protection Status Card
                protectionStatusCard
                    .padding(.horizontal)

                // Quick Stats Grid
                statsGrid
                    .padding(.horizontal)

                // Scam Alert Preview or Empty State
                if !viewModel.recentCalls.isEmpty {
                    recentActivitySection
                        .padding(.horizontal)
                } else {
                    emptyHomeState
                        .padding(.horizontal)
                }

                // Sync Status + Offline Warning
                syncStatusBar
                    .padding(.horizontal)

                if let lastSync = viewModel.lastSyncDate,
                   Date().timeIntervalSince(lastSync) > 24 * 3600 {
                    staleDataBanner
                        .padding(.horizontal)
                }

                // Refresh Button
                BigButton(
                    title: viewModel.isSyncing ? "Updating..." : "Check for New Scams",
                    icon: "arrow.clockwise",
                    action: {
                        Task { await viewModel.refreshData() }
                    },
                    isLoading: viewModel.isSyncing
                )
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .background(Color("appBackground"))
        .navigationTitle("SafeRing")
        .navigationBarTitleDisplayMode(.large)
        .task {
            await viewModel.loadDashboard()
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage)
        }
    }

    // MARK: - Protection Status Card

    private var protectionStatusCard: some View {
        VStack(spacing: AppTheme.spacingMD) {
            // Status Icon
            Image(systemName: statusIcon)
                .font(.system(size: 48))
                .foregroundColor(statusColor)
                .symbolEffect(.bounce, value: viewModel.protectionStatus)

            // Status Text
            Text(viewModel.protectionStatus.rawValue)
                .font(.screenTitle)
                .foregroundColor(Color("primaryText"))

            // Description
            Text(protectionDescription)
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)

            // Setup Button (if needed)
            if viewModel.protectionStatus == .needsSetup {
                BigButton(
                    title: "Enable Protection",
                    icon: "gearshape",
                    action: {
                        viewModel.openCallDirectorySettings()
                    },
                    color: AppTheme.accentColor
                )
                .padding(.top, AppTheme.spacingXS)
            }
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
        .cardShadow()
    }

    // MARK: - Stats Grid

    private var statsGrid: some View {
        LazyVGrid(
            columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible()),
            ],
            spacing: AppTheme.spacingMD
        ) {
            StatCard(
                value: "\(viewModel.blockedCount)",
                label: "Calls\nBlocked",
                icon: "phone.down.fill",
                color: Color("safeGreen")
            )
            StatCard(
                value: "\(viewModel.smsFilteredCount)",
                label: "SMS\nFiltered",
                icon: "trash.fill",
                color: Color("warningYellow")
            )
            StatCard(
                value: "\(viewModel.knownScamCount)",
                label: "Scam #\nKnown",
                icon: "shield.fill",
                color: Color("highRiskOrange")
            )
        }
    }

    // MARK: - Empty Home State

    private var emptyHomeState: some View {
        VStack(spacing: AppTheme.spacingMD) {
            Image(systemName: "shield.checkered")
                .font(.system(size: 48))
                .foregroundColor(Color("safeGreen"))

            Text("Your Protection Is Active")
                .font(.sectionTitle)
                .foregroundColor(Color("primaryText"))

            Text("SafeRing is monitoring your calls and messages. When a scam is detected, it will appear here.")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)

            if let lastSync = viewModel.lastSyncDate {
                Text("Scam database updated \(lastSync.formatted(date: .abbreviated, time: .shortened))")
                    .font(.captionText)
                    .foregroundColor(Color("safeGreen"))
            }

            HStack(spacing: AppTheme.spacingSM) {
                Image(systemName: "hand.raised.fill")
                    .foregroundColor(Color("safeGreen"))
                Text("No calls blocked yet — that\'s a good day!")
                    .font(.badgeLabel)
                    .foregroundColor(Color("secondaryText"))
            }
            .padding(.vertical, AppTheme.spacingXS)
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Recent Activity

    private var recentActivitySection: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
            Text("Recent Activity")
                .font(.sectionTitle)
                .foregroundColor(Color("primaryText"))

            ForEach(viewModel.recentCalls.prefix(5)) { call in
                CallRow(callLog: call)
            }
        }
    }

    // MARK: - Stale Data Banner

    private var staleDataBanner: some View {
        HStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "exclamationmark.icloud.fill")
                .foregroundColor(Color("warningYellow"))
            Text("Scam database hasn't been updated in 24+ hours. Connect to the internet and tap refresh to get the latest protection.")
                .font(.badgeLabel)
                .foregroundColor(Color("secondaryText"))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(AppTheme.spacingMD)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color("warningYellow").opacity(0.1))
        .cornerRadius(AppTheme.smallCornerRadius)
    }

    // MARK: - Sync Status

    private var syncStatusBar: some View {
        HStack {
            if viewModel.didJustSync {
                Image(systemName: "checkmark.icloud.fill")
                    .foregroundColor(Color("safeGreen"))
                Text("Synced")
                    .font(.captionText)
                    .foregroundColor(Color("safeGreen"))
            } else if viewModel.isSyncing {
                Image(systemName: "arrow.triangle.2.circlepath.icloud")
                    .foregroundColor(Color("secondaryText"))
                Text("Updating...")
                    .font(.captionText)
                    .foregroundColor(Color("secondaryText"))
            } else {
                Image(systemName: viewModel.lastSyncDate != nil ? "icloud.fill" : "icloud.slash.fill")
                    .foregroundColor(viewModel.lastSyncDate != nil ? Color("safeGreen") : Color("secondaryText"))
                Text(syncStatusText)
                    .font(.captionText)
                    .foregroundColor(viewModel.lastSyncDate != nil ? Color("safeGreen") : Color("secondaryText"))
            }
            Spacer()
        }
        .padding(.vertical, AppTheme.spacingXS)
    }

    // MARK: - Helpers

    private var statusIcon: String {
        switch viewModel.protectionStatus {
        case .active: return "checkmark.shield.fill"
        case .needsSetup: return "shield.slash.fill"
        case .checking: return "shield.lefthalf.filled"
        }
    }

    private var statusColor: Color {
        switch viewModel.protectionStatus {
        case .active: return Color("safeGreen")
        case .needsSetup: return Color("warningYellow")
        case .checking: return Color("secondaryText")
        }
    }

    private var protectionDescription: String {
        switch viewModel.protectionStatus {
        case .active:
            return "SafeRing is actively screening your calls and SMS messages for known scams."
        case .needsSetup:
            return "Tap the button below to go to Settings, then turn ON SafeRing, then come back and tap Check for New Scams."
        case .checking:
            return "Checking protection status..."
        }
    }

    private var syncStatusText: String {
        if let date = viewModel.lastSyncDate {
            return "Last updated \(date.formatted(date: .abbreviated, time: .shortened))"
        }
        return "Not yet synced"
    }
}

// MARK: - Stat Card Component

private struct StatCard: View {
    let value: String
    let label: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(spacing: AppTheme.spacingXS) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)

            Text(value)
                .font(.riskScore)
                .foregroundColor(Color("primaryText"))
                .minimumScaleFactor(0.5)

            Text(label)
                .font(.badgeLabel)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
                .lineSpacing(2)
        }
        .padding(AppTheme.spacingSM)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        HomeView(viewModel: HomeViewModel())
    }
}
