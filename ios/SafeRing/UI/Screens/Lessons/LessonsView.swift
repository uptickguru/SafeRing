import SwiftUI

/// Lessons screen with senior-friendly, offline-readable scam awareness content.
///
/// # Security
/// This screen is OFFLINE-ONLY. No data is collected. No analytics. No PII.
///
/// # Senior-Friendly Design
/// - Large text (≥18pt) for readability
/// - One idea per card
/// - Simple language
/// - No login required
/// - Offline-readable
struct LessonsView: View {

    // MARK: - Properties

    @State private var selectedTab = 0

    enum Tab: CaseIterable {
        case callbackRules
        case warningSigns
        case neverGive
        case familyPassword

        var title: String {
            switch self {
            case .callbackRules: return "Callback Rules"
            case .warningSigns: return "Warning Signs"
            case .neverGive: return "Never Give"
            case .familyPassword: return "Family Password"
            }
        }

        var icon: String {
            switch self {
            case .callbackRules: return "phone.arrow.forward"
            case .warningSigns: return "exclamationmark.triangle"
            case .neverGive: return "lock.shield"
            case .familyPassword: return "person.2"
            }
        }
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                TabView(selection: $selectedTab) {
                    ForEach(Tab.allCases, id: \.self) { tab in
                        lessonsView(for: tab)
                            .tabItem {
                                Label(tab.title, systemImage: tab.icon)
                            }
                            .tag(tab)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .frame(maxHeight: .infinity)

                // No-login badge
                HStack {
                    Image(systemName: "shield.checkmark.fill")
                        .font(.title3)
                    Text("No login required")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(.top, 16)
                .padding(.horizontal, 24)
            }
            .navigationTitle("Safety Lessons")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        // Dismiss
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private func lessonsView(for tab: Tab) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                switch tab {
                case .callbackRules:
                    callbackRulesLessons()
                case .warningSigns:
                    warningSignsLessons()
                case .neverGive:
                    neverGiveLessons()
                case .familyPassword:
                    familyPasswordLessons()
                }
            }
            .padding()
        }
    }

    // MARK: - Callback Rules

    private func callbackRulesLessons() -> some View {
        VStack(alignment: .leading, spacing: 20) {
            header(title: "Callback Rules", icon: "phone.arrow.forward", color: .accentColor)

            lessonCard(icon: "arrow.right", title: "Rule 1: Never Call Back", body: "If someone asks you to call back, DON'T. Just hang up.")
            lessonCard(icon: "phone.fill", title: "Rule 2: Use Your Own Number", body: "Only dial numbers you ALREADY have saved in your contacts.")
            lessonCard(icon: "person.badge.questionmark", title: "Rule 3: Call the Family", body: "If you're unsure, call a family member you trust. They can help you decide.")

            Divider()

            Text("These rules protect you from scammers.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    // MARK: - Warning Signs

    private func warningSignsLessons() -> some View {
        VStack(alignment: .leading, spacing: 20) {
            header(title: "Warning Signs", icon: "exclamationmark.triangle", color: .warningYellow)

            warningSignCard(icon: "clock", title: "Urgency", body: "Scammers push you to act FAST. They say 'you'll lose it' or 'act now!'")
            warningSignCard(icon: "lock", title: "Secrecy", body: "Scammers say 'don't tell anyone' or 'this is private.'")
            warningSignCard(icon: "creditcard", title: "Odd Payment", body: "Scammers want payment in weird ways (gift cards, crypto, wire transfers).")
        }
    }

    // MARK: - Never Give

    private func neverGiveLessons() -> some View {
        VStack(alignment: .leading, spacing: 20) {
            header(title: "Never Give", icon: "lock.shield", color: .accentColor)

            neverGiveItem(icon: "creditcard", title: "Bank Account Numbers", body: "Never give your bank account number to a phone call.")
            neverGiveItem(icon: "key.fill", title: "Passwords", body: "Never give your passwords to anyone over the phone.")
            neverGiveItem(icon: "envelope", title: "Security Questions", body: "Never answer security questions to a stranger.")
            neverGiveItem(icon: "person.fill", title: "Personal Info", body: "Never share your address or birthday with a caller.")
        }
    }

    // MARK: - Family Password

    private func familyPasswordLessons() -> some View {
        VStack(alignment: .leading, spacing: 20) {
            header(title: "Family Password", icon: "person.2", color: .accentColor)

            lessonCard(icon: "person.fill", title: "Pick a Trusted Family Member", body: "Choose one person you trust. They can help you when you're unsure.")
            lessonCard(icon: "phone.fill", title: "Use the App to Check", body: "When in doubt, use SafeRing to check the number first.")
            lessonCard(icon: "shield.checkmark", title: "Ask Before You Call", body: "Before calling anyone, ask your trusted person: 'Should I call this number?'")

            Divider()

            Text("Your family password is a safety net.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    // MARK: - Components

    private func header(title: String, icon: String, color: Color) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
                .frame(width: 40)

            Text(title)
                .font(.title2.bold())
        }
    }

    private func lessonCard(icon: String, title: String, body: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.largeTitle)
                .foregroundColor(.accentColor)

            Text(title)
                .font(.headline)

            Text(body)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.05), radius: 8)
        )
    }

    private func warningSignCard(icon: String, title: String, body: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.largeTitle)
                .foregroundColor(.warningYellow)

            Text(title)
                .font(.headline)

            Text(body)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.05), radius: 8)
        )
    }

    private func neverGiveItem(icon: String, title: String, body: String) -> some View {