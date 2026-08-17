import SwiftUI

struct PasteCheckView: View {
    @ObservedObject var household: HouseholdStore
    var onNeedHelp: (HelpReason) -> Void
    var onCallPerson: () -> Void
    var initialText: String = ""
    var initialSender: String = ""

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var sender = ""
    @State private var result: ScamCheckResult?
    @State private var foundPhones: [String] = []
    @State private var showPassword = false

    private var person: String {
        household.trustedContactName.isEmpty ? "my person" : household.trustedContactName
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: AppTheme.spacingLG) {
                    Text("Paste a text or email. It stays on this phone. Add who it was from if you know.")
                        .font(.bodyText)
                        .foregroundColor(Color("secondaryText"))

                    TextField("From / return number (optional)", text: $sender)
                        .font(.bodyText)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .padding(12)
                        .background(Color("cardBackground"))
                        .cornerRadius(AppTheme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                                .stroke(Color("secondaryText").opacity(0.2), lineWidth: 1)
                        )
                        .accessibilityLabel("Sender or return phone number")

                    TextEditor(text: $text)
                        .font(.bodyText)
                        .frame(minHeight: 180)
                        .padding(8)
                        .background(Color("cardBackground"))
                        .cornerRadius(AppTheme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: AppTheme.cornerRadius)
                                .stroke(Color("secondaryText").opacity(0.2), lineWidth: 1)
                        )
                        .accessibilityLabel("Message to check")

                    BigButton(
                        title: "Check this",
                        icon: "magnifyingglass",
                        action: runCheck,
                        isDisabled: text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    )

                    if !foundPhones.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Numbers found in the message")
                                .font(.badgeLabel)
                            ForEach(foundPhones, id: \.self) { p in
                                Text("· \(PhoneNumberUtils.pretty(p))")
                                    .font(.bodyText)
                            }
                            Text("Never call a number from a suspicious text — call \(person) on the number you saved.")
                                .font(.captionText)
                                .foregroundColor(Color("secondaryText"))
                        }
                    }

                    if let result {
                        resultCard(result)
                    }
                }
                .padding()
            }
            .background(Color("appBackground"))
            .navigationTitle("Check this")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onAppear {
                if text.isEmpty, !initialText.isEmpty {
                    text = initialText
                }
                if sender.isEmpty, !initialSender.isEmpty {
                    sender = initialSender
                }
                if result == nil, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    runCheck()
                }
            }
            .alert("Ask them the family password", isPresented: $showPassword) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Ask the person on the phone for your family password. Do not say the password first. If they do not know it, hang up and call \(person) with the green button.")
            }
        }
    }

    private func runCheck() {
        let extracted = PhoneNumberUtils.extractPhones(from: text)
        foundPhones = extracted
        if sender.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, let first = extracted.first {
            sender = PhoneNumberUtils.pretty(first)
        }
        result = OnDeviceScamChecker.check(text)
        // Label-only note: iOS main app cannot read SMS inbox sender; optional field + body parse is the elegant path.
        Logger.shared.info(
            "Paste check phones=\(extracted.count) senderSet=\(!sender.isEmpty)",
            category: .sms
        )
    }

    @ViewBuilder
    private func resultCard(_ result: ScamCheckResult) -> some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingMD) {
            HStack {
                Image(systemName: icon(for: result.verdict))
                    .foregroundColor(color(for: result.verdict))
                    .font(.title)
                VStack(alignment: .leading, spacing: 4) {
                    Text(result.verdict.title)
                        .font(.sectionTitle)
                    Text("This is not a guarantee. When money is involved, get your person.")
                        .font(.captionText)
                        .foregroundColor(Color("secondaryText"))
                }
            }

            if !sender.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text("From: \(sender)")
                    .font(.bodyText)
                    .foregroundColor(Color("secondaryText"))
            }

            ForEach(result.reasons, id: \.self) { reason in
                Label(reason, systemImage: "exclamationmark.circle")
                    .font(.bodyText)
            }

            if !result.urls.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Links we found")
                        .font(.badgeLabel)
                    ForEach(result.urls, id: \.absoluteString) { url in
                        Text(url.absoluteString)
                            .font(.captionText)
                            .foregroundColor(Color("secondaryText"))
                            .textSelection(.enabled)
                    }
                }
            }

            if result.verdict != .looksOkay {
                BigButton.destructive(
                    title: "Get my person",
                    icon: "bell.badge.fill",
                    action: { onNeedHelp(.pasteScam) }
                )
                BigButton.success(
                    title: "Call \(person) for real",
                    icon: "phone.fill",
                    action: onCallPerson
                )
                Button("Ask them the family password") {
                    showPassword = true
                }
                .font(.buttonLabel)
                .frame(maxWidth: .infinity)
            } else {
                BigButton(
                    title: "Still verify with my person",
                    icon: "person.2.fill",
                    action: { onNeedHelp(.verify) }
                )
            }
        }
        .padding(AppTheme.spacingLG)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(color(for: result.verdict).opacity(0.12))
        .cornerRadius(AppTheme.cornerRadius)
    }

    private func icon(for verdict: ScamCheckResult.Verdict) -> String {
        switch verdict {
        case .likelyScam: return "xmark.shield.fill"
        case .suspicious: return "exclamationmark.shield.fill"
        case .looksOkay: return "checkmark.shield.fill"
        }
    }

    private func color(for verdict: ScamCheckResult.Verdict) -> Color {
        switch verdict {
        case .likelyScam: return Color("criticalRed")
        case .suspicious: return Color("highRiskOrange")
        case .looksOkay: return Color("safeGreen")
        }
    }
}
