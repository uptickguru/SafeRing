import SwiftUI
import UIKit

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

/// SafeRing visual language — calm, large, high-contrast, fills the phone.


// MARK: - Elegant SafeRing tokens (hard RGB — never muddy asset colors)

// MARK: - Timeless luxury tokens (Rolex-class restraint)

enum SR {
    /// sRGB via UIColor — predictable on device and simulator
    private static func c(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat = 1) -> Color {
        Color(uiColor: UIColor(red: r, green: g, blue: b, alpha: a))
    }

    // Ivory dial / soft metal
    static let canvasTop = c(0.97, 0.965, 0.955)
    static let canvasBot = c(0.94, 0.935, 0.92)
    static let surface = c(0.99, 0.985, 0.978)
    static let ink = c(0.16, 0.15, 0.14)
    static let mute = c(0.48, 0.46, 0.43)
    static let line = c(0.82, 0.79, 0.74, 0.85)

    // Precious metal accents
    static let gold = c(0.62, 0.52, 0.34)
    static let goldSoft = c(0.72, 0.62, 0.42)
    static let steel = c(0.55, 0.56, 0.58)

    // Actions — dignified, not carnival
    static let help = c(0.48, 0.22, 0.24)          // soft burgundy
    static let helpDeep = c(0.38, 0.16, 0.18)
    static let caution = c(0.52, 0.40, 0.28)        // muted bronze
    static let cautionDeep = c(0.42, 0.32, 0.22)
    static let go = c(0.28, 0.40, 0.36)             // quiet sage
    static let goDeep = c(0.22, 0.32, 0.29)
    static let accent = gold

    // Legacy aliases used by older call sites
    static let helpLegacy = help
    static let ok = go
    static let warn = caution

    static func font(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        // Classic system, not rounded cartoon
        .system(size: size, weight: weight, design: .default)
    }

    static var canvas: some View {
        LinearGradient(colors: [canvasTop, canvasBot], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea()
    }
}

struct PressSoft: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.988 : 1)
            .opacity(configuration.isPressed ? 0.94 : 1)
            .animation(.easeOut(duration: 0.16), value: configuration.isPressed)
    }
}

/// Primary control — solid metal; fixed height or expand to fill parent.
struct ElegantPrimary: View {
    let title: String
    var subtitle: String? = nil
    var icon: String? = nil
    var top: Color
    var bottom: Color = .clear
    /// nil = fill offered space (use with frame maxHeight: .infinity)
    var height: CGFloat? = 56
    var titleSize: CGFloat = 22
    var lightLabel: Bool = true
    let action: () -> Void

    private var isHero: Bool {
        if let height { return height > 140 }
        return true
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: isHero ? 22 : 16, style: .continuous)
                    .fill(top)
                RoundedRectangle(cornerRadius: isHero ? 22 : 16, style: .continuous)
                    .stroke(SR.gold.opacity(0.35), lineWidth: 0.8)

                VStack(spacing: isHero ? 10 : 5) {
                    if let icon {
                        Image(systemName: icon)
                            .font(.system(size: isHero ? 34 : 18, weight: .medium))
                            .symbolRenderingMode(.monochrome)
                    }
                    Text(title)
                        .font(SR.font(titleSize, .semibold))
                        .tracking(isHero ? 2.5 : 0.4)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .minimumScaleFactor(0.85)
                    if let subtitle {
                        Text(subtitle)
                            .font(SR.font(isHero ? 18 : 15, .regular))
                            .opacity(0.88)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
                .foregroundStyle(lightLabel ? Color.white.opacity(0.96) : SR.ink)
                .padding(.horizontal, 16)
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)
            .frame(height: height)
            .shadow(color: Color.black.opacity(0.10), radius: 12, y: 5)
        }
        .buttonStyle(PressSoft())
    }
}

struct StatusPill: View {
    let text: String
    var ok: Bool = true
    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(ok ? SR.gold : SR.mute)
                .frame(width: 7, height: 7)
            Text(text)
                .font(SR.font(14, .medium))
                .foregroundStyle(SR.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Capsule(style: .continuous).fill(SR.surface))
        .overlay(Capsule(style: .continuous).stroke(SR.line, lineWidth: 0.8))
    }
}

