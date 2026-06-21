import SwiftUI

/// Extra-large button designed for senior-friendly touch targets.
///
/// # Senior-Friendly Design
/// - Minimum 64pt height for easy tapping
/// - Bold, clear text
/// - Optional icon for visual recognition
/// - High contrast by default
/// - Loading state with spinner
///
struct BigButton: View {

    // MARK: - Properties

    let title: String
    let icon: String?
    let action: () -> Void
    var isLoading: Bool = false
    var color: Color = AppTheme.accentColor
    var isDisabled: Bool = false

    // MARK: - Body

    var body: some View {
        Button(action: action) {
            HStack(spacing: AppTheme.spacingSM) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.2)
                } else if let icon = icon {
                    Image(systemName: icon)
                        .font(.title3)
                        .imageScale(.medium)
                }

                Text(title)
                    .font(.largeButtonLabel)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .frame(height: AppTheme.xlTouchTarget)
            .foregroundColor(.white)
            .background(
                RoundedRectangle(cornerRadius: AppTheme.buttonCornerRadius)
                    .fill(isDisabled ? Color("secondaryText") : color)
            )
            .opacity(isDisabled ? 0.5 : 1.0)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled || isLoading)
        .accessibilityHint("Double tap to \(title.lowercased())")
    }

    // MARK: - Modifiers

    /// Creates a disabled-style button with reduced opacity.
    func disabled(_ disabled: Bool) -> BigButton {
        var copy = self
        copy.isDisabled = disabled
        return copy
    }
}

// MARK: - Primary Button Variant

extension BigButton {
    /// Primary action button with the app accent color.
    static func primary(
        title: String,
        icon: String? = nil,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) -> BigButton {
        BigButton(
            title: title,
            icon: icon,
            action: action,
            isLoading: isLoading,
            color: AppTheme.accentColor
        )
    }

    /// Destructive action button in red.
    static func destructive(
        title: String,
        icon: String? = nil,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) -> BigButton {
        BigButton(
            title: title,
            icon: icon,
            action: action,
            isLoading: isLoading,
            color: Color("criticalRed")
        )
    }

    /// Success/confirmation button in green.
    static func success(
        title: String,
        icon: String? = nil,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) -> BigButton {
        BigButton(
            title: title,
            icon: icon,
            action: action,
            isLoading: isLoading,
            color: Color("safeGreen")
        )
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 16) {
        BigButton(title: "Check Call", icon: "magnifyingglass", action: {})
        BigButton(title: "Loading...", icon: nil, action: {}, isLoading: true)
        BigButton.primary(title: "Primary Action", icon: "star", action: {})
        BigButton.destructive(title: "Report Scam", icon: "exclamationmark.shield", action: {})
        BigButton.success(title: "All Clear", icon: "checkmark", action: {})
        BigButton(title: "Disabled", icon: nil, action: {}).disabled(true)
    }
    .padding()
}
