import Foundation

struct ScamCheckResult {
    enum Verdict {
        case likelyScam
        case suspicious
        case looksOkay

        var title: String {
            switch self {
            case .likelyScam: return "Treat this as a scam"
            case .suspicious: return "This looks fishy"
            case .looksOkay: return "No obvious scam markers"
            }
        }
    }

    let verdict: Verdict
    let score: Double
    let reasons: [String]
    let urls: [URL]
    let recommendedAction: ThreatAction
}

enum OnDeviceScamChecker {

    private static let scamPhrases: [(String, String)] = [
        ("gift card", "Asks for a gift card"),
        ("wire transfer", "Asks for a wire"),
        ("western union", "Western Union payment"),
        ("moneygram", "MoneyGram payment"),
        ("bitcoin", "Crypto payment"),
        ("usdt", "Crypto payment"),
        ("itunes card", "Gift card payment"),
        ("social security", "Social Security impersonation"),
        ("your ssn", "Asks for SSN"),
        ("irs", "IRS impersonation"),
        ("warrant for your arrest", "Fake warrant"),
        ("account suspended", "Account-suspension phish"),
        ("verify your account", "Account-verification phish"),
        ("unusual activity", "Bank-impersonation phrasing"),
        ("final notice", "Urgency + final notice"),
        ("act now", "Urgency language"),
        ("limited time", "Urgency language"),
        ("grandson", "Grandparent-scam language"),
        ("granddaughter", "Grandparent-scam language"),
        ("i'm in jail", "Emergency / jail script"),
        ("i am in jail", "Emergency / jail script"),
        ("bail", "Bail-payment script"),
        ("don't tell", "Secrecy request"),
        ("do not tell", "Secrecy request"),
        ("keep this secret", "Secrecy request"),
        ("remote access", "Tech-support script"),
        ("anydesk", "Remote-access tool"),
        ("teamviewer", "Remote-access tool"),
        ("microsoft support", "Fake tech support"),
        ("apple support", "Fake Apple support"),
        ("usps", "Postal / package lure"),
        ("fedex", "Package lure"),
        ("dhl", "Package lure"),
        ("package held", "Package-hold phish"),
        ("delivery fee", "Fake delivery fee"),
        ("unpaid postage", "Fake postage fee"),
        ("click here", "Click-through lure"),
        ("tap to pay", "Payment lure"),
    ]

    private static let lookalikeHosts = [
        "usps": ["usps.com", "usps.gov"],
        "fedex": ["fedex.com"],
        "ups": ["ups.com"],
        "amazon": ["amazon.com", "amazon.co.uk"],
        "apple": ["apple.com", "icloud.com"],
        "paypal": ["paypal.com"],
        "irs": ["irs.gov"],
        "ssa": ["ssa.gov"],
        "microsoft": ["microsoft.com", "live.com", "office.com"],
        "chase": ["chase.com"],
        "wellsfargo": ["wellsfargo.com"],
        "bankofamerica": ["bankofamerica.com"],
    ]

    static func check(_ raw: String) -> ScamCheckResult {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = text.lowercased()
        var reasons: [String] = []
        var score = 0.0

        var matched = 0
        for (phrase, reason) in scamPhrases where lower.contains(phrase) {
            matched += 1
            if !reasons.contains(reason) {
                reasons.append(reason)
            }
        }
        score += min(Double(matched) * 0.16, 0.7)

        let urls = extractURLs(from: text)
        if urls.count >= 1, matched >= 1 {
            score += 0.15
            reasons.append("Contains a link plus scam language")
        }

        for url in urls {
            let host = (url.host ?? "").lowercased()
            if host.isEmpty { continue }
            if isShortener(host) {
                score += 0.2
                reasons.append("Uses a link shortener (\(host))")
            }
            if lookalikeHit(host) != nil {
                score += 0.35
                reasons.append("Link host looks like a brand impersonation (\(host))")
            }
            if url.scheme?.lowercased() != "https" {
                score += 0.08
                reasons.append("Link is not HTTPS")
            }
        }

        if lower.contains("http"), urls.isEmpty {
            reasons.append("Looks like it contains a link we could not parse")
            score += 0.05
        }

        score = min(score, 1.0)

        let verdict: ScamCheckResult.Verdict
        let action: ThreatAction
        if score >= 0.6 {
            verdict = .likelyScam
            action = lower.contains("gift") || lower.contains("wire") || lower.contains("bitcoin")
                ? .loopTrustedContact
                : .doNotReply
        } else if score >= 0.3 {
            verdict = .suspicious
            action = .looksOkStillVerify
        } else {
            verdict = .looksOkay
            action = .looksOkStillVerify
        }

        if reasons.isEmpty {
            reasons.append("No classic scam phrases or brand-lookalike links. Still verify anything about money.")
        }

        return ScamCheckResult(
            verdict: verdict,
            score: score,
            reasons: reasons,
            urls: urls,
            recommendedAction: action
        )
    }

    private static func extractURLs(from text: String) -> [URL] {
        let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        let range = NSRange(text.startIndex..., in: text)
        let matches = detector?.matches(in: text, options: [], range: range) ?? []
        return matches.compactMap { $0.url }
    }

    private static func isShortener(_ host: String) -> Bool {
        let shorteners = [
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly",
            "is.gd", "cutt.ly", "rebrand.ly", "rb.gy", "lnkd.in",
        ]
        return shorteners.contains(where: { host == $0 || host.hasSuffix(".\($0)") })
    }

    private static func lookalikeHit(_ host: String) -> String? {
        for (brand, legit) in lookalikeHosts {
            if legit.contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
                return nil
            }
            if host.contains(brand) {
                return brand
            }
        }
        return nil
    }
}
