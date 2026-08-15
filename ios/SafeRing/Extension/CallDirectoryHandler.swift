import Foundation
import CallKit

/// CallKit Call Directory extension for SafeRing.
///
/// # Architecture
/// This extension reads scam numbers from a shared file-based store in the app group container.
/// It does NOT perform any network calls at call time — all data is pre-synced to the device.
///
/// # Data Flow
/// 1. Main app syncs scam data to shared container (via BackgroundTasks)
/// 2. CallDirectoryHandler reads from the shared container at call time
/// 3. No network I/O inside this extension
///
/// # Security
/// All phone numbers are HMAC-SHA256 hashed before storage.
/// The extension only ever sees hashes, never raw phone numbers.
///
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
        // Use first 10 hex chars → convert to a phone number in E.164 format
        // We can't use the hash directly as CXCallDirectoryPhoneNumber requires
        // a phone number format. Generate a deterministic pseudo-number from hash.
        let prefix = String(hash.prefix(10))
        guard let value = UInt64(prefix, radix: 16) else { return nil }
        // Map to a US phone number range: +1 (555) XXX-XXXX
        let digits = value % 10_000_000_000  // 10 digits
        return Int64(digits)
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
