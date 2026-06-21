import SwiftUI

/// Color-coded risk badge showing scam assessment level.
///
/// Displays a compact, accessibility-friendly badge that quickly
/// communicates risk level through both color and text.
///
/// # Accessibility
/// - Color is never the only indicator — text label is always included.
/// - Uses high-contrast, WCAG AA compliant colors.
/// - Supports Dynamic Type for text scaling.
///
struct RiskBadge: View {

    // MARK: - Properties

    /// The risk score (0.0–1.0)
    let riskScore: Double

    /// Optional custom label override
    let label: String?

    /// Size variant
    let size: BadgeSize

    /// Whether to show the numeric score
    var showScore: Bool = false

    // MARK: - Initializer

    init(
        riskScore: Double,
        label: String? = nil,
        size: BadgeSize = .medium,
        showScore: Bool = false
    ) {
        self.riskScore = riskScore
        self.label = label
        self.size = size
        self.showScore = showScore
    }

    // MARK: - Body

    var body: some View {
        HStack(spacing: spacing) {
            // Dot indicator
            Circle()
                .fill(riskColor)
                .frame(width: dotSize, height: dotSize)

            // Label
            Text(displayLabel)
                .font(size.font)
                .foregroundColor(riskColor)
                .fontWeight(.semibold)

            // Optional score
            if showScore {
                Text("(\(Int(riskScore * 100))%)")
                    .font(size.scoreFont)
                    .foregroundColor(Color("secondaryText"))
            }
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.vertical, verticalPadding)
        .background(riskColor.opacity(0.12))
        .cornerRadius(AppTheme.smallCornerRadius)
        .accessibilityLabel("Risk level: \(riskLevelText), score: \(Int(riskScore * 100)) percent")
    }

    // MARK: - Computed Properties

    private var riskLevel: RiskLevel {
        switch riskScore {
        case ..<0.3:  return .safe
        case ..<0.5:  return .suspicious
        case ..<0.75: return .highRisk
        default:      return .scam
        }
    }

    private var riskColor: Color {
        switch riskLevel {
        case .safe:       return Color("safeGreen")
        case .suspicious: return Color("warningYellow")
        case .highRisk:   return Color("highRiskOrange")
        case .scam:       return Color("criticalRed")
        }
    }

    private var displayLabel: String {
        if let label = label { return label }
        return riskLevel.rawValue
    }

    private var riskLevelText: String {
        riskLevel.rawValue
    }

    // MARK: - Layout

    private var spacing: CGFloat {
        switch size {
        case .small: return 4
        case .medium: return 6
        case .large: return 8
        }
    }

    private var dotSize: CGFloat {
        switch size {
        case .small: return 6
        case .medium: return 8
        case .large: return 10
        }
    }

    private var horizontalPadding: CGFloat {
        switch size {
        case .small: return 8
        case .medium: return 10
        case .large: return 14
        }
    }

    private var verticalPadding: CGFloat {
        switch size {
        case .small: return 3
        case .medium: return 5
        case .large: return 8
        }
    }
}

// MARK: - Supporting Types

extension RiskBadge {
    enum RiskLevel: String {
        case safe = "Safe"
        case suspicious = "Suspicious"
        case highRisk = "High Risk"
        case scam = "Scam"
    }

    enum BadgeSize {
        case small
        case medium
        case large

        var font: Font {
            switch self {
            case .small: return .badgeLabel
            case .medium: return .captionText
            case .large: return .bodyText
            }
        }

        var scoreFont: Font {
            switch self {
            case .small: return .caption2
            case .medium: return .captionText
            case .large: return .bodyText
            }
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 12) {
        RiskBadge(riskScore: 0.1, size: .small)
        RiskBadge(riskScore: 0.4, size: .medium)
        RiskBadge(riskScore: 0.6, size: .medium, showScore: true)
        RiskBadge(riskScore: 0.9, size: .large, showScore: true)
        RiskBadge(riskScore: 0.95, label: "IRS Scam", size: .large, showScore: true)
    }
    .padding()
}
