import Foundation
import WatchConnectivity

@MainActor
class ContentViewModel: ObservableObject {
    @Published var recentScams: [ScamAlert] = []
    @Published var isConnected: Bool = false
    @Published var checkResult: CheckResult?
    
    private let session = WCSession.default
    
    struct ScamAlert: Identifiable {
        let id = UUID()
        let numberHash: String
        let riskScore: Double
        let scamType: String
        let timestamp: Date
    }
    
    struct CheckResult {
        let hash: String
        let riskScore: Double
        let scamType: String
    }
    
    init() {
        if WCSession.isSupported() {
            session.delegate = self
            session.activate()
        }
    }
    
    func checkNumber(_ number: String) async {
        let cleaned = number.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
        guard cleaned.count >= 10 else { return }
        let e164 = "1" + cleaned.suffix(10)
        // Simple string hash for watch (don't have CommonCrypto)
        let hash = simpleHash(e164)
        
        // Check via API
        await checkRemote(hash: hash)
    }
    
    private func checkRemote(hash: String) async {
        guard let url = URL(string: "https://safering.deathbyathousand.com/v1/check?hash=\(hash)") else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let risk = json["risk_score"] as? Double ?? 0
                let type = json["scam_type"] as? String ?? "unknown"
                checkResult = CheckResult(hash: hash, riskScore: risk, scamType: type)
            }
        } catch {
            checkResult = CheckResult(hash: hash, riskScore: -1, scamType: "error")
        }
    }
    
    private func simpleHash(_ input: String) -> String {
        var hash = UInt64(5381)
        for byte in input.utf8 {
            hash = ((hash << 5) &+ hash) &+ UInt64(byte)
        }
        return String(format: "%016llx", hash)
    }
}

extension ContentViewModel: WCSessionDelegate {
    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in
            isConnected = true
        }
    }
}
