import Foundation
import CallKit
import Combine

struct CallCheckIn: Identifiable, Equatable {
    let id = UUID()
    let endedAt: Date
    let lastedAtLeast: TimeInterval
}

/// Watches system calls without reading numbers.
///
/// CXCallObserver never gives us the remote number. That is what we want:
/// after a connected call ends, we ask the human if it was okay.
@MainActor
final class CallEndObserver: NSObject, ObservableObject {

    static let shared = CallEndObserver()

    @Published var pendingCheckIn: CallCheckIn?

    private let observer = CXCallObserver()
    private var connectedAt: [UUID: Date] = [:]

    override init() {
        super.init()
        observer.setDelegate(self, queue: .main)
    }

    func dismiss() {
        pendingCheckIn = nil
    }

    func considerForegroundResume() {
        // If a call ended while we were backgrounded, the last pending
        // check-in is already published. Nothing else to do.
    }
}

extension CallEndObserver: CXCallObserverDelegate {
    nonisolated func callObserver(_ callObserver: CXCallObserver, callChanged call: CXCall) {
        Task { @MainActor in
            handle(call)
        }
    }

    private func handle(_ call: CXCall) {
        if call.hasConnected, connectedAt[call.uuid] == nil {
            connectedAt[call.uuid] = Date()
        }

        guard call.hasEnded else { return }
        let started = connectedAt.removeValue(forKey: call.uuid)
        let duration = started.map { Date().timeIntervalSince($0) } ?? 0
        // Ignore instant tap-away / missed.
        guard duration >= 8 else { return }
        pendingCheckIn = CallCheckIn(endedAt: Date(), lastedAtLeast: duration)
        Logger.shared.info(
            "Call ended after \(Int(duration))s — offering check-in",
            category: .ui
        )
    }
}
