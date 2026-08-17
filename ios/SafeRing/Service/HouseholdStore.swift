import Foundation
import Security
import Combine

/// Local household configuration for the tripwire MVP.
///
/// Trusted-contact number and family password stay on this device.
/// The password is stored in the Keychain and is never uploaded.
@MainActor
final class HouseholdStore: ObservableObject {

    static let shared = HouseholdStore()

    enum SignalChannel: String, CaseIterable, Identifiable {
        case sms
        case whatsapp
        case signal
        case facetime

        var id: String { rawValue }

        var title: String {
            switch self {
            case .sms: return "Text / iMessage"
            case .whatsapp: return "WhatsApp"
            case .signal: return "Signal"
            case .facetime: return "FaceTime (Apple only)"
            }
        }

        var subtitle: String {
            switch self {
            case .sms: return "Works with any phone. Opens Messages already filled in."
            case .whatsapp: return "Opens a chat with the alert already typed."
            case .signal: return "Opens a private Signal chat. You type the alert."
            case .facetime: return "Apple only. For Android use Text. Call always dials the phone network."
            }
        }

        var icon: String {
            switch self {
            case .sms: return "message.fill"
            case .whatsapp: return "phone.bubble.fill"
            case .signal: return "lock.shield.fill"
            case .facetime: return "video.fill"
            }
        }
    }

    private enum Keys {
        static let ownerName = "household.ownerName"
        static let trustedName = "household.trustedName"
        static let trustedNumber = "household.trustedNumber"
        static let channel = "household.signalChannel"
        static let silenceUnknown = "household.silenceUnknown"
        static let filterUnknownSms = "household.filterUnknownSms"
        static let carrierProtection = "household.carrierProtection"
        static let helpCount = "household.helpCount"
        static let lastHelpAt = "household.lastHelpAt"
        static let passwordAccount = "safering.family-password"
    }

    @Published var ownerDisplayName: String {
        didSet { defaults.set(ownerDisplayName, forKey: Keys.ownerName) }
    }

    @Published var trustedContactName: String {
        didSet { defaults.set(trustedContactName, forKey: Keys.trustedName) }
    }

    @Published var trustedContactNumber: String {
        didSet { defaults.set(trustedContactNumber, forKey: Keys.trustedNumber) }
    }

    @Published var preferredChannel: SignalChannel {
        didSet { defaults.set(preferredChannel.rawValue, forKey: Keys.channel) }
    }

    @Published var silenceUnknownConfirmed: Bool {
        didSet { defaults.set(silenceUnknownConfirmed, forKey: Keys.silenceUnknown) }
    }

    @Published var filterUnknownSmsConfirmed: Bool {
        didSet { defaults.set(filterUnknownSmsConfirmed, forKey: Keys.filterUnknownSms) }
    }

    @Published var carrierProtectionConfirmed: Bool {
        didSet { defaults.set(carrierProtectionConfirmed, forKey: Keys.carrierProtection) }
    }

    @Published private(set) var helpCount: Int {
        didSet { defaults.set(helpCount, forKey: Keys.helpCount) }
    }

    @Published private(set) var lastHelpAt: Date? {
        didSet {
            if let lastHelpAt {
                defaults.set(lastHelpAt.timeIntervalSince1970, forKey: Keys.lastHelpAt)
            } else {
                defaults.removeObject(forKey: Keys.lastHelpAt)
            }
        }
    }

    @Published private(set) var hasFamilyPassword: Bool

