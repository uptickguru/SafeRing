import SwiftUI

/// Settings screen for SafeRing.
///
/// # Senior-Friendly Design
/// - Large toggle switches
/// - Clear, minimal options (no overwhelming settings)
/// - One-tap "Make it smarter" for advanced settings
/// - No login/signup required
///
struct SettingsView: View {

    // MARK: - App Storage

    @AppStorage("protectionEnabled") private var protectionEnabled = true
    @AppStorage("smsScanningEnabled") private var smsScanningEnabled = true
    @AppStorage("autoBlockScam") private var autoBlockScam = true
    @AppStorage("showSmsBody") private var showSmsBody = false
    @AppStorage("showAdvancedSettings") private var showAdvancedSettings = false
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = true

    // MARK: - State

    @State private var showResetConfirmation = false
    @State private var callDirectoryStatus = "Checking..."

    // MARK: - Body

    @ObservedObject private var household = HouseholdStore.shared
    @State private var newPassword = ""
    @State private var showContacts = false

    var body: some View {
        List {
            Section {
                TextField("Your name", text: $household.ownerDisplayName)
                TextField("Trusted person name", text: $household.trustedContactName)
                TextField("Trusted person number", text: $household.trustedContactNumber)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                Button("Pick from Contacts") { showContacts = true }

                Picker("How to reach them", selection: $household.preferredChannel) {
                    ForEach(HouseholdStore.SignalChannel.allCases) { channel in
                        Text(channel.title).tag(channel)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(household.preferredChannel.subtitle)
                        .font(.captionText)
                        .foregroundColor(Color("secondaryText"))
                    Text("Text / iMessage is the only channel that reliably delivers a prefilled alert on iPhone. Signal has no send API. WhatsApp can prefill if it is installed. FaceTime is for a live check, not a text.")
                        .font(.captionText)
                        .foregroundColor(Color("secondaryText"))
                }

                SecureField("New family password", text: $newPassword)
                Button("Save family password on this phone") {
                    household.setFamilyPassword(newPassword)
                    newPassword = ""
                }
                .disabled(newPassword.trimmingCharacters(in: .whitespaces).count < 3)
            } header: {
                Text("Your person")
                    .font(.sectionTitle)
            } footer: {
                Text("The number and password stay on this device. Help texts only go to this person.")
                    .font(.captionText)
            }

            Section {
                Toggle("Silence Unknown Callers is on", isOn: $household.silenceUnknownConfirmed)
                Toggle("Filter Unknown Senders is on", isOn: $household.filterUnknownSmsConfirmed)
                Toggle("Carrier scam block is on", isOn: $household.carrierProtectionConfirmed)
                Button("Open iPhone Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
            } header: {
                Text("Give the iPhone permission")
                    .font(.sectionTitle)
            } footer: {
                Text("1. Settings → Phone → Silence Unknown Callers.\n2. Settings → Apps → Messages → Unknown & Spam → Filter Unknown Senders.\n3. Contacts: allow SafeRing when it asks, so you can pick your person.\n4. Carrier: T-Mobile Scam Shield, AT&T ActiveArmor, or Verizon Call Filter — turn Block on.\niPhone will not let SafeRing become the system caller-ID app. Silence Unknown + carrier block do the intercept. SafeRing is the family tripwire.")
                    .font(.captionText)
            }

            // MARK: - Protection Section
            Section {
                Toggle(isOn: $protectionEnabled) {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Call Protection")
                                .font(.bodyText)
                            Text("Screen incoming calls for scams")
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    } icon: {
                        Image(systemName: "phone.badge.waveform.fill")
                            .foregroundColor(Color("safeGreen"))
                    }
                }
                .tint(AppTheme.accentColor)

                Toggle(isOn: $smsScanningEnabled) {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("SMS Scanning")
                                .font(.bodyText)
                            Text("Check text messages for scams")
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    } icon: {
                        Image(systemName: "message.badge.fill")
                            .foregroundColor(Color("warningYellow"))
                    }
                }
                .tint(AppTheme.accentColor)

                Toggle(isOn: $autoBlockScam) {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Auto-Block Known Scams")
                                .font(.bodyText)
                            Text("Block confirmed scam callers automatically")
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    } icon: {
                        Image(systemName: "nosign")
                            .foregroundColor(Color("criticalRed"))
                    }
                }
                .tint(AppTheme.accentColor)
            } header: {
                Text("Protection")
                    .font(.sectionTitle)
            }

            // MARK: - Permissions Status
            Section {
                PermissionRow(
                    icon: "bell.badge.fill",
                    color: Color("warningYellow"),
                    title: "Notifications",
                    description: "Alerts for scam calls and SMS",
                    settingsURL: UIApplication.openSettingsURLString
                )
                PermissionRow(
                    icon: "phone.badge.waveform.fill",
                    color: Color("safeGreen"),
                    title: "Call Directory Extension",
                    description: callDirectoryStatus,
                    action: openCallDirectorySettings
                )
                PermissionRow(
                    icon: "message.badge.fill",
                    color: Color("warningYellow"),
                    title: "SMS Access",
                    description: "Scan messages for scam content",
                    settingsURL: UIApplication.openSettingsURLString
                )
            } header: {
                Text("Permissions")
                    .font(.sectionTitle)
            } footer: {
                Text("SafeRing needs these permissions to protect you. If any were denied during setup, tap Learn More to enable them in Settings.")
                    .font(.captionText)
            }

            // MARK: - Extension Status
            Section {
                HStack {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Call Screening Extension")
                                .font(.bodyText)
                            Text(callDirectoryStatus)
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    } icon: {
                        Image(systemName: callDirectoryIcon)
                            .foregroundColor(callDirectoryColor)
                    }

                    Spacer()

                    Button("Settings") {
                        openCallDirectorySettings()
                    }
                    .font(.buttonLabel)
                    .foregroundColor(AppTheme.accentColor)
                }
            } header: {
                Text("System Integration")
                    .font(.sectionTitle)
            }

