import SwiftUI

/// Edit Message Filter keyword list (App Group → extension).
struct KeywordEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var keywords: [String] = FilterRulesStore.shared.keywords
    @State private var draft = ""
    @State private var blocked: [String] = FilterRulesStore.shared.blockedSenders
    @State private var blockDraft = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("If a text contains any of these phrases, SafeRing marks it Junk (when SMS Filtering is on).")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Keywords") {
                    ForEach(keywords, id: \.self) { k in
                        Text(k)
                    }
                    .onDelete { idx in
                        keywords.remove(atOffsets: idx)
                        persist()
                    }
                    HStack {
                        TextField("Add phrase", text: $draft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button("Add") {
                            let t = draft.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                            guard !t.isEmpty, !keywords.contains(t) else { return }
                            keywords.append(t)
                            draft = ""
                            persist()
                        }
                    }
                    Button("Reset to SafeRing defaults") {
                        keywords = FilterRulesStore.defaultKeywords
                        persist()
                    }
                    .foregroundStyle(.orange)
                }

                Section("Blocked senders (digits)") {
                    Text("Numbers here are junked even without keyword hits.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(blocked, id: \.self) { b in
                        Text(b)
                            .font(.system(.body, design: .monospaced))
                    }
                    .onDelete { idx in
                        blocked.remove(atOffsets: idx)
                        FilterRulesStore.shared.blockedSenders = blocked
                    }
                    HStack {
                        TextField("+1…", text: $blockDraft)
                            .keyboardType(.phonePad)
                        Button("Block") {
                            FilterRulesStore.shared.addBlockedSender(blockDraft)
                            blocked = FilterRulesStore.shared.blockedSenders
                            blockDraft = ""
                        }
                    }
                }
            }
            .navigationTitle("Filter words")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func persist() {
        FilterRulesStore.shared.keywords = keywords
    }
}

/// Exceptional HITL: send encrypted (or https bring-up) sample for OSINT, seed junk fingerprint.
struct ExceptionalReportView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var sender = ""
    @State private var bodyText = ""
    @State private var note = "Repeat unknown texter — identify & fingerprint"
    @State private var label = "family-exceptional"
    @State private var consent = false
    @State private var busy = false
    @State private var result: String?
    @State private var err: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Only for exceptional cases (e.g. family getting repeat scam texts from the same unknown number). You must agree before anything leaves the phone.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section("What to send") {
                    TextField("Sender number", text: $sender)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                    TextField("Message text", text: $bodyText, axis: .vertical)
                        .lineLimit(4...10)
                    TextField("Note for ops", text: $note, axis: .vertical)
                    TextField("Household label (no real name needed)", text: $label)
                }
                Section {
                    Toggle("I request investigation of this number/message for our family’s protection", isOn: $consent)
                    Text("We store a private fingerprint immediately so future texts can go to Junk while ops identify the sender. Prefer encrypted capture when server keys are configured.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let result {
                    Section("Result") {
                        Text(result).font(.footnote)
                    }
                }
                if let err {
                    Section {
                        Text(err).foregroundStyle(.red).font(.footnote)
                    }
                }
                Section {
                    Button(busy ? "Sending…" : "Send for investigation") {
                        Task { await send() }
                    }
                    .disabled(!consent || sender.filter(\.isNumber).count < 10 || bodyText.isEmpty || busy)
                }
            }
            .navigationTitle("Investigate number")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private func send() async {
        busy = true
        err = nil
        result = nil
        defer { busy = false }
        do {
            FilterRulesStore.shared.exceptionalCaptureEnabled = true
            let id = try await ExceptionalCaptureService.submit(
                senderE164: sender,
                messageBody: bodyText,
                note: note,
                householdLabel: label
            )
            result = "Case \(id). Fingerprint seeded for Junk. Ops can OSINT, then confirm block."
        } catch {
            err = error.localizedDescription
        }
    }
}
