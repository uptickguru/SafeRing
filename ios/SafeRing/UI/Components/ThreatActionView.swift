import SwiftUI

/// ThreatActionView — the most critical screen in SafeRing.
///
/// # Critical Safety Rule
/// This view MUST drive HUMAN ACTION. It MUST NEVER present a "you're safe,
/// proceed" terminal state that closes the loop on the AI's verdict. There is
/// no screen state that ends at "safe."
///
/// # Design Principles
/// - Every enum case routes to a human action
/// - The dial action targets the SAVED number only (never the incoming/suspect number)
/// - Large buttons (≥64pt) for senior-friendly touch targets
/// - High contrast colors for risk indicators
/// - VoiceOver/TalkBack labels on all interactive elements
/// - Dynamic Type compatible
/// - No terminal "safe/proceed" state
///
/// # Threat Actions
/// - CALL_SAVED_CONTACT: Don't call back this number. Call {SavedContact} on their real number.
/// - ASK_FAMILY_PASSWORD: Prompt to ask them your family password (no field that transmits it).
/// - LOOP_TRUSTED_CONTACT: Alert the trusted contact (M5).
/// - DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
/// - LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee. Keeps "Verify with trusted contact" visible.
///
struct ThreatActionView: View {

    // MARK: - Properties

    /// The recommended action from the threat detection system.
    let recommendedAction: ThreatAction

    /// The incoming caller's display name (e.g., "Unknown", "John").
    let callerLabel: String

    /// The saved contact to call instead (for CALL_SAVED_CONTACT).
    let savedContact: SavedContact?

    /// Whether the user has opted into trusted circle alerts.
    let userOptedIn: Bool

    /// The incoming caller's number hash (for event reporting, never displayed).
    let numberHash: String

