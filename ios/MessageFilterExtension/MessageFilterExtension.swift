import IdentityLookup

/// SafeRing SMS Message Filter Extension
///
/// This extension intercepts incoming SMS messages before they reach the user's inbox.
/// It runs in a sandboxed environment with NO network access, so all classification
/// happens on-device using keywords + pattern matching.
///
/// # Architecture
/// - No network access (Apple sandbox restriction)
/// - Uses keyword-based classification (CoreML models can't be loaded in extensions easily)
/// - Returns .junk for high-confidence scam messages → iOS moves them to "Junk" folder
/// - Returns .none for everything else → normal delivery
///
/// # Classification Pipeline
/// 1. URL detection (shorteners, suspicious TLDs, raw IPs)
/// 2. Urgency pattern matching ("act now", "verify immediately")
/// 3. Financial keyword matching (gift cards, wire transfer, crypto)
/// 4. Impersonation patterns (IRS, bank, Apple, Amazon)
/// 5. Composite scoring with threshold
///
/// # Privacy
/// - Message body is processed entirely on-device in the extension sandbox
/// - No data leaves the device during filtering
/// - The main app can later report events when the user opens SafeRing
final class MessageFilterExtension: ILMessageFilterQueryHandling, ILMessageFilterCapabilitiesQuerying {

    // MARK: - Capabilities

    func queryCapabilitiesForRequest(
        _ request: ILMessageFilterCapabilitiesQueryRequest,
        context: ILMessageFilterCapabilitiesQueryContext,
        completion: @escaping (ILMessageFilterCapabilitiesQueryResponse) -> Void
    ) {
        let response = ILMessageFilterCapabilitiesQueryResponse()
        response.capabilities = .filter
        completion(response)
    }

    // MARK: - Query Handling

    func queryRequest(
        _ request: ILMessageFilterQueryRequest,
        context: ILMessageFilterQueryContext,
        completion: @escaping (ILMessageFilterQueryResponse) -> Void
    ) {
        let response = ILMessageFilterQueryResponse()

        // Extract message body
        guard let messageBody = request.messageBody else {
            response.action = .none
            completion(response)
            return
        }

        let lowercased = messageBody.lowercased()

        // Run classification pipeline
        let score = classifyMessage(lowercased, originalBody: messageBody)

        if score >= 0.85 {
            // High confidence scam → filter to junk
            response.action = .filter
        } else {
            response.action = .none
        }

        completion(response)
    }

    // MARK: - Classification Engine

    /// Returns a scam confidence score from 0.0 to 1.0.
    /// Threshold for filtering: 0.85
    private func classifyMessage(_ body: String, originalBody: String) -> Double {
        var score: Double = 0.0

        // 1. URL analysis
        score += scoreURLs(originalBody)

        // 2. Urgency patterns (max +0.25)
        score += scoreUrgency(body)

        // 3. Financial/payment keywords (max +0.30)
        score += scoreFinancial(body)

        // 4. Impersonation patterns (max +0.30)
        score += scoreImpersonation(body)

        // 5. General scam indicators (max +0.20)
        score += scoreGeneralScam(body)

        // 6. Bonus: multiple categories triggered = higher confidence
        let categoriesTriggered = countCategories(body)
        if categoriesTriggered >= 3 {
            score += 0.15
        } else if categoriesTriggered >= 2 {
            score += 0.08
        }

        return min(score, 1.0)
    }

    // MARK: - URL Scoring

    private func scoreURLs(_ body: String) -> Double {
        var score: Double = 0.0

        // Shortener services (high scam signal)
        let shorteners = ["bit.ly", "tinyurl", "t.co", "goo.gl", "is.gd", "ow.ly", "rebrand.ly"]
        for s in shorteners {
            if body.contains(s) {
                score += 0.20
                break
            }
        }

        // Suspicious TLDs
        let suspiciousTLDs = [".xyz", ".top", ".buzz", ".club", ".work", ".click", ".link", ".gq", ".tk", ".ml", ".cf"]
        for tld in suspiciousTLDs {
            if body.contains(tld) {
                score += 0.15
                break
            }
        }

        // Raw IP addresses in URLs
        let ipPattern = #"\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"#
        if let regex = try? NSRegularExpression(pattern: ipPattern),
           regex.firstMatch(in: body, range: NSRange(body.startIndex..., in: body)) != nil {
            score += 0.20
        }

        // URL present at all (moderate signal)
        let urlPattern = #"https?://[^\s]+"#
        if let regex = try? NSRegularExpression(pattern: urlPattern),
           regex.numberOfMatches(in: body, range: NSRange(body.startIndex..., in: body)) > 0 {
            score += 0.05
        }

        return min(score, 0.35)
    }

