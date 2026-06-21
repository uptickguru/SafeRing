import Foundation
import OSLog

/// Unified logging system for SafeRing.
///
/// Uses Apple's `os_log` infrastructure for efficient, structured logging
/// that integrates with the unified logging system (Console.app, Xcode).
///
/// # Categories
/// Logs are organized by category for filtering in Console.app:
/// - `app`: General app lifecycle events
/// - `ui`: UI navigation and user interactions
/// - `network`: API calls and network events
/// - `ml`: Machine learning model operations
/// - `background`: Background task execution
/// - `sms`: SMS classification events
/// - `extension`: CallDirectory and system extension events
/// - `repository`: Data repository operations
/// - `useCase`: Business logic execution
///
final class Logger {

    // MARK: - Singleton

    static let shared = Logger()

    private init() {}

    // MARK: - Log Categories

    /// Log categories for the subsystem.
    enum LogCategory: String {
        case app = "App"
        case ui = "UI"
        case network = "Network"
        case ml = "ML"
        case background = "Background"
        case sms = "SMS"
        case `extension` = "Extension"
        case repository = "Repository"
        case useCase = "UseCase"

        var osLog: OSLog {
            OSLog(subsystem: subsystem, category: rawValue)
        }
    }

    // MARK: - Subsystem

    private static let subsystem = Bundle.main.bundleIdentifier ?? "online.db1k.safering.ios"

    // MARK: - Logging Methods

    /// Log an informational message.
    /// - Parameters:
    ///   - message: The message to log.
    ///   - category: The log category (default: .app).
    func info(_ message: String, category: LogCategory = .app) {
        os_log(.info, log: category.osLog, "%{public}@", message)
    }

    /// Log a warning message.
    /// - Parameters:
    ///   - message: The warning message.
    ///   - category: The log category (default: .app).
    func warning(_ message: String, category: LogCategory = .app) {
        os_log(.default, log: category.osLog, "⚠️ %{public}@", message)
    }

    /// Log an error message.
    /// - Parameters:
    ///   - message: The error message.
    ///   - category: The log category (default: .app).
    func error(_ message: String, category: LogCategory = .app) {
        os_log(.error, log: category.osLog, "❌ %{public}@", message)
    }

    /// Log a debug message (only in debug builds).
    /// - Parameters:
    ///   - message: The debug message.
    ///   - category: The log category (default: .app).
    func debug(_ message: String, category: LogCategory = .app) {
        #if DEBUG
        os_log(.debug, log: category.osLog, "🔍 %{public}@", message)
        #endif
    }

    /// Log a fault/crash-level message.
    /// - Parameters:
    ///   - message: The fault message.
    ///   - category: The log category (default: .app).
    func fault(_ message: String, category: LogCategory = .app) {
        os_log(.fault, log: category.osLog, "💥 %{public}@", message)
    }
}
