import Foundation

/// Offline-first repository for scam data.
///
/// Implements the Repository pattern to abstract data sources:
/// - **Local:** SwiftData-backed `ScamStore` for offline cache
/// - **Remote:** `ApiClient` for server queries
///
/// # Zero PII Policy
/// All phone numbers are hashed with SHA-256 before any network call.
/// The local store also stores only hashes, never plaintext numbers.
///
/// # Strategy
/// 1. Always check local cache first (instant response)
/// 2. If cache miss or stale, query remote API
/// 3. On remote response, update local cache
/// 4. If remote fails, return cached data with staleness info
///
@MainActor
final class ScamRepository {

    // MARK: - Properties

    private let apiClient: ApiClient
    private let scamStore: ScamStore

    /// Maximum age of cached data before refresh is attempted (default: 6 hours).
    private let cacheMaxAge: TimeInterval = 6 * 3600

    // MARK: - Initializer

    init(apiClient: ApiClient, scamStore: ScamStore) {
        self.apiClient = apiClient
        self.scamStore = scamStore
    }

    // MARK: - Public API

    /// Checks a phone number hash against scam databases.
    /// Returns cached result if available and fresh, otherwise fetches from API.
    ///
    /// - Parameter hash: SHA-256 hash of the phone number.
    /// - Returns: CheckResult with risk assessment.
    /// - Throws: RepositoryError if both sources fail.
    func checkNumber(hash: String) async throws -> CheckResult {
        // 1. Try local cache first
        if let cached = scamStore.fetchScamNumber(byHash: hash) {
            let isStale = Date().timeIntervalSince(cached.updatedAt) > cacheMaxAge

            if !isStale {
                Logger.shared.info("Cache hit for hash: \(hash.prefix(8))...", category: .repository)
                return CheckResult(
                    riskScore: cached.riskScore,
                    scamLabel: cached.scamLabel,
                    confidence: cached.confidence,
                    shouldBlock: cached.shouldBlock,
                    source: .localCache
                )
            }
        }

        // 2. Cache miss or stale — query remote API
        do {
            let response = try await apiClient.checkNumber(hash: hash)

            // Update local cache
            let scamNumber = ScamNumber(
                numberHash: hash,
                riskScore: response.risk,
                scamLabel: response.label ?? "Unknown",
                confidence: response.confidence,
                firstReportedAt: response.firstReportedAt.map { Date(timeIntervalSince1970: $0) },
                reportCount: response.reportCount,
                shouldBlock: response.risk >= AppConfig.autoBlockThreshold
            )
            scamStore.saveScamNumber(scamNumber)

            return CheckResult(
                riskScore: response.risk,
                scamLabel: response.label,
                confidence: response.confidence,
                shouldBlock: response.risk >= AppConfig.autoBlockThreshold,
                source: .remote
            )
        } catch {
            // 3. Remote failed — return cached data even if stale
            if let cached = scamStore.fetchScamNumber(byHash: hash) {
                Logger.shared.warning(
                    "Remote check failed, using stale cache. Error: \(error.localizedDescription)",
                    category: .repository
                )
                return CheckResult(
                    riskScore: cached.riskScore,
                    scamLabel: cached.scamLabel,
                    confidence: cached.confidence,
                    shouldBlock: cached.shouldBlock,
                    source: .staleCache
                )
            }

            // 4. No data available from any source
            throw RepositoryError.noData(error: error)
        }
    }

    /// Fetches all known scam prefixes from the server and caches locally.
    /// - Returns: Array of prefix patterns.
    /// - Throws: RepositoryError.
    func fetchPrefixes() async throws -> [ScamPrefix] {
        do {
            let response = try await apiClient.fetchPrefixes()
            return response.prefixes
        } catch {
            throw RepositoryError.noData(error: error)
        }
    }

    /// Reports a scam number to the server.
    ///
    /// - Parameters:
    ///   - hash: SHA-256 hash of the scam number.
    ///   - tag: Scam type tag.
    /// - Returns: ReportResponse from the server.
    /// - Throws: RepositoryError.
    func reportScam(hash: String, tag: String) async throws -> ReportResponse {
        let request = ReportRequest(
            hash: hash,
            tag: tag,
            timestamp: Date().timeIntervalSince1970,
            deviceModel: AppConfig.deviceModel,
            osVersion: AppConfig.osVersion
        )

        do {
            let response = try await apiClient.submitReport(request)

            // Update local cache if report was successful
            if response.success {
                if let existing = scamStore.fetchScamNumber(byHash: hash) {
                    existing.reportCount = response.totalReports ?? existing.reportCount + 1
                    existing.updatedAt = Date()
                    try? scamStore.saveScamNumber(existing)
                } else {
                    let newEntry = ScamNumber(
                        numberHash: hash,
                        riskScore: 0.7, // User report elevates risk
                        scamLabel: tag,
                        confidence: 0.5,
                        reportCount: 1
                    )
                    scamStore.saveScamNumber(newEntry)
                }
            }

            return response
        } catch {
            throw RepositoryError.uploadFailed(error: error)
        }
    }

    /// Returns the most recent scam data from local cache.
    /// - Returns: Array of cached ScamNumbers.
    func getAllCachedScamNumbers(minRisk: Double = 0.3) -> [ScamNumber] {
        scamStore.fetchScamNumbers(minRisk: minRisk)
    }

    /// Returns blocked numbers for CallDirectory extension population.
    /// - Returns: Array of blocked ScamNumbers.
    func getBlockedNumbers() -> [ScamNumber] {
        scamStore.fetchBlockedNumbers()
    }

    /// Returns the timestamp of the last successful data sync.
    var lastSyncDate: Date? {
        scamStore.lastUpdateDate
    }

    /// Returns the number of cached scam numbers.
    var cachedScamNumberCount: Int {
        scamStore.scamNumberCount
    }

    /// Clears all cached data.
    func clearCache() {
        scamStore.clearAll()
    }
}

// MARK: - Supporting Types

/// Result of a scam number check.
struct CheckResult {
    let riskScore: Double
    let scamLabel: String?
    let confidence: Double
    let shouldBlock: Bool
    let source: DataSource

    enum DataSource: String {
        case localCache = "Local Cache"
        case remote = "Remote API"
        case staleCache = "Stale Cache"
    }

    /// Categorized risk level.
    var riskLevel: ScamNumber.RiskLevel {
        switch riskScore {
        case ..<0.3: return .safe
        case ..<0.5: return .suspicious
        case ..<0.75: return .highRisk
        default: return .scam
        }
    }
}

// MARK: - Repository Errors

enum RepositoryError: LocalizedError {
    case noData(error: Error)
    case uploadFailed(error: Error)

    var errorDescription: String? {
        switch self {
        case .noData(let error):
            return "Unable to check number: \(error.localizedDescription)"
        case .uploadFailed(let error):
            return "Failed to submit report: \(error.localizedDescription)"
        }
    }
}
