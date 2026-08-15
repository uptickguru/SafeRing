import SwiftUI

/// View for scanning an attachment (image/document) for scam content.
///
/// # Security
/// EXIF/location metadata is stripped client-side before upload.
/// The file is analyzed only for scam content and not retained.
///
struct AttachmentScanView: View {

    // MARK: - Properties

    @StateObject var viewModel: SubmitToCheckViewModel
    @Environment(\.presentationMode) private var presentationMode

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: AppTheme.spacingLG) {
                // Header
                headerSection

                // File Selection
                fileSelectionSection
                    .padding(.horizontal)

                // Scan Button
                if viewModel.isScanningAttachment {
                    loadingIndicator
                } else if viewModel.attachmentResult != nil {
                    resultSection
                        .padding(.horizontal)
                } else {
                    scanButton
                        .padding(.horizontal)
                }

                // Error Message
                if let error = viewModel.attachmentError {
                    errorSection
                        .padding(.horizontal)
                }
            }
            .navigationTitle("Scan Attachment")
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
            Image(systemName: "paperclip.fill")
                .font(.system(size: 48))
                .foregroundColor(Color("safeGreen"))

            Text("Scan Attachment")
                .font(.screenTitle)
                .foregroundColor(Color("primaryText"))

            Text("Select a file to scan for scam content. EXIF/location metadata will be stripped before upload.")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - File Selection Section

    private var fileSelectionSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            // File Info
            if viewModel.attachmentFileName.isEmpty {
                Text("No file selected")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
                    .padding(AppTheme.spacingMD)
                    .frame(maxWidth: .infinity)
                    .background(Color("inputBackground"))
                    .cornerRadius(AppTheme.cornerRadius)
                    .overlay(
                        RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                            .stroke(Color("inputBorder"), lineWidth: 1)
                    )
            } else {
                HStack(spacing: AppTheme.spacingSM) {
                    Image(systemName: "paperclip")
                        .foregroundColor(Color("safeGreen"))

                    VStack(alignment: .leading, spacing: AppTheme.spacingXS) {
                        Text(viewModel.attachmentFileName)
                            .font(.bodyText)
                            .foregroundColor(Color("primaryText"))

                        Text("\(viewModel.attachmentData?.count ?? 0) bytes")
                            .font(.captionText)
                            .foregroundColor(Color("secondaryText"))
                    }

                    Spacer()

                    Button("Change") {
                        // Change file
                    }
                    .buttonStyle(.bordered)
                }
                .padding(AppTheme.spacingMD)
                .frame(maxWidth: .infinity)
                .background(Color("inputBackground"))
                .cornerRadius(AppTheme.cornerRadius)
            }

            // EXIF Notice
            Text("⚠️ EXIF/location metadata will be stripped before upload")
                .font(.captionText)
                .foregroundColor(Color("secondaryText"))
                .padding(.top, AppTheme.spacingXS)
        }
    }

    // MARK: - Loading Indicator

    private var loadingIndicator: some View {
        VStack(spacing: AppTheme.spacingSM) {
            ProgressView()
                .scaleEffect(1.2)

            Text("Scanning for scams...")
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

            if viewModel.attachmentResult?.riskScore != nil {
                Text("\(Int(viewModel.attachmentResult!.riskScore * 100))%")
                    .font(.riskScore)
                    .foregroundColor(Color("primaryText"))
                    .minimumScaleFactor(0.5)
            }

            // Scam Type
            if let scamType = viewModel.attachmentResult?.scamType {
                Text(scamType)
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
            }

            // Action Buttons
            if viewModel.attachmentResult?.isScam == true {
                BigButton.destructive(
                    title: "Report as Scam",
                    icon: "exclamationmark.shield",
                    action: {
                        // Report the attachment as a scam
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

    // MARK: - Scan Button

    private var scanButton: some View {
        BigButton.primary(
            title: "Scan Attachment",
            icon: "magnifyingglass",
            isLoading: false,
            action: {
                // Scan the attachment
            }
        )
    }

    // MARK: - Error Section

    private var errorSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title2)
                .foregroundColor(Color("criticalRed"))

            Text(viewModel.attachmentError)
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
    AttachmentScanView(viewModel: SubmitToCheckViewModel(apiClient: ApiClient()))
}
