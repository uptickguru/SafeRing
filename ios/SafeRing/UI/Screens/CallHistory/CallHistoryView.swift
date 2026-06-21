import SwiftUI
import SwiftData

/// View showing the user's call history with scam risk indicators.
///
/// Displays incoming and outgoing calls with color-coded risk badges,
/// allowing seniors to quickly identify suspicious activity.
///
struct CallHistoryView: View {

    // MARK: - Properties

    @Environment(\.modelContext) private var modelContext
    @Query(
        sort: \CallLog.timestamp,
        order: .reverse
    ) private var callLogs: [CallLog]

    @State private var selectedFilter: FilterOption = .all

    // MARK: - Filter Options

    enum FilterOption: String, CaseIterable {
        case all = "All"
        case suspicious = "Suspicious"
        case blocked = "Blocked"
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            // Filter Picker
            Picker("Filter", selection: $selectedFilter) {
                ForEach(FilterOption.allCases, id: \.self) { option in
                    Text(option.rawValue).tag(option)
                }
            }
            .pickerStyle(.segmented)
            .padding()

            // Call List
            if filteredLogs.isEmpty {
                emptyState
            } else {
                List {
                    ForEach(filteredLogs) { log in
                        CallRow(callLog: log)
                            .listRowInsets(EdgeInsets(
                                top: AppTheme.spacingXS,
                                leading: AppTheme.spacingMD,
                                bottom: AppTheme.spacingXS,
                                trailing: AppTheme.spacingMD
                            ))
                    }
                    .onDelete(perform: deleteLogs)
                }
                .listStyle(.plain)
            }
        }
        .background(Color("appBackground"))
        .navigationTitle("Call History")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if !callLogs.isEmpty {
                    EditButton()
                        .font(.bodyText)
                }
            }
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: AppTheme.spacingMD) {
            Spacer()

            Image(systemName: "phone.badge.checkmark")
                .font(.system(size: 64))
                .foregroundColor(Color("safeGreen"))

            Text(emptyTitle)
                .font(.screenTitle)
                .foregroundColor(Color("primaryText"))

            Text(emptyMessage)
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppTheme.spacingXL)

            Spacer()
        }
    }

    // MARK: - Helpers

    private var filteredLogs: [CallLog] {
        switch selectedFilter {
        case .all:
            return callLogs
        case .suspicious:
            return callLogs.filter { $0.riskScore >= 0.3 }
        case .blocked:
            return callLogs.filter { $0.screeningResult == .blocked }
        }
    }

    private var emptyTitle: String {
        switch selectedFilter {
        case .all: return "No Call History"
        case .suspicious: return "No Suspicious Calls"
        case .blocked: return "No Blocked Calls"
        }
    }

    private var emptyMessage: String {
        switch selectedFilter {
        case .all:
            return "Your call history will appear here. SafeRing automatically screens incoming calls for scam risks."
        case .suspicious:
            return "Great news! No suspicious calls detected. SafeRing is working to keep you protected."
        case .blocked:
            return "No calls have been blocked yet. SafeRing will automatically block known scam numbers."
        }
    }

    private func deleteLogs(at offsets: IndexSet) {
        for index in offsets {
            let log = filteredLogs[index]
            modelContext.delete(log)
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        CallHistoryView()
    }
}
