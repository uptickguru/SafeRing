import Foundation

/// URLSession-based API client for SafeRing backend services.
///
/// # Security
/// - All network calls use HTTPS only.
/// - Phone numbers are **never** sent in plain text — only SHA-256 hashes.
/// - No authentication tokens, no device identifiers, no cookies.
/// - Rate limiting is handled client-side with local caching.
///
/// # Rate Limiting
/// - /check: 100 requests/min (cached aggressively)
/// - /prefixes: 10 requests/min
/// - /report: 20 requests/min
///
final class ApiClient {

    // MARK: - Properties

    private let session: URLSession
    private let baseURL: URL
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    /// Simple in-memory rate limiter: endpoint -> [timestamps]
    private var rateLimitBuckets: [String: [Date]] = [:]
    private let rateLimitQueue = DispatchQueue(label: "online.db1k.safering.ratelimit")

    // MARK: - Initializer

    init(
        baseURLString: String = AppConfig.defaultBaseURL,
        session: URLSession = .shared
    ) {
        guard let url = URL(string: baseURLString) else {
            fatalError("Invalid base URL: \(baseURLString)")
        }
        self.baseURL = url
        self.session = session

        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    // MARK: - API Methods

    /// Looks up a hashed phone number against the scam database.
    /// - Parameter hash: SHA-256 hash (hex string) of the phone number.
    /// - Returns: CheckResponse with risk assessment.
    /// - Throws: ApiError if the request fails or rate limit is exceeded.
    func checkNumber(hash: String) async throws -> CheckResponse {
        try checkRateLimit(for: "/check", maxRequests: 100, windowSeconds: 60)

        var components = URLComponents(url: baseURL.appendingPathComponent("v1/check"), resolvingAgainstBaseURL: true)
        components?.queryItems = [URLQueryItem(name: "hash", value: hash)]

        guard let url = components?.url else {
            throw ApiError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 10

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200:
            return try decoder.decode(CheckResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 404:
            // Hash not found in database — means no known risk
            throw ApiError.notFound
        case 500...599:
            throw ApiError.serverError(statusCode: httpResponse.statusCode)
        default:
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }
    }

    /// Fetches known scam phone number prefixes.
    /// - Returns: PrefixResponse with prefix patterns.
    /// - Throws: ApiError.
    func fetchPrefixes() async throws -> PrefixResponse {
        try checkRateLimit(for: "/prefixes", maxRequests: 10, windowSeconds: 60)

        let url = baseURL.appendingPathComponent("v1/prefixes")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 30

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200:
            return try decoder.decode(PrefixResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        default:
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }
    }

    /// Submits a user report for a scam number.
    /// - Parameter report: The ReportRequest containing the hashed number and scam type.
    /// - Returns: ReportResponse confirming receipt.
    /// - Throws: ApiError.
    func submitReport(_ report: ReportRequest) async throws -> ReportResponse {
        try checkRateLimit(for: "/report", maxRequests: 20, windowSeconds: 60)

        let url = baseURL.appendingPathComponent("v1/report")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 15
        request.httpBody = try encoder.encode(report)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(ReportResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        default:
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }
    }

    /// Fetches anonymous aggregate stats about detected scams.
    /// - Returns: Stats dictionary.
    /// - Throws: ApiError.
    func fetchStats() async throws -> [String: Any] {
        let url = baseURL.appendingPathComponent("v1/stats")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 15

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ApiError.decodingFailed
        }

        return json
    }

    // MARK: - Rate Limiting

    /// Checks whether a rate limit has been exceeded for a given endpoint.
    /// - Parameters:
    ///   - endpoint: API endpoint path (e.g., "/check").
    ///   - maxRequests: Maximum allowed requests in the window.
    ///   - windowSeconds: Time window in seconds.
    /// - Throws: ApiError.rateLimitExceeded if limit is hit.
    private func checkRateLimit(
        for endpoint: String,
        maxRequests: Int,
        windowSeconds: Int
    ) throws {
        let now = Date()
        try rateLimitQueue.sync {
            var timestamps = rateLimitBuckets[endpoint] ?? []
            // Remove expired timestamps
            timestamps.removeAll { now.timeIntervalSince($0) > TimeInterval(windowSeconds) }

            if timestamps.count >= maxRequests {
                throw ApiError.rateLimitExceeded
            }

            timestamps.append(now)
            rateLimitBuckets[endpoint] = timestamps
        }
    }
}

// MARK: - API Errors

enum ApiError: LocalizedError {
    case invalidURL
    case invalidResponse
    case decodingFailed
    case notFound
    case rateLimitExceeded
    case serverError(statusCode: Int)
    case unexpectedStatusCode(Int)
    case networkError(underlying: Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid API URL"
        case .invalidResponse:
            return "Invalid server response"
        case .decodingFailed:
            return "Failed to decode server response"
        case .notFound:
            return "Number not found in database"
        case .rateLimitExceeded:
            return "Rate limit exceeded. Please wait before trying again."
        case .serverError(let code):
            return "Server error (HTTP \(code))"
        case .unexpectedStatusCode(let code):
            return "Unexpected response (HTTP \(code))"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        }
    }
}
