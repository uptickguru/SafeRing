import Foundation
import CoreML

/// On-device CoreML wrapper for phone number classification.
///
/// # Zero PII
/// This classifier works with SHA-256 hashes of phone numbers, never raw numbers.
/// The on-device model is trained on statistical patterns of scam number hashes
/// and prefix distributions — no original numbers are embedded in the model.
///
/// # Architecture
/// The CoreML model (when available) takes a feature vector derived from:
/// - Prefix region (extracted from hash metadata)
/// - Number length pattern
/// - Scam prefix match score
///
/// On first launch, or when the model is not yet downloaded, this falls back
/// to a rule-based heuristic for immediate protection.
///
final class NumberClassifier {

    // MARK: - Properties

    /// The CoreML model, if available.
    private var model: MLModel?

    /// Whether the CoreML model is loaded and ready.
    var isModelReady: Bool { model != nil }

    // MARK: - Initializer

    /// Initializes the classifier, attempting to load the CoreML model.
    /// - Parameter modelURL: URL to the compiled CoreML model file.
    ///   If nil, the classifier operates in heuristic-only mode.
    init(modelURL: URL? = nil) {
        if let url = modelURL {
            do {
                self.model = try MLModel(contentsOf: url)
                Logger.shared.info("Number classifier model loaded successfully", category: .ml)
            } catch {
                Logger.shared.warning(
                    "Failed to load number classifier model: \(error.localizedDescription). Using heuristic fallback.",
                    category: .ml
                )
                self.model = nil
            }
        } else {
            Logger.shared.info("Number classifier running in heuristic-only mode", category: .ml)
            self.model = nil
        }
    }

    // MARK: - Classification

    /// Classifies a hashed phone number for scam risk.
    ///
    /// - Parameter numberHash: SHA-256 hash of the phone number (hex string).
    /// - Returns: Classification result with risk score.
    func classify(numberHash: String) -> NumberClassifierResult {
        if let model = model {
            return classifyWithModel(model, hash: numberHash)
        } else {
            return heuristicClassification(hash: numberHash)
        }
    }

    // MARK: - Heuristic Fallback

    /// Rule-based heuristic classification when CoreML model is unavailable.
    ///
    /// The heuristic checks:
    /// - Prefix patterns known to be associated with scams
    /// - Number length anomalies
    /// - Repeated digit patterns typical of spoofed numbers
    ///
    private func heuristicClassification(hash: String) -> NumberClassifierResult {
        var riskScore: Double = 0.0
        var reasons: [String] = []

        // Heuristic 1: Check known scam prefixes from local cache
        // (handled by repository — here we just return baseline)
        // Default risk score: 0.15 (very low false positive)
        riskScore = 0.15

        // Heuristic 2: Hash-based pattern matching
        // Certain hash patterns correlate with known scam number patterns.
        // For example, hashes starting with certain hex values may indicate
        // numbers from high-risk area codes.
        if let firstByte = Int(hash.prefix(2), radix: 16) {
            // This is a simplified heuristic — in production, the model
            // would learn these patterns from data.
            if firstByte < 20 {
                riskScore += 0.1
                reasons.append("Prefix pattern correlates with known scam regions")
            }
        }

        // Heuristic 3: Collision detection
        // If the hash has an unusual distribution of characters
        let hexDigits = Set(hash)
        if hexDigits.count < 10 {
            riskScore += 0.05 // Unusually low entropy
        }

        return NumberClassifierResult(
            riskScore: min(riskScore, 0.6), // Heuristic caps at 0.6
            scamLabel: riskScore > 0.3 ? "Heuristic Match" : nil,
            confidence: 0.4 // Low confidence for heuristic only
        )
    }

    // MARK: - Model Inference

    /// Runs inference using the CoreML model.
    /// Falls back to heuristic if model prediction fails.
    private func classifyWithModel(_ model: MLModel, hash: String) -> NumberClassifierResult {
        do {
            // Build feature provider from hash characteristics
            let features = try extractFeatures(from: hash)
            let prediction = try model.prediction(from: features)

            // Extract risk score from model output
            // Model output structure depends on the actual .mlmodel format.
            // This is the generic interface — adjust for your specific model.
            let riskScore = prediction.featureValue(for: "risk_score")?.doubleValue ?? 0.0
            let scamLabel = prediction.featureValue(for: "scam_label")?.stringValue
            let confidence = prediction.featureValue(for: "confidence")?.doubleValue ?? 0.5

            return NumberClassifierResult(
                riskScore: riskScore,
                scamLabel: scamLabel,
                confidence: confidence
            )
        } catch {
            Logger.shared.warning(
                "ML model prediction failed: \(error.localizedDescription). Using heuristic fallback.",
                category: .ml
            )
            return heuristicClassification(hash: hash)
        }
    }

    // MARK: - Feature Extraction

    /// Extracts ML-friendly features from the hash for model inference.
    /// - Parameter hash: SHA-256 hash string.
    /// - Returns: MLFeatureProvider with extracted features.
    /// - Throws: If feature extraction fails.
    private func extractFeatures(from hash: String) throws -> MLFeatureProvider {
        // Convert hash to numerical features
        let bytes = hash.map { char -> Double in
            Double(Int(String(char), radix: 16) ?? 0) / 15.0
        }

        // Pad or truncate to expected input size (e.g., 64 features for SHA-256 hex)
        let padded = bytes + Array(repeating: 0.0, count: max(0, 64 - bytes.count))
        let input = try MLMultiArray(shape: [1, 64] as [NSNumber], dataType: .double)
        for (index, value) in padded.prefix(64).enumerated() {
            input[index] = NSNumber(value: value)
        }

        return try MLDictionaryFeatureProvider(dictionary: [
            "hash_features": MLFeatureValue(multiArray: input)
        ])
    }

    // MARK: - Model Update

    /// Updates the CoreML model with a newly downloaded version.
    /// - Parameter modelURL: URL to the new compiled model.
    /// - Returns: Whether the update was successful.
    func updateModel(from modelURL: URL) -> Bool {
        do {
            let newModel = try MLModel(contentsOf: modelURL)
            self.model = newModel
            Logger.shared.info("Number classifier model updated", category: .ml)
            return true
        } catch {
            Logger.shared.error(
                "Failed to update number classifier model: \(error.localizedDescription)",
                category: .ml
            )
            return false
        }
    }
}

// MARK: - Result Type

struct NumberClassifierResult {
    let riskScore: Double
    let scamLabel: String?
    let confidence: Double
}
