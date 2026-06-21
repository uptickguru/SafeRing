import Foundation
import CoreML
import NaturalLanguage

/// On-device CoreML wrapper for SMS text classification.
///
/// # Zero PII
/// The SMS message body is processed **entirely on-device**. The raw text is
/// NEVER transmitted to any server. Only the hashed sender number may leave
/// the device during scam lookups.
///
/// # Architecture
/// When available, uses a quantized CoreML model (e.g., MobileBERT or DistilBERT)
/// for semantic analysis of SMS text. Falls back to keyword-based detection
/// when the model is not yet downloaded.
///
/// ## Classification Categories
/// - **Legitimate** — Normal, non-suspicious message
/// - **Spam** — Unsolicited commercial messages (annoying but not dangerous)
/// - **Scam** — Fraudulent messages attempting to steal money/data
///   - IRS/Tax Scam
///   - Tech Support Scam
///   - Grandparent Scam
///   - Romance Scam
///   - Phishing
///   - Package Delivery Scam
///   - Government Grant Scam
///
final class SmsClassifier {

    // MARK: - Properties

    /// The CoreML text classification model, if available.
    private var textModel: NLModel?

    /// Whether the CoreML model is loaded and ready.
    var isModelReady: Bool { textModel != nil }

    // MARK: - Initializer

    /// Initializes the SMS classifier.
    /// - Parameter modelURL: URL to the compiled CoreML text classifier model.
    ///   If nil, operates in keyword-only mode.
    init(modelURL: URL? = nil) {
        if let url = modelURL {
            do {
                self.textModel = try NLModel(contentsOf: url)
                Logger.shared.info("SMS classifier model loaded successfully", category: .ml)
            } catch {
                Logger.shared.warning(
                    "Failed to load SMS classifier model: \(error.localizedDescription). Using keyword-only mode.",
                    category: .ml
                )
                self.textModel = nil
            }
        } else {
            Logger.shared.info("SMS classifier running in keyword-only mode", category: .ml)
            self.textModel = nil
        }
    }

    // MARK: - Classification

    /// Classifies an SMS message for scam risk.
    ///
    /// - Parameter message: The raw SMS body text.
    /// - Returns: Classification result with scores.
    func classify(message: String) -> SmsClassifierResult {
        if let model = textModel {
            return classifyWithModel(model, message: message)
        } else {
            return keywordOnlyClassification(message: message)
        }
    }

    // MARK: - Model Inference

    /// Uses the CoreML NLModel to classify the message.
    private func classifyWithModel(_ model: NLModel, message: String) -> SmsClassifierResult {
        let label = model.predictedLabel(for: message) ?? "legitimate"

        // Convert NLModel label to our scoring format
        // NLModel returns the most likely label — we need to extract probabilities
        let hypothesis = model.predictedLabelHypotheses(for: message, maximumCount: 3)

        let scamScore: Double
        let spamScore: Double
        let legitimateScore: Double
        let confidence: Double
        let scamLabel: String?

        switch label.lowercased() {
        case "scam":
            scamScore = hypothesis["scam"] ?? 0.8
            spamScore = hypothesis["spam"] ?? 0.1
            legitimateScore = hypothesis["legitimate"] ?? 0.1
            confidence = scamScore
            scamLabel = extractScamType(from: message, model: model)
        case "spam":
            scamScore = hypothesis["scam"] ?? 0.1
            spamScore = hypothesis["spam"] ?? 0.7
            legitimateScore = hypothesis["legitimate"] ?? 0.2
            confidence = spamScore
            scamLabel = nil
        default: // legitimate
            scamScore = hypothesis["scam"] ?? 0.05
            spamScore = hypothesis["spam"] ?? 0.05
            legitimateScore = hypothesis["legitimate"] ?? 0.9
            confidence = legitimateScore
            scamLabel = nil
        }

        return SmsClassifierResult(
            scamScore: scamScore,
            spamScore: spamScore,
            legitimateScore: legitimateScore,
            confidence: confidence,
            scamLabel: scamLabel
        )
    }

    // MARK: - Keyword-Only Fallback