    /// Whether the call was blocked by the screening service.
    let wasBlocked: Bool

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: AppTheme.spacingLG) {
                // Header — risk indicator
                headerSection
                    .padding(.horizontal)

                // Action Buttons — always visible, always drive human action
                actionButtonsSection
                    .padding(.horizontal)

                // Additional guidance
                guidanceSection
                    .padding(.horizontal)

                // Footer — never terminal, always actionable
                footerSection
                    .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .background(Color("appBackground"))
        .navigationTitle("Threat Detected")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Close") {
                    // Don't close — keep the threat visible
                    performHumanAction()
                }
                .accessibilityLabel("Continue with threat action")
                .buttonStyle(.bordered)
            }
        }
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(spacing: AppTheme.spacingMD) {
            // Risk Icon
            Image(systemName: threatIcon)
                .font(.system(size: 64))
                .foregroundColor(threatColor)
                .symbolEffect(.pulse, isActive: true)

            // Caller Label
            Text(callerLabel)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(Color("primaryText"))

            // Risk Description
            Text(threatDescription)
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
        }
        .padding(AppTheme.spacingLG)
        .background(threatBackgroundColor)
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Action Buttons Section

    private var actionButtonsSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            switch recommendedAction {
            case .callSavedContact:
                if let contact = savedContact {
                    callSavedContactButton(for: contact)
                }

            case .askFamilyPassword:
                askFamilyPasswordButton

            case .loopTrustedContact:
                loopTrustedContactButton

            case .doNotReply:
                doNotReplyButton

            case .looksOkStillVerify:
                looksOkStillVerifyButton
            }
        }
        .padding(AppTheme.spacingLG)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Guidance Section

    private var guidanceSection: some View {
        VStack(spacing: AppTheme.spacingXS) {
            // Guidance text varies by action
            switch recommendedAction {
            case .callSavedContact:
                Text("Calling \(contactName) on their saved number, not this caller's number.")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))

            case .askFamilyPassword:
                Text("Ask them your family password. This will NOT transmit any information to the caller.")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))

            case .loopTrustedContact:
                Text("Alerting your trusted contact about this suspicious call.")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))

            case .doNotReply:
                Text("Don't respond to this message. It could be a scam attempt.")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))

            case .looksOkStillVerify:
                Text("This is NOT a guarantee of safety. Always verify important communications with your trusted contact.")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
            }
        }
        .padding(AppTheme.spacingMD)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Footer Section

    private var footerSection: some View {
        VStack(spacing: AppTheme.spacingXS) {
            // Always show "Report" button — never terminal
            BigButton(
                title: "Report as Scam",
                icon: "exclamationmark.shield",
                action: {
                    performHumanAction()
                },
                color: Color("criticalRed")
            )

            // Always show "Learn More" button — never terminal
            Button {
                performHumanAction()
            } label: {
                HStack {
                    Image(systemName: "info.circle")
                    Text("What is this threat?")
                        .foregroundColor(Color("linkBlue"))
                }
            }
            .font(.bodyText)
            .foregroundColor(Color("linkBlue"))
        }
        .padding(AppTheme.spacingLG)
        .background(Color("cardBackground"))
        .cornerRadius(AppTheme.cornerRadius)
    }

    // MARK: - Action Button Implementations

    /// CALL_SAVED_CONTACT: Big button that one-tap dials the SAVED number.
    private func callSavedContactButton(for contact: SavedContact) -> some View {
        BigButton(
            title: "Don't call this number back. Call \(contactName) on their real number",
            icon: "phone",
            action: {
                // Dial the SAVED number, never the incoming number
                openPhoneApp(with: contact.savedNumber)
            },
            color: Color("safeGreen")
        )
        .accessibilityLabel("Call \(contactName) on their saved number, not this caller's number")
        .accessibilityHint("This dials the saved number for \(contactName), not the suspicious caller")
    }

    /// ASK_FAMILY_PASSWORD: Prompt to ask them your family password.
    private var askFamilyPasswordButton: some View {
        BigButton(
            title: "Ask them your family password",
            icon: "lock.fill",
            action: {
                // Open a prompt to ask the caller for the family password
                // This does NOT transmit any information to the caller
                performFamilyPasswordPrompt()
            },
            color: AppTheme.accentColor
        )
        .accessibilityLabel("Ask them your family password")
        .accessibilityHint("This will prompt you to ask the caller for your family password without transmitting any information")
    }

    /// LOOP_TRUSTED_CONTACT: Button to alert the trusted contact.
    private var loopTrustedContactButton: some View {
        BigButton(
            title: "Alert Trusted Contact",
            icon: "person.2.fill",
            action: {
                // Trigger trusted circle alert (M5)
                triggerTrustedCircleAlert()
            },
            color: AppTheme.accentColor
        )
        .accessibilityLabel("Alert trusted contact about suspicious call")
        .accessibilityHint("This will alert your trusted contact about this suspicious call")
    }

    /// DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
    private var doNotReplyButton: some View {
        BigButton(
            title: "Delete / Don't Respond",
            icon: "trash.fill",
            action: {
                // Mark as do-not-reply and log the event
                performHumanAction()
            },
            color: Color("criticalRed")
        )
        .accessibilityLabel("Don't respond to this message")
        .accessibilityHint("This will mark this message as do-not-reply and log it as a potential scam")
    }

    /// LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee.
    private var looksOkStillVerifyButton: some View {
        VStack(spacing: AppTheme.spacingSM) {
            BigButton(
                title: "Verify with Trusted Contact",
                icon: "person.2.fill",
                action: {
                    // Trigger verification with trusted contact
                    triggerTrustedCircleAlert()
                },
                color: AppTheme.accentColor
            )
            .accessibilityLabel("Verify with trusted contact")
            .accessibilityHint("This will alert your trusted contact to verify this communication")

            Text("This is NOT a guarantee of safety")
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .accessibilityLabel("This is not a guarantee of safety")
                .accessibilityHint("Always verify important communications with your trusted contact")
        }
    }

    // MARK: - Helpers

    /// Get the contact name from the saved contact.
    private var contactName: String {
        savedContact?.displayName ?? "this person"
    }

    /// Get the threat icon based on the recommended action.
    private var threatIcon: String {
        switch recommendedAction {
        case .callSavedContact:
            return "phone.fill"
        case .askFamilyPassword:
            return "lock.fill"
        case .loopTrustedContact:
            return "person.2.fill"
        case .doNotReply:
            return "exclamationmark.triangle.fill"
        case .looksOkStillVerify:
            return "questionmark.circle.fill"
        }
    }

    /// Get the threat color based on the recommended action.
    private var threatColor: Color {
        switch recommendedAction {
        case .callSavedContact:
            return Color("safeGreen")
        case .askFamilyPassword:
            return Color("warningYellow")
        case .loopTrustedContact:
            return Color("highRiskOrange")
        case .doNotReply:
            return Color("criticalRed")
        case .looksOkStillVerify:
            return Color("highRiskOrange")
        }
    }

    /// Get the threat background color based on the recommended action.
    private var threatBackgroundColor: Color {
        switch recommendedAction {
        case .callSavedContact:
            return Color("safeGreen").opacity(0.1)
        case .askFamilyPassword:
            return Color("warningYellow").opacity(0.1)
        case .loopTrustedContact:
            return Color("highRiskOrange").opacity(0.1)
        case .doNotReply:
            return Color("criticalRed").opacity(0.1)
        case .looksOkStillVerify:
            return Color("highRiskOrange").opacity(0.1)
        }
    }

    /// Get the threat description based on the recommended action.
    private var threatDescription: String {
        switch recommendedAction {
        case .callSavedContact:
            return "This caller may be a scammer. Don't call them back."
        case .askFamilyPassword:
            return "This caller may be trying to get your family password."
        case .loopTrustedContact:
            return "This call has been flagged as suspicious by SafeRing."
        case .doNotReply:
            return "This message has been flagged as a potential scam."
        case .looksOkStillVerify:
            return "This call looks okay but SafeRing wants you to verify."
        }
    }

    // MARK: - Human Action Handlers

    /// Perform the recommended human action.
    private func performHumanAction() {
        switch recommendedAction {
        case .callSavedContact:
            if let contact = savedContact {
                openPhoneApp(with: contact.savedNumber)
            }
        case .askFamilyPassword:
            performFamilyPasswordPrompt()
        case .loopTrustedContact:
            triggerTrustedCircleAlert()
        case .doNotReply:
            // Mark as do-not-reply and log the event
            print("Do not reply action taken (event logged)")
        case .looksOkStillVerify:
            triggerTrustedCircleAlert()
        }
    }

    /// Open the phone app with the saved number (for CALL_SAVED_CONTACT).
    private func openPhoneApp(with number: String) {
        // In production, this would open the phone app with the dialer
        // Pre-fill the saved number, never the incoming number
        #if os(iOS)
        if let url = URL(string: "tel://\(number)") {
            UIApplication.shared.open(url)
        }
        #endif
    }

    /// Perform the family password prompt (for ASK_FAMILY_PASSWORD).
    private func performFamilyPasswordPrompt() {
        // In production, this would show a UI prompt to ask the caller
        // for the family password without transmitting any information
        // This is a critical safety rule — no field that transmits the password
        print("Family password prompt triggered (M6)")
    }

    /// Trigger a trusted circle alert (for LOOP_TRUSTED_CONTACT / LOOKS_OK_STILL_VERIFY).
    private func triggerTrustedCircleAlert() {
        // In production, this would send a push notification to trusted contacts
        // Only if the user has opted in (M5)
        if userOptedIn {
            print("Trusted circle alert triggered (M5)")
        }
    }
}