            // MARK: - Privacy Section
            Section {
                Toggle(isOn: $showSmsBody) {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Save SMS Content")
                                .font(.bodyText)
                            Text("Store message text locally for review")
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    } icon: {
                        Image(systemName: "doc.text.magnifyingglass")
                            .foregroundColor(Color("secondaryText"))
                    }
                }
                .tint(AppTheme.accentColor)

                Button {
                    showAdvancedSettings.toggle()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Advanced Settings")
                                .font(.bodyText)
                            Text("Model updates, cache, diagnostics")
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    } icon: {
                        Image(systemName: "gearshape.2.fill")
                            .foregroundColor(Color("secondaryText"))
                    }
                }

                if showAdvancedSettings {
                    advancedSettings
                }
            } header: {
                Text("Privacy & Data")
                    .font(.sectionTitle)
            } footer: {
                Text("SafeRing never sends your personal data, phone numbers, or message content to any server. All SMS analysis is on-device.")
                    .font(.captionText)
            }

            // MARK: - About Section
            Section {
                HStack {
                    Text("Version")
                        .font(.bodyText)
                    Spacer()
                    Text("1.0.0")
                        .font(.bodyText)
                        .foregroundColor(Color("secondaryText"))
                }

                Link(destination: URL(string: "https://safering.app/privacy")!) {
                    HStack {
                        Text("Privacy Policy")
                            .font(.bodyText)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.captionText)
                    }
                }
                .foregroundColor(Color("primaryText"))

                Link(destination: URL(string: "https://safering.app/support")!) {
                    HStack {
                        Text("Help & Support")
                            .font(.bodyText)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.captionText)
                    }
                }
                .foregroundColor(Color("primaryText"))
            } header: {
                Text("About")
                    .font(.sectionTitle)
            }

            // MARK: - Reset
            Section {
                Button(role: .destructive) {
                    showResetConfirmation = true
                } label: {
                    Label("Start over — clear and onboard again", systemImage: "arrow.counterclockwise")
                        .font(.buttonLabel)
                }
            }
        }
        .listStyle(.insetGrouped)
        .background(Color("appBackground"))
        .navigationTitle("Settings")
        .task {
            await checkExtensionStatus()
        }
        .alert("Reset SafeRing?", isPresented: $showResetConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Reset", role: .destructive) {
                resetApp()
            }
        } message: {
            Text("Clears your person, password, and settings on this phone. You will see the welcome setup again immediately.")
        }
        .sheet(isPresented: $showContacts) {
            ContactPicker(
                onPick: { name, number in
                    if !name.isEmpty { household.trustedContactName = name }
                    household.trustedContactNumber = number
                    showContacts = false
                },
                onCancel: { showContacts = false }
            )
            .ignoresSafeArea()
        }
    }

    // MARK: - Advanced Settings

    @ViewBuilder
    private var advancedSettings: some View {
        Button("Clear Scam Cache") {
            clearCache()
        }
        .font(.bodyText)

        Button("Force Data Sync") {
            forceSync()
        }
        .font(.bodyText)
    }

    // MARK: - Helpers

    private var callDirectoryIcon: String {
        switch callDirectoryStatus {
        case let s where s.contains("Active"): return "checkmark.circle.fill"
        case let s where s.contains("Needs"): return "exclamationmark.circle.fill"
        default: return "circle.dotted"
        }
    }

    private var callDirectoryColor: Color {
        switch callDirectoryStatus {
        case let s where s.contains("Active"): return Color("safeGreen")
        case let s where s.contains("Needs"): return Color("warningYellow")
        default: return Color("secondaryText")
        }
    }

    private func openCallDirectorySettings() {
        CallDirectoryManager.shared.openSettings()
    }

    private func checkExtensionStatus() async {
        let status = await CallDirectoryManager.shared.getExtensionStatus()
        callDirectoryStatus = CallDirectoryManager.shared.statusDescription(for: status)
    }

    private func clearCache() {
        Task {
            let context = SafeRingApp.sharedModelContainer.mainContext
            let store = ScamStore(modelContext: context)
            store.clearAll()
        }
    }

    private func forceSync() {
        Task {
            let context = SafeRingApp.sharedModelContainer.mainContext
            let repo = ScamRepository(
                apiClient: ApiClient(),
                scamStore: ScamStore(modelContext: context)
            )
            let sync = SyncScamDataUseCase(repository: repo)
            try? await sync.execute()
        }
    }

    private func resetApp() {
        // Full wipe → onboarding on next paint (works without killing the app)
        HouseholdStore.shared.resetForOnboarding()
        hasCompletedOnboarding = false
        clearCache()
    }
}

// MARK: - Permission Row Component

private struct PermissionRow: View {
    let icon: String
    let color: Color
    let title: String
    let description: String
    var settingsURL: String?
    var action: (() -> Void)?

    var body: some View {
        HStack {
            Label {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.bodyText)
                    Text(description)
                        .font(.captionText)
                        .foregroundColor(Color("secondaryText"))
                }
            } icon: {
                Image(systemName: icon)
                    .foregroundColor(color)
            }

            Spacer()

            if let action = action {
                Button("Settings") {
                    action()
                }
                .font(.buttonLabel)
                .foregroundColor(AppTheme.accentColor)
            } else if let url = settingsURL {
                Link(destination: URL(string: url)!) {
                    Label("Learn More", systemImage: "arrow.up.right")
                        .font(.captionText)
                }
                .foregroundColor(AppTheme.accentColor)
            }
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        SettingsView()
    }
}