    var isConfigured: Bool {
        !trustedContactNumber.filter(\.isNumber).isEmpty
            && trustedContactNumber.filter(\.isNumber).count >= 10
            && hasFamilyPassword
            && !ownerDisplayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var osChecklistComplete: Bool {
        silenceUnknownConfirmed && filterUnknownSmsConfirmed && carrierProtectionConfirmed
    }

    var savedContact: SavedContact {
        SavedContact(
            id: UUID(),
            displayName: trustedContactName.isEmpty ? "your person" : trustedContactName,
            savedNumber: e164Number
        )
    }

    var e164Number: String {
        let n = Self.normalizeToE164(trustedContactNumber)
        if n.filter({ $0.isNumber }).count >= 10 {
            FilterRulesStore.shared.syncTrustedContactE164(n)
        }
        return n
    }

    var displayNumber: String {
        let digits = trustedContactNumber.filter(\.isNumber)
        if digits.count == 11, digits.hasPrefix("1") {
            let rest = digits.dropFirst()
            return "+1 (\(rest.prefix(3))) \(rest.dropFirst(3).prefix(3))-\(rest.suffix(4))"
        }
        if digits.count == 10 {
            return "(\(digits.prefix(3))) \(digits.dropFirst(3).prefix(3))-\(digits.suffix(4))"
        }
        return trustedContactNumber
    }

    private let defaults: UserDefaults

    private init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.ownerDisplayName = defaults.string(forKey: Keys.ownerName) ?? ""
        self.trustedContactName = defaults.string(forKey: Keys.trustedName) ?? ""
        self.trustedContactNumber = defaults.string(forKey: Keys.trustedNumber) ?? ""
        self.preferredChannel = SignalChannel(rawValue: defaults.string(forKey: Keys.channel) ?? "") ?? .sms
        self.silenceUnknownConfirmed = defaults.bool(forKey: Keys.silenceUnknown)
        self.filterUnknownSmsConfirmed = defaults.bool(forKey: Keys.filterUnknownSms)
        self.carrierProtectionConfirmed = defaults.bool(forKey: Keys.carrierProtection)
        self.helpCount = defaults.integer(forKey: Keys.helpCount)
        if defaults.object(forKey: Keys.lastHelpAt) != nil {
            self.lastHelpAt = Date(timeIntervalSince1970: defaults.double(forKey: Keys.lastHelpAt))
        } else {
            self.lastHelpAt = nil
        }
        self.hasFamilyPassword = KeychainStore.get(account: Keys.passwordAccount) != nil
    }

    func familyPassword() -> String? {
        KeychainStore.get(account: Keys.passwordAccount)
    }

    func setFamilyPassword(_ password: String) {
        let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        KeychainStore.set(trimmed, account: Keys.passwordAccount)
        hasFamilyPassword = true
    }

    func clearFamilyPassword() {
        KeychainStore.delete(account: Keys.passwordAccount)
        hasFamilyPassword = false
    }

    func recordHelpSent() {
        helpCount += 1
        lastHelpAt = Date()
    }

    func resetHousehold() {
        ownerDisplayName = ""
        trustedContactName = ""
        trustedContactNumber = ""
        preferredChannel = .sms
        silenceUnknownConfirmed = false
        filterUnknownSmsConfirmed = false
        carrierProtectionConfirmed = false
        helpCount = 0
        lastHelpAt = nil
        clearFamilyPassword()
    }

    /// Wipe household + onboarding flag so next frame shows Onboarding.
    /// Does not use removePersistentDomain (that races AppStorage).
    func resetForOnboarding() {
        resetHousehold()
        let d = UserDefaults.standard
        for key in [
            "hasCompletedOnboarding",
            "protectionEnabled",
            "smsScanningEnabled",
            "autoBlockScam",
            "showSmsBody",
            "showAdvancedSettings",
        ] {
            d.removeObject(forKey: key)
        }
        d.set(false, forKey: "hasCompletedOnboarding")
        d.synchronize()
        NotificationCenter.default.post(name: .saferingDidReset, object: nil)
    }

    /// Seed household for UI tests / simulator capture.
    /// Launch with: -uitest-seed 1
    func applyLaunchTestSeedIfNeeded() {
        let args = ProcessInfo.processInfo.arguments
        guard args.contains("-uitest-seed") else { return }
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
        if ownerDisplayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            ownerDisplayName = "Helen"
        }
        if trustedContactName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            trustedContactName = "Kevin"
        }
        if trustedContactNumber.filter(\.isNumber).count < 10 {
            trustedContactNumber = "15551234567"
        }
        if !hasFamilyPassword {
            setFamilyPassword("rosebud")
        }
        silenceUnknownConfirmed = true
        filterUnknownSmsConfirmed = true
        carrierProtectionConfirmed = true
    }

    static func normalizeToE164(_ raw: String) -> String {

        let digits = raw.filter(\.isNumber)
        if digits.hasPrefix("1"), digits.count == 11 {
            return "+\(digits)"
        }
        if digits.count == 10 {
            return "+1\(digits)"
        }
        if raw.hasPrefix("+") {
            return "+\(digits)"
        }
        return digits.isEmpty ? raw : "+\(digits)"
    }
}

enum KeychainStore {
    static func set(_ value: String, account: String) {
        delete(account: account)
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "online.db1k.safering.ios",
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    static func get(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "online.db1k.safering.ios",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "online.db1k.safering.ios",
        ]
        SecItemDelete(query as CFDictionary)
    }
}


extension Notification.Name {
    static let saferingDidReset = Notification.Name("saferingDidReset")
}
