import Foundation
import SwiftData

/// Data access layer for SwiftData-backed local storage.
/// Provides a clean interface for CRUD operations on scam data, call logs,
/// and SMS logs without exposing SwiftData internals to the domain layer.
///
/// # Thread Safety
/// This class is designed to be used from the main actor context.
/// Background operations should create their own ScamStore instance.
///
@MainActor
final class ScamStore {

    // MARK: - Properties

    private let modelContext: ModelContext

    // MARK: - Initializer

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    // MARK: - Scam Numbers

    /// Fetches a scam number by its SHA-256 hash.
    /// - Parameter hash: The hex-encoded SHA-256 hash to look up.
    /// - Returns: The matching ScamNumber, or nil if not found.
    func fetchScamNumber(byHash hash: String) -> ScamNumber? {
        let predicate = #Predicate<ScamNumber> { $0.numberHash == hash }
        let descriptor = FetchDescriptor<ScamNumber>(predicate: predicate)
        return try? modelContext.fetch(descriptor).first
    }

    /// Fetches all known scam numbers, optionally filtered by minimum risk.
    /// - Parameter minRisk: Minimum risk score to include.
    /// - Returns: Array of matching ScamNumber objects.
    func fetchScamNumbers(minRisk: Double = 0.0) -> [ScamNumber] {
        let predicate = #Predicate<ScamNumber> { $0.riskScore >= minRisk }
        let descriptor = FetchDescriptor<ScamNumber>(
            predicate: predicate,
            sortBy: [SortDescriptor(\.riskScore, order: .reverse)]
        )
        return (try? modelContext.fetch(descriptor)) ?? []
    }

    /// Fetches all scam numbers that should be auto-blocked.
    /// Used to populate the CallKit CallDirectory extension.
    /// - Returns: Array of ScamNumbers with shouldBlock == true.
    func fetchBlockedNumbers() -> [ScamNumber] {
        let predicate = #Predicate<ScamNumber> { $0.shouldBlock == true }
        let descriptor = FetchDescriptor<ScamNumber>(predicate: predicate)
        return (try? modelContext.fetch(descriptor)) ?? []
    }

    /// Inserts or updates a scam number record.
    /// - Parameter scamNumber: The ScamNumber to save.
    func saveScamNumber(_ scamNumber: ScamNumber) {
        if let existing = fetchScamNumber(byHash: scamNumber.numberHash) {
            existing.riskScore = scamNumber.riskScore
            existing.scamLabel = scamNumber.scamLabel
            existing.confidence = scamNumber.confidence
            existing.updatedAt = Date()
            existing.reportCount = scamNumber.reportCount
            existing.shouldBlock = scamNumber.shouldBlock
        } else {
            modelContext.insert(scamNumber)
        }
        try? modelContext.save()
    }

    /// Batch inserts or updates scam numbers from a server response.
    /// - Parameter numbers: Array of ScamNumber to upsert.
    func saveScamNumbers(_ numbers: [ScamNumber]) {
        for number in numbers {
            saveScamNumber(number)
        }
    }

    /// Removes a scam number from local storage.
    /// - Parameter scamNumber: The ScamNumber to delete.
    func deleteScamNumber(_ scamNumber: ScamNumber) {
        modelContext.delete(scamNumber)
        try? modelContext.save()
    }

    /// Returns the count of known scam numbers in the local database.
    var scamNumberCount: Int {
        let descriptor = FetchDescriptor<ScamNumber>()
        return (try? modelContext.fetchCount(descriptor)) ?? 0
    }