    // MARK: - Urgency Scoring

    private func scoreUrgency(_ body: String) -> Double {
        let urgencyPhrases = [
            "act now", "action required", "immediately", "urgent",
            "respond now", "reply immediately", "don't delay", "do not delay",
            "time sensitive", "final notice", "last warning", "expire today",
            "expires today", "limited time", "within 24 hours", "within 12 hours",
            "your account will be", "failure to respond", "will be suspended",
            "will be closed", "will be locked", "verify now", "confirm now"
        ]

        var matches = 0
        for phrase in urgencyPhrases {
            if body.contains(phrase) { matches += 1 }
        }

        return min(Double(matches) * 0.08, 0.25)
    }

    // MARK: - Financial Scoring

    private func scoreFinancial(_ body: String) -> Double {
        let financialTerms = [
            "gift card", "gift cards", "itunes card", "apple card", "google play card",
            "wire transfer", "western union", "moneygram", "money order",
            "bitcoin", "cryptocurrency", "crypto wallet", "btc",
            "paypal", "venmo", "cashapp", "zelle", "cash app",
            "bank account", "routing number", "account number", "ssn",
            "social security", "credit card", "debit card", "cvv",
            "wire money", "send money", "transfer funds"
        ]

        var matches = 0
        for term in financialTerms {
            if body.contains(term) { matches += 1 }
        }

        return min(Double(matches) * 0.10, 0.30)
    }

    // MARK: - Impersonation Scoring

    private func scoreImpersonation(_ body: String) -> Double {
        let impersonationPatterns: [(String, Double)] = [
            ("irs", 0.20),
            ("internal revenue", 0.20),
            ("social security administration", 0.20),
            ("medicare", 0.15),
            ("fedex", 0.12),
            ("ups", 0.10),
            ("usps", 0.12),
            ("dhl", 0.10),
            ("amazon", 0.12),
            ("apple support", 0.15),
            ("apple id", 0.15),
            ("icloud", 0.12),
            ("microsoft", 0.12),
            ("netflix", 0.10),
            ("your bank", 0.15),
            ("chase bank", 0.15),
            ("wells fargo", 0.15),
            ("bank of america", 0.15),
            ("wells fargo", 0.15),
            ("capital one", 0.15),
        ]

        var maxScore: Double = 0.0
        for (pattern, weight) in impersonationPatterns {
            if body.contains(pattern) {
                maxScore = max(maxScore, weight)
            }
        }

        return min(maxScore, 0.30)
    }

    // MARK: - General Scam Scoring

    private func scoreGeneralScam(_ body: String) -> Double {
        let scamIndicators = [
            "you won", "you've won", "you are the winner", "congratulations",
            "lottery", "prize", "sweepstakes", "jackpot",
            "inheritance", "beneficiary", "next of kin",
            "nigerian prince", "million dollars", "usd", "$1,000,000",
            "click here", "tap here", "click the link", "click below",
            "free money", "government grant", "stimulus",
            "work from home", "make money fast", "earn $",
            "dating", "lonely", "soulmate", "romance",
            "virus detected", "malware detected", "your device is infected",
            "tech support", "call this number", "call us at"
        ]

        var matches = 0
        for indicator in scamIndicators {
            if body.contains(indicator) { matches += 1 }
        }

        return min(Double(matches) * 0.06, 0.20)
    }

    // MARK: - Category Counter

    /// Count how many distinct scam categories are triggered.
    private func countCategories(_ body: String) -> Int {
        var count = 0

        // Urgency
        let urgency = ["act now", "immediately", "urgent", "final notice", "verify now", "action required"]
        if urgency.contains(where: { body.contains($0) }) { count += 1 }

        // Financial
        let financial = ["gift card", "wire transfer", "bitcoin", "cryptocurrency", "western union", "zelle", "venmo"]
        if financial.contains(where: { body.contains($0) }) { count += 1 }

        // Impersonation
        let impersonation = ["irs", "social security", "amazon", "apple", "microsoft", "fedex", "usps"]
        if impersonation.contains(where: { body.contains($0) }) { count += 1 }

        // Prize/winner
        let prizes = ["you won", "congratulations", "lottery", "prize", "winner"]
        if prizes.contains(where: { body.contains($0) }) { count += 1 }

        // URL present
        if body.contains("http://") || body.contains("https://") { count += 1 }

        return count
    }
}
