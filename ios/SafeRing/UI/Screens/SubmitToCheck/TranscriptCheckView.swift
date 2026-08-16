import SwiftUI

/// View for checking a call transcript for scam content.
///
/// # Security
/// The transcript is submitted as-is. The user must only submit conversations
/// they are lawfully permitted to share.
///
struct TranscriptCheckView: View {

    // MARK: - Properties

    @StateObject var viewModel: SubmitToCheckViewModel
    @Environment(\.presentationMode) private var presentationMode

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: AppTheme.spacingLG) {
                // Header
                headerSection

                // Consent Notice
                consentNoticeSection
                    .padding(.horizontal)

                // Text Field
                textFieldSection
                    .padding(.horizontal)

                // Check Button
                if viewModel.isCheckingTranscript {
                    loadingIndicator
                } else if viewModel.transcriptResult != nil {
                    resultSection
                        .padding(.horizontal)
                } else {
                    checkButton
                        .padding(.horizontal)
                }

                // Error Message
                if let error = viewModel.transcriptError {
                    errorSection
                        .padding(.horizontal)
                }
            }
            .navigationTitle("Check Transcript")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .alert("Consent Required", isPresented: $viewModel.showConsentAlert) {
                Button("I Understand", role: .none) {
                    viewModel.consentAcknowledged = true
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("You must only submit conversations you are lawfully permitted to share.")
            }
        }
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "quote.bubble.fill")
                .font(.system(size: 48))
                .foregroundColor(Color("safeGreen"))

            Text("Check Transcript")
                .font(.screenTitle)
                .foregroundColor(Color("primaryText"))

            Text("Paste the conversation you want to check for scam content. You must only submit conversations you are lawfully permitted to share.")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Consent Notice Section

    private var consentNoticeSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "shield.checkered")
                .font(.title2)
                .foregroundColor(Color("accentColor"))

            Text("Consent Notice")
                .font(.bodyText)
                .foregroundColor(Color("primaryText"))

            Text("You must only submit conversations you are lawfully permitted to share.")
                .font(.captionText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
                .padding(AppTheme.spacingMD)
                .background(Color("warningYellow").opacity(0.1))
                .cornerRadius(AppTheme.cornerRadius)
        }
    }

    // MARK: - Text Field Section

    private var textFieldSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            TextField("Paste transcript text here...", text: $viewModel.transcriptText)
                .font(.bodyText)
                .padding(AppTheme.spacingMD)
                .background(Color("inputBackground"))
                .cornerRadius(AppTheme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                        .stroke(Color("inputBorder"), lineWidth: 1)
                )

            // Placeholder hint
            if viewModel.transcriptText.isEmpty {
                Text("Paste the conversation you want to check...")
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

            if viewModel.transcriptResult?.riskScore != nil {
                Text("\(Int(viewModel.transcriptResult!.riskScore * 100))%")
                    .font(.riskScore)
                    .foregroundColor(Color("primaryText"))
                    .minimumScaleFactor(0.5)
            }

            // Scam Type
            if let scamType = viewModel.transcriptResult?.scamType {
                Text(scamType)
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
            }

            // Result Text
            if let resultText = viewModel.transcriptResult?.text {
                Text(resultText)
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
                    .multilineTextAlignment(.center)
                    .padding(AppTheme.spacingMD)
                    .background(Color("resultBackground"))
                    .cornerRadius(AppTheme.cornerRadius)
            }

            // Action Buttons
            if viewModel.transcriptResult?.isScam == true {
                BigButton.destructive(
                    title: "Report as Scam",
                    icon: "exclamationmark.shield",
                    action: {
                        // Report the transcript as a scam
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
            title: "Check Transcript",
            icon: "magnifyingglass",
            isLoading: false,
            action: {
                Task { do { _ = try await viewModel.checkTranscript() } catch {} }
            }
        )
    }

    // MARK: - Error Section

    private var errorSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title2)
                .foregroundColor(Color("criticalRed"))

            Text(viewModel.transcriptError ?? "")
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
    TranscriptCheckView(viewModel: SubmitToCheckViewModel(apiClient: ApiClient()))
}
