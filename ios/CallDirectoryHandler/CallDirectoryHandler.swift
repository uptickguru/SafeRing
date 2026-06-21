import Foundation
import CallKit

/// CallKit Call Directory extension for SafeRing.
///
/// Reads scam numbers from a shared file-based store in the app group container.
/// Zero PII: all numbers are SHA-256 hashed.
final class CallDirectoryHandler: CXCallDirectoryProvider {

    private static var store: ExtensionScamStore?
    private var store: ExtensionScamStore {
        if let s = Self.store { return s }
        let s = ExtensionScamStore()
        Self.store = s
        return s
    }

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        NSLog("[SafeRing] CallDirectory: update requested")
        context.delegate = self

        do {
            let allNumbers = try store.loadScamNumbers()

            // Block high-risk (≥0.7), identify the rest
            let blocked = allNumbers.filter { $0.riskScore >= 0.7 }
            let identified = allNumbers.filter { $0.riskScore >= 0.3 && $0.riskScore < 0.7 }

            for scam in blocked {
                if let id = hashToIdentifier(scam.numberHash) {
                    context.addBlockingEntry(withNextSequentialPhoneNumber: id)
                }
            }

            for scam in identified {
                if let id = hashToIdentifier(scam.numberHash) {
                    let pct = Int(scam.riskScore * 100)
                    let label = "SafeRing: \(scam.scamType) (\(pct)%)"
                    context.addIdentificationEntry(withNextSequentialPhoneNumber: id, label: label)
                }
            }

            NSLog("[SafeRing] CallDirectory: \(blocked.count) blocked, \(identified.count) identified")
            context.completeRequest()

        } catch {
            NSLog("[SafeRing] CallDirectory error: \(error.localizedDescription)")
            context.cancelRequest(withError: error)
        }
    }

    private func hashToIdentifier(_ hash: String) -> CXCallDirectoryPhoneNumber? {
        let prefix = String(hash.prefix(15))
        guard let value = UInt64(prefix, radix: 16) else { return nil }
        return Int64(value & 0x7FFFFFFFFFFFFFFF)
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for context: CXCallDirectoryExtensionContext, withError error: Error) {
        NSLog("[SafeRing] CallDirectory context failed: \(error.localizedDescription)")
    }
}

// MARK: - Extension-local types

struct ExtensionScamNumber: Codable {
    let numberHash: String
    let riskScore: Double
    let scamType: String
}

final class ExtensionScamStore {
    private let fileURL: URL

    init() {
        let groupID = "group.online.db1k.safering.ios"
        let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)
        self.fileURL = container!.appendingPathComponent("scam_cache.json")
    }

    func loadScamNumbers() throws -> [ExtensionScamNumber] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return [] }
        let data = try Data(contentsOf: fileURL)
        return try JSONDecoder().decode([ExtensionScamNumber].self, from: data)
    }
}
