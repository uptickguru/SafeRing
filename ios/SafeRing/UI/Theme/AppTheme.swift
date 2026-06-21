import SwiftUI

/// Design tokens and theme constants for SafeRing.
///
/// # Senior-Friendly Design
/// - Large text sizes (body minimum 17pt)
/// - High contrast colors (no pastels)
/// - Generous spacing and touch targets (minimum 48pt)
/// - Clear visual hierarchy with bold headings
///
struct AppTheme {

    // MARK: - Spacing

    /// Extra small spacing (4pt)
    static let spacingXXS: CGFloat = 4
    /// Small spacing (8pt)
    static let spacingXS: CGFloat = 8
    /// Standard spacing (12pt)
    static let spacingSM: CGFloat = 12
    /// Medium spacing (16pt)
    static let spacingMD: CGFloat = 16
    /// Large spacing (24pt)
    static let spacingLG: CGFloat = 24
    /// Extra large spacing (32pt)
    static let spacingXL: CGFloat = 32
    /// Massive spacing (48pt)
    static let spacingXXL: CGFloat = 48

    // MARK: - Corner Radius

    /// Standard corner radius for cards and containers (12pt)
    static let cornerRadius: CGFloat = 12
    /// Corner radius for buttons (16pt)
    static let buttonCornerRadius: CGFloat = 16
    /// Corner radius for small elements like badges (8pt)
    static let smallCornerRadius: CGFloat = 8

    // MARK: - Touch Targets

    /// Minimum touch target size for interactive elements (44pt)
    static let minimumTouchTarget: CGFloat = 44
    /// Large touch target for primary actions (56pt)
    static let largeTouchTarget: CGFloat = 56
    /// Extra large touch target for senior-friendly buttons (64pt)
    static let xlTouchTarget: CGFloat = 64

    // MARK: - Animation

    /// Standard animation duration (0.3s)
    static let standardAnimation: Animation = .easeInOut(duration: 0.3)
    /// Quick animation for micro-interactions (0.15s)
    static let quickAnimation: Animation = .easeOut(duration: 0.15)

    // MARK: - Preferred Font

    /// The preferred font design for accessibility.
    /// Uses the system rounded font for a friendly, approachable feel.
    static let preferredFontDesign: Font.Design = .default

    // MARK: - Accent Color

    /// The app's accent color — a high-visibility blue that works well
    /// for seniors with color vision deficiencies.
    static var accentColor: Color { Color("AccentColor") }

    // MARK: - Shadow

    /// Shadow for elevated elements like cards and alerts.
    static let cardShadow: some ViewModifier = ShadowModifier()

    private struct ShadowModifier: ViewModifier {
        func body(content: Content) -> some View {
            content
                .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 2)
                .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 1)
        }
    }

    // MARK: - Risk Colors

    /// Colors for risk level indicators — designed for accessibility.
    struct RiskColors {
        static let safe = Color("safeGreen")
        static let suspicious = Color("warningYellow")
        static let highRisk = Color("highRiskOrange")
        static let scam = Color("criticalRed")
        static let unknown = Color("secondaryText")

        static func color(for risk: Double) -> Color {
            switch risk {
            case ..<0.3: return safe
            case ..<0.5: return suspicious
            case ..<0.75: return highRisk
            default: return scam
            }
        }
    }
}

// MARK: - View Extension

extension View {
    /// Applies the standard card shadow to a view.
    func cardShadow() -> some View {
        modifier(AppTheme.cardShadow)
    }
}
