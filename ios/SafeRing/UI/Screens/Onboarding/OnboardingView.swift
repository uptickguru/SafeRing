import SwiftUI

/// Three-step onboarding wizard for first-time users.
///
/// # Senior-Friendly Design
/// - 3 screens, 30 seconds max
/// - Large illustrations and text
/// - Single tap to grant permissions
/// - Clear, encouraging messaging
/// - No login or account creation
///
/// # Steps
/// 1. Welcome & Permission Granting
/// 2. Call Protection Setup (CallKit)
/// 3. All Set — Notification & SMS Permission
///
struct OnboardingView: View {

    // MARK: - Properties

    let onComplete: () -> Void

    @State private var currentStep: Int = 0
    @State private var animateContent = false

    // MARK: - Steps

    private let steps: [OnboardingStep] = [
        OnboardingStep(
            title: "Meet SafeRing",
            subtitle: "AI-powered protection against phone scams",
            icon: "shield.checkered",
            color: Color("safeGreen"),
            description: "SafeRing automatically screens your calls and text messages for known scam patterns. No setup required — just grant permission and you're protected.",
            actionLabel: "Get Started"
        ),
        OnboardingStep(
            title: "Call Protection",
            subtitle: "Stop scams before they ring",
            icon: "phone.badge.waveform.fill",
            color: AppTheme.accentColor,
            description: "SafeRing will check every incoming call against our scam database. Scam numbers are identified before you answer, and confirmed scams are blocked automatically.",
            actionLabel: "Enable Call Screening"
        ),
        OnboardingStep(
            title: "You're All Set!",
            subtitle: "Nothing else to configure",
            icon: "hand.wave.fill",
            color: Color("safeGreen"),
            description: "SafeRing works silently in the background. You can review blocked calls and report new scams from the app anytime. Stay safe! 🛡️",
            actionLabel: "Start Protection"
        ),
    ]

    // MARK: - Body

    var body: some View {
        ZStack {
            Color("appBackground").ignoresSafeArea()

            VStack {
                // Skip button
                skipButton

                Spacer()

                // Step Content
                TabView(selection: $currentStep) {
                    ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                        stepView(step)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.5), value: currentStep)

                Spacer()

                // Navigation
                VStack(spacing: AppTheme.spacingMD) {
                    // Page Dots
                    HStack(spacing: 12) {
                        ForEach(0..<steps.count, id: \.self) { index in
                            Circle()
                                .fill(currentStep == index ? AppTheme.accentColor : Color("secondaryText").opacity(0.3))
                                .frame(width: 12, height: 12)
                                .animation(.spring(), value: currentStep)
                        }
                    }

                    // Action Button
                    BigButton(
                        title: steps[currentStep].actionLabel,
                        icon: currentStep == steps.count - 1 ? "checkmark" : "arrow.right",
                        action: handleAction,
                        color: steps[currentStep].color
                    )
                    .padding(.horizontal)

                    // Back button (not on first step)
                    if currentStep > 0 {
                        Button("Back") {
                            withAnimation {
                                currentStep -= 1
                            }
                        }
                        .font(.bodyText)
                        .foregroundColor(Color("secondaryText"))
                    }
                }
                .padding(.bottom, 40)
            }
        }
    }

    // MARK: - Skip Button

    private var skipButton: some View {
        HStack {
            Spacer()
            if currentStep < steps.count - 1 {
                Button("Skip") {
                    completeOnboarding()
                }
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .padding()
            }
        }
    }

    // MARK: - Step View

    private func stepView(_ step: OnboardingStep) -> some View {
        VStack(spacing: AppTheme.spacingXL) {
            // Icon
            Image(systemName: step.icon)
                .font(.system(size: 80))
                .foregroundColor(step.color)
                .symbolEffect(.bounce, value: currentStep)

            // Text Content
            VStack(spacing: AppTheme.spacingSM) {
                Text(step.title)
                    .font(.heroTitle)
                    .foregroundColor(Color("primaryText"))
                    .multilineTextAlignment(.center)

                Text(step.subtitle)
                    .font(.largeBody)
                    .foregroundColor(Color("secondaryText"))
                    .multilineTextAlignment(.center)
            }

            // Description
            Text(step.description)
                .font(.bodyText)
                .foregroundColor(Color("secondaryText"))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, AppTheme.spacingLG)
        }
        .padding(.horizontal)
    }

    // MARK: - Actions

    private func handleAction() {
        if currentStep == steps.count - 1 {
            completeOnboarding()
        } else {
            withAnimation {
                currentStep += 1
            }
        }
    }

    private func completeOnboarding() {
        withAnimation(.easeInOut(duration: 0.5)) {
            onComplete()
        }
    }
}

// MARK: - Onboarding Step Model

private struct OnboardingStep {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    let description: String
    let actionLabel: String
}

// MARK: - Preview

#Preview {
    OnboardingView(onComplete: { })
}
