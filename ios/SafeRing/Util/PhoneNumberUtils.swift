import Foundation

/// E.164 normalize + extract phones from free text (SMS body).
enum PhoneNumberUtils {
    private static let pattern: NSRegularExpression = {
        // US-centric 10/11 digit patterns with optional separators
        try! NSRegularExpression(
            pattern: #"(?<!\d)(?:\+?1[\s.-]?)?(?:\(\d{3}\)|\d{3})[\s.-]?\d{3}[\s.-]?\d{4}(?!\d)"#,
            options: []
        )
    }()

    static func normalizeToE164(_ raw: String) -> String {
        let digits = raw.filter(\.isNumber)
        if digits.hasPrefix("1"), digits.count == 11 { return "+\(digits)" }
        if digits.count == 10 { return "+1\(digits)" }
        if raw.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("+"), !digits.isEmpty {
            return "+\(digits)"
        }
        return digits.isEmpty ? raw : "+\(digits)"
    }

    static func isPlausibleE164(_ raw: String) -> Bool {
        let d = normalizeToE164(raw).filter(\.isNumber)
        return (10...15).contains(d.count)
    }

    static func pretty(_ raw: String) -> String {
        let n = normalizeToE164(raw)
        let d = String(n.filter(\.isNumber))
        guard d.count == 11, d.hasPrefix("1") else { return n }
        let a = d.dropFirst().prefix(3)
        let b = d.dropFirst(4).prefix(3)
        let c = d.suffix(4)
        return "(\(a)) \(b)-\(c)"
    }

    static func extractPhones(from text: String) -> [String] {
        guard !text.isEmpty else { return [] }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        let matches = pattern.matches(in: text, options: [], range: range)
        var ordered: [String] = []
        var seen = Set<String>()
        for m in matches {
            guard let r = Range(m.range, in: text) else { continue }
            let e164 = normalizeToE164(String(text[r]))
            guard isPlausibleE164(e164), !seen.contains(e164) else { continue }
            seen.insert(e164)
            ordered.append(e164)
        }
        return ordered
    }
}
