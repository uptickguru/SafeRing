import Foundation
import SwiftUI

@MainActor
final class QRScannerViewModel: ObservableObject {
    @Published var scannedURL: String?
    @Published var riskScore: Double?
    @Published var isChecking = false
    @Published var error: String?
    @Published var scanHistory: [QRScanResult] = []
    
    struct QRScanResult: Identifiable {
        let id = UUID()
        let url: String
        let riskScore: Double
        let timestamp: Date
    }
    
    func checkURL(_ urlString: String) async {
        guard let url = URL(string: urlString) else {
            error = "Invalid URL format"
            return
        }
        
        isChecking = true
        error = nil
        
        do {
            let response = try await ApiClient.shared.checkURL(url: urlString)
            scannedURL = urlString
            riskScore = response.risk_score
            
            scanHistory.insert(
                QRScanResult(url: urlString, riskScore: response.risk_score, timestamp: Date()),
                at: 0
            )
            if scanHistory.count > 10 {
                scanHistory = Array(scanHistory.prefix(10))
            }
        } catch {
            self.error = "Failed to check URL: \(error.localizedDescription)"
        }
        
        isChecking = false
    }
    
    func reset() {
        scannedURL = nil
        riskScore = nil
        error = nil
    }
}
