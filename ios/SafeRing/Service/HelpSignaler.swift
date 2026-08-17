import Foundation
import UIKit
import MessageUI

/// Off-call signaling to the trusted contact.
///
/// There is no Signal send API and no RCS API a third-party iOS app can call.
/// Order of usefulness on iPhone:
/// 1. SMS / iMessage via MessageUI (prefilled, works with any number)
/// 2. WhatsApp URL (can prefill text if installed)
/// 3. FaceTime Audio (encrypted live verify, not a text alert)
/// 4. Signal URL (opens a chat, cannot prefill)
enum HelpReason: String {
    case money
    case help
    case afterCall
    case checkInTimeout
    case pasteScam
    case verify

    var shortLabel: String {
        switch self {
        case .money: return "someone asking for money"
        case .help: return "tapped Help"
        case .afterCall: return "a call just ended and they want a check-in"
        case .checkInTimeout: return "a call ended and they did not confirm it was okay"
        case .pasteScam: return "they pasted a message that looks like a scam"
        case .verify: return "they want you to verify a call or message"
        }
    }
}

struct HelpDraft: Identifiable, Equatable {
    let id = UUID()
    let recipients: [String]
    let body: String
    let reason: HelpReason
}

enum HelpSignalError: LocalizedError {
    case notConfigured
    case messagingUnavailable
    case cannotOpen(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Add a trusted person first."
        case .messagingUnavailable:
            return "This device cannot send texts."
        case .cannotOpen(let app):
            return "Could not open \(app). Is it installed?"
        }
    }
}

@MainActor
final class HelpSignaler: ObservableObject {

    static let shared = HelpSignaler()

    @Published var draft: HelpDraft?
    @Published var lastError: String?

    private let household: HouseholdStore

    init(household: HouseholdStore = .shared) {
        self.household = household
    }

    func makeDraft(reason: HelpReason) throws -> HelpDraft {
        guard household.isConfigured else { throw HelpSignalError.notConfigured }
        let name = household.ownerDisplayName
        let trusted = household.trustedContactName.isEmpty ? "there" : household.trustedContactName
        let body = """
        SafeRing alert for \(trusted):

        \(name) — \(reason.shortLabel).

        Call them on their saved number. Do not call back an unknown number. Do not send money or gift cards.

        This is a redacted family alert. No caller ID or message content is included.
        """
        return HelpDraft(
            recipients: [household.e164Number],
            body: body,
            reason: reason
        )
    }

    func prepareSMS(reason: HelpReason) throws {
        draft = try makeDraft(reason: reason)
        guard MFMessageComposeViewController.canSendText() else {
            throw HelpSignalError.messagingUnavailable
        }
    }

    func openPreferred(reason: HelpReason) throws {
        switch household.preferredChannel {
        case .sms:
            try prepareSMS(reason: reason)
        case .whatsapp:
            try openWhatsApp(reason: reason)
        case .signal:
            try openSignal()
        case .facetime:
            // FaceTime is Apple-only. Help alerts use SMS so Android numbers work.
            try prepareSMS(reason: reason)
        }
    }

    func openFaceTimeAudio() throws {
        guard household.isConfigured else { throw HelpSignalError.notConfigured }
        let number = household.e164Number
        let candidates = [
            URL(string: "facetime-audio://\(number)"),
            URL(string: "facetime://\(number)"),
        ].compactMap { $0 }
        for url in candidates where UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
            household.recordHelpSent()
            return
        }
        try openPhone()
    }

    func openPhone() throws {
        guard household.isConfigured else { throw HelpSignalError.notConfigured }
        // Real cellular/PSTN call — reaches Android. Never FaceTime on this path.
        let raw = household.e164Number
        let allowed = Set("+0123456789")
        let digits = String(raw.filter { allowed.contains($0) })
        guard digits.contains(where: { $0.isNumber }) else {
            throw HelpSignalError.notConfigured
        }
        // tel:+E164 (not tel://) — reliable on modern iOS
        guard let url = URL(string: "tel:\(digits)") else {
            throw HelpSignalError.cannotOpen("Phone")
        }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    func openSignal() throws {
        guard household.isConfigured else { throw HelpSignalError.notConfigured }
        let number = household.e164Number
        let candidates = [
            URL(string: "sgnl://signal.me/#p/\(number)"),
            URL(string: "https://signal.me/#p/\(number)"),
        ].compactMap { $0 }
        for url in candidates {
            if url.scheme == "https" || UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                household.recordHelpSent()
                return
            }
        }
        throw HelpSignalError.cannotOpen("Signal")
    }

    func openWhatsApp(reason: HelpReason) throws {
        let draft = try makeDraft(reason: reason)
        let digits = household.e164Number.filter(\.isNumber)
        let encoded = draft.body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let candidates = [
            URL(string: "whatsapp://send?phone=\(digits)&text=\(encoded)"),
            URL(string: "https://wa.me/\(digits)?text=\(encoded)"),
        ].compactMap { $0 }
        for url in candidates {
            if url.scheme == "https" || UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                household.recordHelpSent()
                return
            }
        }
        throw HelpSignalError.cannotOpen("WhatsApp")
    }

    func isAvailable(_ channel: HouseholdStore.SignalChannel) -> Bool {
        switch channel {
        case .sms:
            return MFMessageComposeViewController.canSendText()
        case .whatsapp:
            return UIApplication.shared.canOpenURL(URL(string: "whatsapp://send")!)
        case .signal:
            return UIApplication.shared.canOpenURL(URL(string: "sgnl://signal.me")!)
        case .facetime:
            return UIApplication.shared.canOpenURL(URL(string: "facetime-audio://")!)
                || UIApplication.shared.canOpenURL(URL(string: "facetime://")!)
        }
    }

    func markSent() {
        household.recordHelpSent()
        draft = nil
    }

    func cancelDraft() {
        draft = nil
    }
}
