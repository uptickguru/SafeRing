import Foundation
import IdentityLookup

/// Shared App Group rules: keywords, allow/block senders, network flag.
/// Used by Message Filter extension + main app Settings.
final class FilterRulesStore {
    static let shared = FilterRulesStore()

    static let appGroupID = "group.online.db1k.safering.ios"
    private let defaults: UserDefaults
    private let logURL: URL?

    private enum Keys {
        static let keywords = "mf.keywords"
        static let allow = "mf.allow_senders"
        static let block = "mf.block_senders"
        static let network = "mf.network_assist"
        static let exceptional = "mf.exceptional_capture"
        static let seeded = "mf.seeded_v1"
    }

    /// Default senior-focused scam phrases (editable in Settings / future remote pack offline).
    static let defaultKeywords: [String] = [
        "gift card", "itunes card", "wire transfer", "western union", "moneygram",
        "bitcoin", "usdt", "crypto", "social security", "your ssn", "irs ",
        "warrant for your arrest", "account suspended", "verify your account",
        "unusual activity", "final notice", "act now", "limited time",
        "grandson", "granddaughter", "i'm in jail", "i am in jail", "bail money",
        "don't tell", "do not tell", "keep this secret", "anydesk", "teamviewer",
        "microsoft support", "apple support", "package held", "delivery fee",
        "unpaid postage", "click here", "tap to pay", "remote access",
        "bank account", "routing number", "one-time password share",
        "confirm your identity", "suspended account", "prize winner", "you have won"
    ]

    private init() {
        if let ud = UserDefaults(suiteName: Self.appGroupID) {
            defaults = ud
        } else {
            defaults = .standard
        }
        if let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Self.appGroupID
        ) {
            logURL = container.appendingPathComponent("mf_decisions.jsonl")
        } else {
            logURL = nil
        }
        seedIfNeeded()
    }

    private func seedIfNeeded() {
        if defaults.bool(forKey: Keys.seeded) { return }
        if keywords.isEmpty {
            keywords = Self.defaultKeywords
        }
        defaults.set(true, forKey: Keys.seeded)
    }

    // MARK: - Keywords

    var keywords: [String] {
        get {
            (defaults.stringArray(forKey: Keys.keywords) ?? []).map {
                $0.trimmingCharacters(in: .whitespacesAndNewlines)
            }.filter { !$0.isEmpty }
        }
        set {
            let cleaned = newValue
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                .filter { !$0.isEmpty }
            defaults.set(Array(Set(cleaned)).sorted(), forKey: Keys.keywords)
        }
    }

    func firstMatchingKeyword(in text: String) -> String? {
        let lower = text.lowercased()
        for k in keywords where !k.isEmpty {
            if lower.contains(k) { return k }
        }
        return nil
    }

    func looksLikePhishingLinkCombo(body: String) -> Bool {
        let lower = body.lowercased()
        let hasLink = lower.contains("http://") || lower.contains("https://") || lower.contains("www.")
        guard hasLink else { return false }
        let urgency = ["urgent", "immediately", "act now", "suspend", "verify", "confirm", "locked", "expire"]
        return urgency.contains { lower.contains($0) }
    }

    // MARK: - Senders

    /// Digits-only fingerprints and raw lowercased handles.
    var allowedSenders: [String] {
        get { defaults.stringArray(forKey: Keys.allow) ?? [] }
        set { defaults.set(Array(Set(newValue.map(Self.normalizeSender))).sorted(), forKey: Keys.allow) }
    }

    var blockedSenders: [String] {
        get { defaults.stringArray(forKey: Keys.block) ?? [] }
        set { defaults.set(Array(Set(newValue.map(Self.normalizeSender))).sorted(), forKey: Keys.block) }
    }

    var networkAssistEnabled: Bool {
        get { defaults.bool(forKey: Keys.network) }
        set { defaults.set(newValue, forKey: Keys.network) }
    }

    /// Explicit opt-in: allow encrypted exceptional capture to ops for OSINT.
    var exceptionalCaptureEnabled: Bool {
        get { defaults.bool(forKey: Keys.exceptional) }
        set { defaults.set(newValue, forKey: Keys.exceptional) }
    }

    func isAllowedSender(_ sender: String) -> Bool {
        let n = Self.normalizeSender(sender)
        guard !n.isEmpty else { return false }
        return allowedSenders.contains(n) || allowedSenders.contains(where: { n.hasSuffix($0) || $0.hasSuffix(n) })
    }

    func isBlockedSender(_ sender: String) -> Bool {
        let n = Self.normalizeSender(sender)
        guard !n.isEmpty else { return false }
        return blockedSenders.contains(n) || blockedSenders.contains(where: { n.hasSuffix($0) || $0.hasSuffix(n) })
    }

    func addBlockedSender(_ sender: String) {
        var b = blockedSenders
        let n = Self.normalizeSender(sender)
        guard !n.isEmpty else { return }
        if !b.contains(n) { b.append(n) }
        blockedSenders = b
    }

    func addAllowedSender(_ sender: String) {
        var a = allowedSenders
        let n = Self.normalizeSender(sender)
        guard !n.isEmpty else { return }
        if !a.contains(n) { a.append(n) }
        allowedSenders = a
    }

    /// Seed family trusted number into allow list (call from main app onboarding/settings).
    func syncTrustedContactE164(_ e164: String) {
        addAllowedSender(e164)
    }

    static func normalizeSender(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = trimmed.filter(\.isNumber)
        if digits.count >= 10 {
            if digits.count == 10 { return "1" + digits }
            if digits.hasPrefix("1") && digits.count == 11 { return digits }
            return digits
        }
        return trimmed.lowercased()
    }

    // MARK: - Decision log (privacy: sender normalized/redacted prefix only)

    func recordDecision(sender: String, action: String, reason: String) {
        guard let logURL else { return }
        let norm = Self.normalizeSender(sender)
        let redacted = norm.count > 6 ? String(norm.prefix(3)) + "…" + String(norm.suffix(2)) : "—"
        let line = [
            "ts": ISO8601DateFormatter().string(from: Date()),
            "action": action,
            "reason": reason,
            "sender": redacted
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: line),
              var str = String(data: data, encoding: .utf8) else { return }
        str += "\n"
        if !FileManager.default.fileExists(atPath: logURL.path) {
            try? str.write(to: logURL, atomically: true, encoding: .utf8)
        } else if let handle = try? FileHandle(forWritingTo: logURL) {
            defer { try? handle.close() }
            try? handle.seekToEnd()
            if let d = str.data(using: .utf8) { try? handle.write(contentsOf: d) }
        }
    }

    func recentDecisionCount(limit: Int = 50) -> Int {
        guard let logURL,
              let text = try? String(contentsOf: logURL, encoding: .utf8) else { return 0 }
        return min(limit, text.split(separator: "\n").count)
    }
}
