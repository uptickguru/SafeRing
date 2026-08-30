import SwiftUI
import AVFoundation

struct QRScannerView: View {
    @StateObject private var viewModel = QRScannerViewModel()
    @State private var isScanning = false
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                if let url = viewModel.scannedURL {
                    resultView(url: url)
                } else {
                    scannerView
                }
                
                if !viewModel.scanHistory.isEmpty {
                    historySection
                }
            }
            .navigationTitle("QR Scanner")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
    
    private var scannerView: some View {
        VStack(spacing: 20) {
            Text("Point your camera at a QR code")
                .font(.headline)
                .padding(.top)
            
            CameraPreview { code in
                Task {
                    await viewModel.checkURL(code)
                }
            }
            .frame(height: 300)
            .cornerRadius(12)
            .padding(.horizontal)
            
            if viewModel.isChecking {
                ProgressView("Checking URL safety...")
                    .padding()
            }
            
            if let error = viewModel.error {
                Text(error)
                    .foregroundColor(.red)
                    .padding()
            }
        }
    }
    
    private func resultView(url: String) -> some View {
        VStack(spacing: 20) {
            riskIndicator
            
            VStack(alignment: .leading, spacing: 8) {
                Text("URL:")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(url)
                    .font(.system(.body, design: .monospaced))
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)
            }
            .padding(.horizontal)
            
            Button("Scan Another") {
                viewModel.reset()
            }
            .buttonStyle(.bordered)
        }
    }
    
    private var riskIndicator: some View {
        Group {
            if let score = viewModel.riskScore {
                VStack(spacing: 8) {
                    Image(systemName: iconName(for: score))
                        .font(.system(size: 60))
                        .foregroundColor(color(for: score))
                    
                    Text(label(for: score))
                        .font(.title2)
                        .fontWeight(.semibold)
                    
                    Text("Risk Score: \(Int(score * 100))%")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding()
            }
        }
    }
    
    private var historySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recent Scans")
                .font(.headline)
                .padding(.horizontal)
            
            ForEach(viewModel.scanHistory.prefix(5)) { result in
                HStack {
                    Circle()
                        .fill(color(for: result.riskScore))
                        .frame(width: 8, height: 8)
                    
                    Text(result.url)
                        .font(.caption)
                        .lineLimit(1)
                    
                    Spacer()
                    
                    Text(result.timestamp, style: .time)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal)
                .padding(.vertical, 4)
            }
        }
    }
    
    private func iconName(for score: Double) -> String {
        if score < 0.3 { return "checkmark.circle.fill" }
        if score < 0.7 { return "exclamationmark.triangle.fill" }
        return "xmark.octagon.fill"
    }
    
    private func color(for score: Double) -> Color {
        if score < 0.3 { return .green }
        if score < 0.7 { return .orange }
        return .red
    }
    
    private func label(for score: Double) -> String {
        if score < 0.3 { return "Safe" }
        if score < 0.7 { return "Suspicious" }
        return "Dangerous"
    }
}

struct CameraPreview: UIViewRepresentable {
    let onCodeScanned: (String) -> Void
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        
        let captureSession = AVCaptureSession()
        
        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video),
              let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice) else {
            return view
        }
        
        if captureSession.canAddInput(videoInput) {
            captureSession.addInput(videoInput)
        }
        
        let metadataOutput = AVCaptureMetadataOutput()
        
        if captureSession.canAddOutput(metadataOutput) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(
                context.coordinator,
                queue: DispatchQueue.main
            )
            metadataOutput.metadataObjectTypes = [.qr]
        }
        
        let previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer.frame = view.bounds
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        
        DispatchQueue.global(qos: .userInitiated).async {
            captureSession.startRunning()
        }
        
        context.coordinator.previewLayer = previewLayer
        
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onCodeScanned: onCodeScanned)
    }
    
    class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let onCodeScanned: (String) -> Void
        var previewLayer: AVCaptureVideoPreviewLayer?
        
        init(onCodeScanned: @escaping (String) -> Void) {
            self.onCodeScanned = onCodeScanned
        }
        
        func metadataOutput(_ output: AVCaptureMetadataOutput,
                          didOutput metadataObjects: [AVMetadataObject],
                          from connection: AVCaptureConnection) {
            if let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
               metadataObject.type == .qr,
               let stringValue = metadataObject.stringValue {
                onCodeScanned(stringValue)
            }
        }
    }
}