    /// Returns the timestamp of the most recent scam data update.
    var lastUpdateDate: Date? {
        let descriptor = FetchDescriptor<ScamNumber>(
            sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]
        )
        return (try? modelContext.fetch(descriptor).first)?.updatedAt
    }

    // MARK: - Call Logs

    /// Records a new call log entry.
    /// - Parameter log: The CallLog to record.
    func saveCallLog(_ log: CallLog) {
        modelContext.insert(log)
        try? modelContext.save()
    }

    /// Fetches recent call logs, newest first.
    /// - Parameter limit: Maximum number of logs to return.
    /// - Returns: Array of CallLog objects.
    func fetchRecentCallLogs(limit: Int = 100) -> [CallLog] {
        var descriptor = FetchDescriptor<CallLog>(
            sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return (try? modelContext.fetch(descriptor)) ?? []
    }

    /// Fetches call logs flagged as suspicious or scam.
    /// - Returns: Array of risky CallLog objects.
    func fetchRiskyCallLogs() -> [CallLog] {
        let predicate = #Predicate<CallLog> { $0.riskScore >= 0.5 }
        let descriptor = FetchDescriptor<CallLog>(
            predicate: predicate,
            sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
        )
        return (try? modelContext.fetch(descriptor)) ?? []
    }

    /// Fetches a call log by UUID.
    /// - Parameter id: The call log's UUID.
    /// - Returns: Matching CallLog or nil.
    func fetchCallLog(by id: UUID) -> CallLog? {
        let predicate = #Predicate<CallLog> { $0.id == id }
        let descriptor = FetchDescriptor<CallLog>(predicate: predicate)
        return try? modelContext.fetch(descriptor).first
    }

    /// Deletes call logs older than the specified date.
    /// - Parameter date: Cutoff date; logs older than this are removed.
    func deleteCallLogsOlderThan(_ date: Date) {
        let predicate = #Predicate<CallLog> { $0.timestamp < date }
        let descriptor = FetchDescriptor<CallLog>(predicate: predicate)
        if let oldLogs = try? modelContext.fetch(descriptor) {
            for log in oldLogs {
                modelContext.delete(log)
            }
            try? modelContext.save()
        }
    }

    // MARK: - SMS Logs

    /// Saves a new SMS classification log.
    /// - Parameter log: The SmsLog to save.
    func saveSmsLog(_ log: SmsLog) {
        modelContext.insert(log)
        try? modelContext.save()
    }

    /// Fetches recent SMS logs, newest first.
    /// - Parameter limit: Maximum number to return.
    /// - Returns: Array of SmsLog objects.
    func fetchRecentSmsLogs(limit: Int = 100) -> [SmsLog] {
        var descriptor = FetchDescriptor<SmsLog>(
            sortBy: [SortDescriptor(\.receivedAt, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return (try? modelContext.fetch(descriptor)) ?? []
    }

    /// Fetches SMS logs classified as scam.
    /// - Returns: Array of scam-classified SmsLog objects.
    func fetchScamSmsLogs() -> [SmsLog] {
        let predicate = #Predicate<SmsLog> { $0.classification.rawValue == "Scam" }
        let descriptor = FetchDescriptor<SmsLog>(
            predicate: predicate,
            sortBy: [SortDescriptor(\.receivedAt, order: .reverse)]
        )
        return (try? modelContext.fetch(descriptor)) ?? []
    }

    /// Fetches an SMS log by UUID.
    /// - Parameter id: The UUID to look up.
    /// - Returns: Matching SmsLog or nil.
    func fetchSmsLog(by id: UUID) -> SmsLog? {
        let predicate = #Predicate<SmsLog> { $0.id == id }
        let descriptor = FetchDescriptor<SmsLog>(predicate: predicate)
        return try? modelContext.fetch(descriptor).first
    }

    /// Deletes SMS logs older than the specified date.
    /// - Parameter date: Cutoff date for deletion.
    func deleteSmsLogsOlderThan(_ date: Date) {
        let predicate = #Predicate<SmsLog> { $0.receivedAt < date }
        let descriptor = FetchDescriptor<SmsLog>(predicate: predicate)
        if let oldLogs = try? modelContext.fetch(descriptor) {
            for log in oldLogs {
                modelContext.delete(log)
            }
            try? modelContext.save()
        }
    }

    /// Clears all stored data (for reset/opt-out).
    func clearAll() {
        try? modelContext.delete(model: ScamNumber.self)
        try? modelContext.delete(model: CallLog.self)
        try? modelContext.delete(model: SmsLog.self)
    }
}
