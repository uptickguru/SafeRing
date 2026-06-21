import SwiftUI

/// One-tap scam reporting screen.
///
/// Allows users to quickly report a suspicious phone number.
/// The report is anonymized — only the SHA-256 hash and scam type
/// are submitted to the server.
///
/// # Zero PII
/// - Phone numbers are hashed before submission.
/// - Scam type tags are categorical, not personal.
/// - No location, device ID, or contact data is included.
///
struct ReportView: View {

    // MARK: - State

    @State private var phoneNumber = ""
    @State private var selectedScamType: ScamType = .general
    @State private var isSubmitting = false
    @State private var showConfirmation = false
    @State private var errorMessage: String?
    @State private var showError = false

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: AppTheme.spacingLG) {
                // Header
                headerSection
                    .padding(.top)

                // Phone Number Input
                phoneNumberSection

                // Scam Type Picker
                scamTypeSection

                // Submit Button
                BigButton(
                    title: isSubmitting ? "Reporting..." : "Report Scam Number",
                    icon: "exclamationmark.shield.fill",
                    action: submitReport,
                    isLoading: isSubmitting,
                    color: Color("criticalRed")
                )

                // Explanation
                explanationSection

                Spacer()
            }
            .padding(.horizontal)
        }
        .background(Color("appBackground"))
        .navigationTitle("Report a Scam")
        .navigationBarTitleDisplayMode(.large)
        .alert("Report Submitted", isPresented: $showConfirmation) {
            Button("Thank You!", role: .cancel) {
                resetForm()
            }
        } message: {
            Text("Your report helps protect others in your community. The scam number has been added to our database. 🛡️")
        }
        .alert("Report Failed", isPresented: $showError) {
            Button("Try Again", role: .cancel) { }
        } message: {
            Text(errorMessage ?? "Unable to submit report. Please check your connection and try again.")
        }
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "exclamationmark.bubble.fill")
                .font(.system(size: 48))
                .foregroundColor(Color("criticalRed"))

            Text("Report a Scam Call or Text")
                .font(.screenTitle)
                .foregroundColor(Color("primaryText"))
                .multilineTextAlignment(.center)

            Text("Help protect your community by reporting suspicious numbers. Your report is anonymous.")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Phone Number Input

    private var phoneNumberSection: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
            Text("Suspicious Phone Number")
                .font(.sectionTitle)
                .foregroundColor(Color("primaryText"))

            TextField("+1 (555) 123-4567", text: $phoneNumber)
                .font(.phoneNumber)
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .padding()
                .background(Color("cardBackground"))
                .cornerRadius(AppTheme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                        .stroke(phoneNumber.isEmpty ? Color.clear : AppTheme.accentColor, lineWidth: 2)
                )

            if !phoneNumber.isEmpty && !isValidPhoneNumber {
                Text("Please enter a complete phone number with area code")
                    .font(.captionText)
                    .foregroundColor(Color("warningYellow"))
            }
        }
    }

    // MARK: - Scam Type

    private var scamTypeSection: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
            Text("Type of Scam")
                .font(.sectionTitle)
                .foregroundColor(Color("primaryText"))

            LazyVGrid(
                columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                ],
                spacing: AppTheme.spacingSM
            ) {
                ForEach(ScamType.allCases, id: \.self) { type in
                    Button {
                        selectedScamType = type
                    } label: {
                        VStack(spacing: AppTheme.spacingXS) {
                            Image(systemName: type.icon)
                                .font(.title3)
                            Text(type.rawValue)
                                .font(.badgeLabel)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(AppTheme.spacingSM)
                        .background(
                            selectedScamType == type
                                ? AppTheme.accentColor.opacity(0.15)
                                : Color("cardBackground")
                        )
                        .cornerRadius(AppTheme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                                .stroke(
                                    selectedScamType == type ? AppTheme.accentColor : Color.clear,
                                    lineWidth: 2
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Explanation

    private var explanationSection: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
            Label("Your Privacy is Protected", systemImage: "lock.shield.fill")
                .font(.bodyTextEmphasized)
                .foregroundColor(Color("safeGreen"))

            VStack(alignment: .leading, spacing: 6) {
                PrivacyRow(text: "Your phone number is never shared")
                PrivacyRow(text: "Only the scam number is reported")
                PrivacyRow(text: "The number is hashed before sending")
                PrivacyRow(text: "No personal information is collected")
                PrivacyRow(text: "No account or login required")
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Validation

    private var isValidPhoneNumber: Bool {
        let digits = phoneNumber.filter { $0.isNumber }
        return digits.count >= 10
    }

    // MARK: - Actions

    private func submitReport() {
        guard isValidPhoneNumber else { return }

        isSubmitting = true
        let rawNumber = phoneNumber
        let scamType = selectedScamType

        Task {
            do {
                let context = SafeRingApp.sharedModelContainer.mainContext
                let repo = ScamRepository(
                    apiClient: ApiClient(),
                    scamStore: ScamStore(modelContext: context)
                )
                let useCase = ReportScamUseCase(repository: repo)
                _ = try await useCase.execute(rawNumber: rawNumber, scamTag: scamType.rawValue)

                await MainActor.run {
                    isSubmitting = false
                    showConfirmation = true
                }
            } catch {
                await MainActor.run {
                    isSubmitting = false
                    errorMessage = error.localizedDescription
                    showError = true
                }
            }
        }
    }

    private func resetForm() {
        phoneNumber = ""
        selectedScamType = .general
    }
}

// MARK: - Scam Type Enum

extension ReportView {
    enum ScamType: String, CaseIterable {
        case general = "General Scam"
        case irs = "IRS / Tax"
        case techSupport = "Tech Support"
        case grandparent = "Grandparent"
        case romance = "Romance"
        case phishing = "Phishing"
        case package = "Package Delivery"
        case investment = "Investment"
        case medicare = "Medicare"
        case other = "Other"

        var icon: String {
            switch self {
            case .general: return "exclamationmark.shield"
            case .irs: return "building.columns"
            case .techSupport: return "desktopcomputer"
            case .grandparent: return "figure.2.and.child.holdinghands"
            case .romance: return "heart.slash"
            case .phishing: return "antenna.radiowaves.left.and.right"
            case .package: return "shippingbox"
            case .investment: return "chart.line.uptrend.xyaxis"
            case .medicare: return "cross.case"
            case .other: return "questionmark"
            }
        }
    }
}

// MARK: - Privacy Row

private struct PrivacyRow: View {
    let text: String

    var body: some View {
        HStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(Color("safeGreen"))
                .font(.captionText)
            Text(text)
                .font(.captionText)
                .foregroundColor(Color("secondaryText"))
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ReportView()
    }
}
