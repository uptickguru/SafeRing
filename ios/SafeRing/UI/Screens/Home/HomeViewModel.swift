import Foundation
import SwiftUI
import Combine

@MainActor
final class HomeViewModel: ObservableObject {

    @Published var lastError: String?
    @Published var showError = false

    let household: HouseholdStore
    let signaler: HelpSignaler

    init(
        household: HouseholdStore = .shared,
        signaler: HelpSignaler = .shared
    ) {
        self.household = household
        self.signaler = signaler
    }

    var protectionTitle: String {
        if !household.isConfigured { return "Needs setup" }
        if household.osChecklistComplete { return "Tripwire ready" }
        return "Tripwire on — finish phone settings"
    }

    var protectionDetail: String {
        if !household.isConfigured {
            return "Add your person and a family password in Settings."
        }
        let name = household.trustedContactName.isEmpty ? "your person" : household.trustedContactName
        return "Help texts \(name) at \(household.displayNumber). Unknown callers should be silenced by the iPhone, not by a number list."
    }

    func requestHelp(_ reason: HelpReason) {
        do {
            try signaler.openPreferred(reason: reason)
            lastError = nil
            showError = false
        } catch {
            lastError = error.localizedDescription
            showError = true
        }
    }

    /// Always a real phone call (tel:) so Android and landlines work.
    /// FaceTime is never the Call button default.
    func callPerson() {
        do {
            try signaler.openPhone()
            lastError = nil
            showError = false
        } catch {
            lastError = error.localizedDescription
            showError = true
        }
    }
}
