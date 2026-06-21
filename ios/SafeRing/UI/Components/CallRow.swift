import SwiftUI

/// A single row in the call history list.
///
/// Displays call information with a color-coded risk indicator,
/// making it easy for seniors to quickly identify suspicious calls.
///
struct CallRow: View {

    // MARK: - Properties

    let callLog: CallLog

    // MARK: - Body

    var body: some View {
        HStack(spacing: AppTheme.spacingMD) {
            // Direction Icon
            directionIcon
                .frame(width: 40, height: 40)
                .background(directionIconBackground)
                .cornerRadius(AppTheme.smallCornerRadius)

            // Call Details
            VStack(alignment: .leading, spacing: 4) {
                // Caller Label
                Text(callLog.callerLabel)
                    .font(.bodyTextEmphasized)
                    .foregroundColor(Color("primaryText"))
                    .lineLimit(1)

                // Call Info
                HStack(spacing: AppTheme.spacingXS) {
                    Text(callLog.direction.rawValue)
                        .font(.captionText)
                        .foregroundColor(Color("secondaryText"))

                    Text("•")
                        .foregroundColor(Color("secondaryText"))

                    Text(formattedTimestamp)
                        .font(.captionText)
                        .foregroundColor(Color("secondaryText"))

                    if callLog.duration > 0 {
                        Text("•")
                            .foregroundColor(Color("secondaryText"))
                        Text(formattedDuration)
                            .font(.captionText)
                            .foregroundColor(Color("secondaryText"))
                    }
                }
            }

            Spacer()

            // Risk Badge or Status
            VStack(alignment: .trailing, spacing: 4) {
                if callLog.riskScore > 0 {
                    RiskBadge(
                        riskScore: callLog.riskScore,
                        label: screeningLabel,
                        size: .small
                    )
                } else {
                    Text(screeningLabel)
                        .font(.badgeLabel)
                        .foregroundColor(Color("secondaryText"))
                }

                if let scamLabel = callLog.scamLabel {
                    Text(scamLabel)
                        .font(.caption2)
                        .foregroundColor(Color("criticalRed"))
                        .lineLimit(1)
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    // MARK: - Direction Icon

    private var directionIcon: some View {
        Image(systemName: directionIconName)
            .font(.body)
            .foregroundColor(.white)
    }

    private var directionIconName: String {
        switch callLog.direction {
        case .incoming: return "phone.arrow.down.left.fill"
        case .outgoing: return "phone.arrow.up.right.fill"
        case .missed: return "phone.arrow.down.left.fill"
        }
    }

    private var directionIconBackground: Color {
        switch callLog.direction {
        case .missed: return Color("criticalRed").opacity(0.8)
        case .incoming: return AppTheme.accentColor
        case .outgoing: return Color("safeGreen")
        }
    }

    // MARK: - Helpers

    private var screeningLabel: String {
        switch callLog.screeningResult {
        case .unknown: return "Unknown"
        case .safe: return "Safe"
        case .suspicious: return "⚠ Suspicious"
        case .blocked: return "🚫 Blocked"
        case .scam: return "🚨 Scam"
        }
    }

    private var formattedTimestamp: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: callLog.timestamp, relativeTo: Date())
    }

    private var formattedDuration: String {
        let minutes = Int(callLog.duration) / 60
        let seconds = Int(callLog.duration) % 60
        if minutes > 0 {
            return "\(minutes)m \(seconds)s"
        }
        return "\(seconds)s"
    }
}

// MARK: - Preview

#Preview {
    VStack {
        CallRow(callLog: CallLog(
            hashedPhoneNumber: "abc123",
            callerLabel: "Unknown Caller",
            direction: .incoming,
            screeningResult: .scam,
            riskScore: 0.92,
            scamLabel: "IRS Scam",
            duration: 0,
            timestamp: Date().addingTimeInterval(-3600)
        ))

        CallRow(callLog: CallLog(
            hashedPhoneNumber: "def456",
            callerLabel: "Mom",
            direction: .incoming,
            screeningResult: .safe,
            riskScore: 0.05,
            duration: 245,
            timestamp: Date().addingTimeInterval(-7200)
        ))

        CallRow(callLog: CallLog(
            hashedPhoneNumber: "ghi789",
            callerLabel: "Unknown",
            direction: .missed,
            screeningResult: .suspicious,
            riskScore: 0.45,
            timestamp: Date().addingTimeInterval(-18000)
        ))
    }
    .padding()
}