    /// Classification using local keyword matching when no ML model is available.
    private func keywordOnlyClassification(message: String) -> SmsClassifierResult {
        // Comprehensive scam keyword patterns
        let scamPatterns: [(pattern: String, type: String)] = [
            ("ssn", "Identity Theft"),
            ("social security", "Identity Theft"),
            ("irs", "IRS Impersonation"),
            ("tax refund", "IRS Impersonation"),
            ("gift card", "Payment Scam"),
            ("wire transfer", "Payment Scam"),
            ("western union", "Payment Scam"),
            ("moneygram", "Payment Scam"),
            ("cryptocurrency", "Investment Scam"),
            ("bitcoin", "Investment Scam"),
            ("you won", "Prize Scam"),
            ("lottery", "Prize Scam"),
            ("congratulations", "Prize Scam"),
            ("account suspended", "Account Phishing"),
            ("verify account", "Account Phishing"),
            ("unusual activity", "Account Phishing"),
            ("click here", "Phishing"),
            ("tech support", "Tech Support Scam"),
            ("virus detected", "Tech Support Scam"),
            ("microsoft", "Tech Support Scam"),
            ("grandson", "Grandparent Scam"),
            ("granddaughter", "Grandparent Scam"),
            ("family emergency", "Emergency Scam"),
            ("romance", "Romance Scam"),
            ("lonely", "Romance Scam"),
            ("stimulus", "Government Scam"),
            ("government grant", "Government Scam"),
            ("free money", "Government Scam"),
            ("fedex delivery", "Package Scam"),
            ("usps tracking", "Package Scam"),
            ("amazon order", "Package Scam"),
            ("work from home", "Job Scam"),
            ("make money fast", "Job Scam"),
            ("passive income", "Job Scam"),
        ]

        let lowercased = message.lowercased()
        var matchedTypes: Set<String> = []
        var detectionCount = 0

        for (pattern, type) in scamPatterns {
            if lowercased.contains(pattern) {
                matchedTypes.insert(type)
                detectionCount += 1
            }
        }

        // Spam keywords (less harmful but unwanted)
        let spamPatterns = [
            "limited time offer", "act now", "exclusive deal",
            "click below", "subscribe", "opt in", "you're selected",
            "earn extra cash", "side hustle", "cash today",
            "increase your income", "debt relief", "credit repair",
        ]

        let spamCount = spamPatterns.filter { lowercased.contains($0) }.count

        // Calculate scores
        let scamScore = min(Double(detectionCount) * 0.15, 0.8)
        let spamScore = min(Double(spamCount) * 0.12, 0.5)
        let legitimateScore = max(0, 1.0 - scamScore - spamScore)
        let confidence = scamScore > spamScore ? scamScore : max(spamScore, 0.3)

        return SmsClassifierResult(
            scamScore: scamScore,
            spamScore: spamScore,
            legitimateScore: legitimateScore,
            confidence: confidence,
            scamLabel: matchedTypes.isEmpty ? nil : matchedTypes.joined(separator: ", ")
        )
    }

    // MARK: - Scam Type Extraction

    /// Attempts to extract the specific scam type from the message.
    /// Uses the NLModel's tag scheme if available, otherwise falls back to keywords.
    private func extractScamType(from message: String, model: NLModel) -> String? {
        // Try the model's secondary label prediction
        if let typeLabel = model.predictedLabelHypotheses(for: message, maximumCount: 5)
            .filter({ $0.key != "scam" && $0.key != "spam" && $0.key != "legitimate" })
            .max(by: { $0.value < $1.value }) {
            return typeLabel.key
        }

        // Fallback: keyword-based type detection
        let lowercased = message.lowercased()
        if lowercased.contains("irs") || lowercased.contains("tax") { return "IRS Impersonation" }
        if lowercased.contains("gift card") || lowercased.contains("western union") { return "Payment Scam" }
        if lowercased.contains("grandson") || lowercased.contains("granddaughter") { return "Grandparent Scam" }
        if lowercased.contains("romance") || lowercased.contains("dating") { return "Romance Scam" }
        if lowercased.contains("tech support") || lowercased.contains("virus") { return "Tech Support" }
        if lowercased.contains("bitcoin") || lowercased.contains("crypto") { return "Crypto Scam" }
        if lowercased.contains("amazon") || lowercased.contains("fedex") || lowercased.contains("usps") { return "Package Scam" }
        if lowercased.contains("ssn") || lowercased.contains("social security") { return "Identity Theft" }

        return "General Scam"
    }

    // MARK: - Model Update

    /// Updates the CoreML text model with a newly downloaded version.
    /// - Parameter modelURL: URL to the new compiled model.
    /// - Returns: Whether the update was successful.
    func updateModel(from modelURL: URL) -> Bool {
        do {
            let newModel = try NLModel(contentsOf: modelURL)
            self.textModel = newModel
            Logger.shared.info("SMS classifier model updated", category: .ml)
            return true
        } catch {
            Logger.shared.error(
                "Failed to update SMS classifier model: \(error.localizedDescription)",
                category: .ml
            )
            return false
        }
    }
}
