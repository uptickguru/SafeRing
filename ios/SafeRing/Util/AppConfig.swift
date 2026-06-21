import Foundation
import UIKit

/// Application configuration constants.
///
/// Centralizes all configurable values for the SafeRing app to avoid
/// magic numbers scattered throughout the codebase.
///
enum AppConfig {

    // MARK: - API Configuration

    /// Default base URL for the SafeRing backend API.
    /// Can be overridden for development/staging environments.
    static let defaultBaseURL = "https://safering.deathbyathousand.com"

    /// API version prefix.
    static let apiVersion = "v1"

    /// Request timeout in seconds.
    static let requestTimeout: TimeInterval = 15

    /// Maximum number of retries for failed network requests.
    static let maxRetries = 2

    // MARK: - Sync Configuration

    /// Interval between background scam data syncs (in seconds).
    /// Default: 6 hours.
    static let syncInterval: TimeInterval = 6 * 60 * 60

    /// Background task identifier for scam data sync.
    static let syncTaskIdentifier = "online.db1k.safering.ios.sync-scam-data"

    /// Background task identifier for report upload.
    static let reportTaskIdentifier = "online.db1k.safering.ios.upload-reports"

    // MARK: - Risk Thresholds

    /// Risk score above which a number should be auto-blocked.
    /// 0.85 = 85% confidence of scam.
    static let autoBlockThreshold: Double = 0.85

    /// Risk score above which a call gets a warning label.
    static let warningThreshold: Double = 0.3

    /// Risk score above which a full-screen alert is shown.
    static let alertThreshold: Double = 0.6

    // MARK: - Cache Configuration

    /// Maximum age of cached scam data before refresh (in seconds).
    static let cacheMaxAge: TimeInterval = 6 * 60 * 60

    /// Maximum number of call logs to store locally.
    static let maxCallLogs = 500

    /// Maximum number of SMS logs to store locally.
    static let maxSmsLogs = 500

    /// Maximum age of logs before auto-cleanup (in seconds). Default: 30 days.
    static let logRetentionPeriod: TimeInterval = 30 * 24 * 60 * 60

    // MARK: - App Group

    /// App group identifier for shared data between the main app and extensions.
    /// Must match the App Groups capability in the Xcode project.
    static let appGroupIdentifier = "group.online.db1k.safering.ios"

    // MARK: - Device Info

    /// Device model string (anonymous, used for aggregate stats only).
    static var deviceModel: String? {
        #if targetEnvironment(simulator)
        return "iOS Simulator"
        #else
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.compactMap { child -> String? in
            guard let value = child.value as? Int8, value != 0 else { return nil }
            return String(UnicodeScalar(UInt8(value)))
        }.joined()
        return identifier.isEmpty ? nil : identifier
        #endif
    }

    /// iOS version string (anonymous, used for aggregate stats only).
    static var osVersion: String? {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }

    /// App version string.
    static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }

    /// Build number.
    static var buildNumber: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    // MARK: - Feature Flags

    /// Whether SMS message body storage is enabled.
    /// Controlled by user setting in the Settings screen.
    static var isSmsBodyStorageEnabled: Bool {
        UserDefaults.standard.bool(forKey: "showSmsBody")
    }

    /// Whether scam call auto-blocking is enabled.
    static var isAutoBlockEnabled: Bool {
        UserDefaults.standard.bool(forKey: "autoBlockScam")
    }
}
