import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = ContentViewModel()
    @State private var checkNumber = ""
    @State private var showCheckField = false
    
    var body: some View {
        TabView {
            // Alerts tab
            VStack(spacing: 8) {
                Image(systemName: "shield.checkered")
                    .font(.title2)
                    .foregroundColor(.green)
                Text("Protected")
                    .font(.caption)
                Text("\(viewModel.recentScams.count) recent alerts")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }
            .padding()
            .tabItem {
                Label("Status", systemImage: "shield")
            }
            
            // Check number tab
            VStack(spacing: 12) {
                if showCheckField {
                    TextField("Phone number", text: $checkNumber)
                        .font(.caption)
                    Button("Check") {
                        Task { await viewModel.checkNumber(checkNumber) }
                    }
                    if let result = viewModel.checkResult {
                        RiskBadgeWatch(riskScore: result.riskScore, scamType: result.scamType)
                    }
                } else {
                    Button("Check Number") {
                        showCheckField = true
                    }
                }
            }
            .padding()
            .tabItem {
                Label("Check", systemImage: "magnifyingglass")
            }
            
            // Recent tab
            List(viewModel.recentScams) { scam in
                HStack {
                    RiskDotWatch(riskScore: scam.riskScore)
                    VStack(alignment: .leading) {
                        Text(scam.scamType).font(.caption2)
                        Text(scam.timestamp, style: .relative).font(.caption2).foregroundColor(.gray)
                    }
                }
            }
            .tabItem {
                Label("Recent", systemImage: "list.bullet")
            }
        }
    }
}

struct RiskBadgeWatch: View {
    let riskScore: Double
    let scamType: String
    
    var color: Color {
        riskScore >= 0.7 ? .red : riskScore >= 0.4 ? .orange : .green
    }
    
    var body: some View {
        VStack {
            Text(scamType).font(.caption2)
            Text("\(Int(riskScore * 100))%").font(.title3).foregroundColor(color)
        }
    }
}

struct RiskDotWatch: View {
    let riskScore: Double
    
    var color: Color {
        riskScore >= 0.7 ? .red : riskScore >= 0.4 ? .orange : .green
    }
    
    var body: some View {
        Circle().fill(color).frame(width: 8, height: 8)
    }
}
