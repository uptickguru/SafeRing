import SwiftUI

/// Full-screen warning overlay for scam call alerts.
///
/// Displayed when an incoming call is identified as a high-risk scam.
/// Designed for maximum visibility and clarity for senior users.
///
/// # Features
/// - Large, unmistakable warning icon and text
/// - High-contrast red/orange color scheme
/// - Clear actionable buttons: Block or Dismiss
/// - Optional call-to-action to report the number
///
struct ScamAlertView: View {

    // MARK: - Properties

    let riskLabel: String
    let riskScore: Double
    let callerName: String
    let scamType: String?

    let onDismiss: () -> Void
    let onBlock: () -> Void

    // MARK: - Body

    var body: some View {
        ZStack {
            // Dimmed background
            Color.black.opacity(0.6)
                .ignoresSafeArea()

            // Alert Card
            VStack(spacing: AppTheme.spacingLG) {
                // Warning Icon
                warningIcon

                // Title
                Text("⚠️ SCAM CALL ⚠️")
                    .font(.heroTitle)
                    .foregroundColor(Color("criticalRed"))
                    .multilineTextAlignment(.center)

                // Caller Info
                VStack(spacing: AppTheme.spacingXS) {
                    Text("From:")
                        .font(.bodyText)
                        .foregroundColor(Color("secondaryText"))
                    Text(callerName)
                        .font(.largeBody)
                        .foregroundColor(Color("primaryText"))
                        .fontWeight(.semibold)
                }

                // Risk Badge
                RiskBadge(
                    riskScore: riskScore,
                    label: riskLabel,
                    size: .large,
                    showScore: true
                )

                // Scam Type
                if let type = scamType {
                    VStack(spacing: AppTheme.spacingXS) {
                        Text("Detected Scam Type:")
                            .font(.bodyText)
                            .foregroundColor(Color("secondaryText"))
                        Text(type)
                            .font(.sectionTitle)
                            .foregroundColor(Color("criticalRed"))
                    }
                }

                // Warning Message
                Text("This call appears to be a confirmed scam. Do not share any personal information, bank details, or passwords.")
                    .font(.bodyText)
                    .foregroundColor(Color("primaryText"))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                // Divider
                Divider()
                    .padding(.vertical, AppTheme.spacingXS)

                // Action Buttons
                VStack(spacing: AppTheme.spacingSM) {
                    // Block Button
                    BigButton(
                        title: "Block This Number",
                        icon: "nosign",
                        action: onBlock,
                        color: Color("criticalRed")
                    )

                    // Dismiss Button
                    BigButton(
                        title: "I'll Handle It",
                        icon: "hand.raised",
                        action: onDismiss,
                        color: Color("secondaryText")
                    )
                }
            }
            .padding(AppTheme.spacingLG)
            .background(Color("cardBackground"))
            .cornerRadius(AppTheme.cornerRadius)
            .cardShadow()
            .padding(.horizontal, AppTheme.spacingMD)
        }
    }

    // MARK: - Warning Icon

    private var warningIcon: some View {
        ZStack {
            Circle()
                .fill(Color("criticalRed").opacity(0.15))
                .frame(width: 80, height: 80)

            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundColor(Color("criticalRed"))
        }
    }
}

// MARK: - Preview

#Preview {
    ScamAlertView(
        riskLabel: "IRS Impersonation",
        riskScore: 0.95,
        callerName: "+1 (888) 555-0123",
        scamType: "IRS / Tax Scam",
        onDismiss: { },
        onBlock: { }
    )
}
