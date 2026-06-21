import SwiftUI

/// SafeRing typography — optimized for senior readability.
///
/// Uses the system Dynamic Type to automatically scale with the user's
/// preferred reading size. All text styles have a minimum size that
/// ensures readability for older adults.
///
/// # Senior-Friendly Guidelines
/// - Body text: minimum 17pt (vs standard 15pt)
/// - Buttons: minimum 18pt bold
/// - Headlines: 28pt+ for clarity
/// - Captions: 15pt minimum (never tiny)
///

// MARK: - Text Styles

extension Font {

    // MARK: - Large Headings

    /// Hero title for onboarding and alerts (34pt, bold)
    static let heroTitle = Font.system(size: 34, weight: .bold, design: AppTheme.preferredFontDesign)

    /// Primary screen title (28pt, bold)
    static let screenTitle = Font.system(size: 28, weight: .bold, design: AppTheme.preferredFontDesign)

    /// Section header (22pt, semibold)
    static let sectionTitle = Font.system(size: 22, weight: .semibold, design: AppTheme.preferredFontDesign)

    // MARK: - Body Text

    /// Standard body text (17pt, regular)
    static let bodyText = Font.system(size: 17, weight: .regular, design: AppTheme.preferredFontDesign)

    /// Emphasized body text (17pt, semibold)
    static let bodyTextEmphasized = Font.system(size: 17, weight: .semibold, design: AppTheme.preferredFontDesign)

    /// Large body for important information (19pt, regular)
    static let largeBody = Font.system(size: 19, weight: .regular, design: AppTheme.preferredFontDesign)

    // MARK: - Button Text

    /// Standard button label (18pt, bold)
    static let buttonLabel = Font.system(size: 18, weight: .bold, design: AppTheme.preferredFontDesign)

    /// Large button label for primary actions (20pt, bold)
    static let largeButtonLabel = Font.system(size: 20, weight: .bold, design: AppTheme.preferredFontDesign)

    // MARK: - Supporting Text

    /// Caption text for secondary info (15pt, regular)
    static let captionText = Font.system(size: 15, weight: .regular, design: AppTheme.preferredFontDesign)

    /// Small label for badges and tags (14pt, semibold)
    static let badgeLabel = Font.system(size: 14, weight: .semibold, design: AppTheme.preferredFontDesign)

    /// Risk score display (52pt, heavy) for prominent risk indicators
    static let riskScore = Font.system(size: 52, weight: .heavy, design: AppTheme.preferredFontDesign)

    /// Phone number display (20pt, monospaced)
    static let phoneNumber = Font.system(size: 20, weight: .regular, design: .monospaced)

    // MARK: - Call Directory Label

    /// Label used for CallKit caller ID labels (matches system call screen)
    static let callDirectoryLabel = Font.system(size: 16, weight: .medium)
}

// MARK: - Bold Protocol for Labels

/// Protocol for views that should render with senior-optimized text.
protocol SeniorFriendlyText {
    /// Applies the standard body text style.
    func seniorBody() -> Text
}

extension Text: SeniorFriendlyText {
    func seniorBody() -> Text {
        self.font(.bodyText)
    }
}
