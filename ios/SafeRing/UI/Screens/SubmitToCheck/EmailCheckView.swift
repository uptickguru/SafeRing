import SwiftUI

/// View for checking an email address for scam content.
///
/// # Security
/// The email text is submitted as-is. The API analyzes it for known scam
/// patterns, phishing links, and social engineering tactics.
///
struct EmailCheckView: View {

    // MARK: - Properties

    @StateObject var viewModel: SubmitToCheckViewModel
    @Environment(\.presentationMode) private var presentationMode

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: AppTheme.spacingLG) {
                // Header
                headerSection

                // Text Field
                textFieldSection
                    .padding(.horizontal)

                // Check Button
                if viewModel.isCheckingEmail {
                    loadingIndicator
                } else if viewModel.emailResult != nil {
                    resultSection
                        .padding(.horizontal)
                } else {
                    checkButton
                        .padding(.horizontal)
                }

                // Error Message
                if let error = viewModel.emailError {
                    errorSection
                        .padding(.horizontal)
                }
            }
            .navigationTitle("Check Email")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .alert("Error", isPresented: $viewModel.showError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(viewModel.errorMessage)
            }
        }
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "envelope.fill")
                .font(.system(size: 48))
                .foregroundColor(Color("safeGreen"))

            Text("Check Email")
                .font(.screenTitle)
                .foregroundColor(Color("primaryText"))

            Text("Paste or forward the email you want to check for scam content.")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Text Field Section

    private var textFieldSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            TextField("Paste email text here...", text: $viewModel.emailText)
                .font(.bodyText)
                .padding(AppTheme.spacingMD)
                .background(Color("inputBackground"))
                .cornerRadius(AppTheme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                        .stroke(Color("inputBorder"), lineWidth: 1)
                )

            // Placeholder hint
            if viewModel.emailText.isEmpty {
                Text("Paste or forward the email you want to check...")
                    .font(.captionText)
                    .foregroundColor(Color("secondaryText"))
                    .padding(.top, AppTheme.spacingXS)
            }
        }
    }

    // MARK: - Loading Indicator

    private var loadingIndicator: some View {
        VStack(spacing: AppTheme.spacingSM) {
            ProgressView()
                .scaleEffect(1.2)

            Text("Checking for scams...")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Result Section

    private var resultSection: some View {
        VStack(spacing: AppTheme.spacingMD) {
            // Risk Score
            Text("Risk Score")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))

            if viewModel.emailResult?.riskScore != nil {
                Text("\(Int(viewModel.emailResult!.riskScore * 100))%")
                    .font(.riskScore)
                    .foregroundColor(Color("primaryText"))
                    .minimumScaleFactor(0.5)
            }

            // Scam Type
            if let scamType = viewModel.emailResult?.scamType {
                Text(scamType)
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
            }

            // Result Text
            if let resultText = viewModel.emailResult?.text {
                Text(resultText)
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
                    .multilineTextAlignment(.center)
                    .padding(AppTheme.spacingMD)
                    .background(Color("resultBackground"))
                    .cornerRadius(AppTheme.cornerRadius)
            }

            // Action Buttons
            if viewModel.emailResult?.isScam == true {
                BigButton.destructive(
                    title: "Report as Scam",
                    icon: "exclamationmark.shield",
                    action: {
                        // Report the email as a scam
                    }
                )
                .padding(.top, AppTheme.spacingMD)
            } else {
                BigButton.success(
                    title: "Looks Safe",
                    icon: "checkmark",
                    action: {
                        // Mark as safe
                    }
                )
                .padding(.top, AppTheme.spacingMD)
            }
        }
    }

    // MARK: - Check Button

    private var checkButton: some View {
        BigButton.primary(
            title: "Check Email",
            icon: "magnifyingglass",
            isLoading: false,
            action: {
                Task { do { _ = try await viewModel.checkEmail() } catch {} }
            }
        )
    }

    // MARK: - Error Section

    private var errorSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title2)
                .foregroundColor(Color("criticalRed"))

            Text(viewModel.emailError ?? "")
                .font(.bodyText)
                .foregroundColor(Color("criticalRed"))
                .multilineTextAlignment(.center)
        }
        .padding(AppTheme.spacingMD)
        .frame(maxWidth: .infinity)
        .background(Color("errorBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }
}

// MARK: - Preview

#Preview {
    EmailCheckView(viewModel: SubmitToCheckViewModel(apiClient: ApiClient()))
}
