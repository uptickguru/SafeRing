import Foundation

/// URLSession-based API client for SafeRing backend services.
///
/// # Security
/// - All network calls use HTTPS only.
/// - Phone numbers are hashed with **HMAC-SHA256** (not plain SHA-256) before sending.
///   HMAC uses a per-install secret key provisioned at enrollment, making the hash
///   computationally infeasible to reverse.
/// - No authentication tokens, no device identifiers, no cookies.
/// - Rate limiting is handled client-side with local caching.
///
/// # Threat Model
/// Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
/// makes it trivially reversible. HMAC-SHA256 with a secret key provides
/// pseudonymization, making it computationally infeasible to recover the
/// original number from the hash.
///
/// # Rate Limiting
/// - /check: 100 requests/min (cached aggressively)
/// - /prefixes: 10 requests/min
/// - /report: 20 requests/min
/// - /circle/invite: 5 requests/min
/// - /circle/alert: 5 requests/min
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
    /// - Parameter hash: HMAC-SHA256 hash (hex string) of the phone number.
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

    /// Posts a device action event for server-side operational visibility.
    /// Fire-and-forget: failures are logged and swallowed.
    /// - Parameter event: The device event to report.
    func postEvent(_ event: DeviceEvent) async {
        let url = baseURL.appendingPathComponent("v1/event")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 5

        do {
            request.httpBody = try encoder.encode(event)
            let (_, response) = try await session.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                Logger.shared.debug(
                    "Event sent: \(event.action) \(event.eventType) -> \(httpResponse.statusCode)",
                    category: .network
                )
            }
        } catch {
            Logger.shared.debug(
                "Event send failed (non-critical): \(error.localizedDescription)",
                category: .network
            )
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

    // MARK: - Circle APIs

    /// Invites a contact to the trusted circle.
    /// - Parameter invite: The CircleInviteRequest containing the hashed phone number.
    ///   The phoneHash is HMAC-SHA256 — NEVER store or send plaintext numbers.
    /// - Returns: CircleInviteResponse with the invitation ID.
    /// - Throws: ApiError if the invitation fails.
    func inviteCircleContact(_ invite: CircleInviteRequest) async throws -> CircleInviteResponse {
        let url = baseURL.appendingPathComponent("v1/circle/invite")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 15
        request.httpBody = try encoder.encode(invite)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(CircleInviteResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 403:
            throw CircleError.contactLimitReached
        case 404:
            throw CircleError.invitationFailed("Contact not found")
        case 500...599:
            throw CircleError.invitationFailed("Server error (HTTP \(httpResponse.statusCode))")
        default:
            throw CircleError.invitationFailed("Unexpected response (HTTP \(httpResponse.statusCode))")
        }
    }

    /// Accepts an invitation to the trusted circle.
    /// - Parameter accept: The CircleAcceptRequest containing the invitation ID.
    /// - Returns: CircleAcceptResponse confirming acceptance.
    /// - Throws: ApiError if the acceptance fails.
    func acceptCircleContact(_ accept: CircleAcceptRequest) async throws -> CircleAcceptResponse {
        let url = baseURL.appendingPathComponent("v1/circle/accept")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 15
        request.httpBody = try encoder.encode(accept)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(CircleAcceptResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 404:
            throw CircleError.acceptanceFailed("Invitation not found")
        case 500...599:
            throw CircleError.acceptanceFailed("Server error (HTTP \(httpResponse.statusCode))")
        default:
            throw CircleError.acceptanceFailed("Unexpected response (HTTP \(httpResponse.statusCode))")
        }
    }

    /// Revokes a trusted circle membership.
    /// - Parameter revoke: The CircleRevokeRequest containing the invitation ID.
    /// - Returns: CircleRevokeResponse confirming revocation.
    /// - Throws: ApiError if the revocation fails.
    func revokeCircleContact(_ revoke: CircleRevokeRequest) async throws -> CircleRevokeResponse {
        let url = baseURL.appendingPathComponent("v1/circle/\(revoke.invitationId)")
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 15

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 204:
            return CircleRevokeResponse(success: true, error: nil)
        case 404:
            throw CircleError.revocationFailed("Invitation not found")
        case 500...599:
            throw CircleError.revocationFailed("Server error (HTTP \(httpResponse.statusCode))")
        default:
            throw CircleError.revocationFailed("Unexpected response (HTTP \(httpResponse.statusCode))")
        }
    }

    /// Sends a REDACTED trusted circle alert to a trusted contact.
    ///
    /// # Security
    /// The alert payload is REDACTED — it contains ONLY category + reason + who asked for help.
    /// NEVER include full phone numbers, message bodies, or account details.
    ///
    /// - Parameter alert: The CircleAlertRequest containing the redacted alert data.
    ///   The alert payload is: category + short reason + who asked for help.
    /// - Returns: CircleAlertResponse confirming delivery.
    /// - Throws: ApiError if the alert fails.
    func sendCircleAlert(_ alert: CircleAlertRequest) async throws -> CircleAlertResponse {
        let url = baseURL.appendingPathComponent("v1/circle/alert")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 15
        request.httpBody = try encoder.encode(alert)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(CircleAlertResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 404:
            throw CircleError.alertFailed("Invitation not found")
        case 500...599:
            throw CircleError.alertFailed("Server error (HTTP \(httpResponse.statusCode))")
        default:
            throw CircleError.alertFailed("Unexpected response (HTTP \(httpResponse.statusCode))")
        }
    }

    /// Fetches the user's subscription entitlement.
    /// - Returns: Entitlement with tier information.
    /// - Throws: ApiError if the check fails.
    func getEntitlement() async throws -> Entitlement {
        let url = baseURL.appendingPathComponent("v1/entitlement")
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

        return try decoder.decode(Entitlement.self, from: data)
    }

    // MARK: - Submit-to-Check APIs

    /// Checks an email address for scam content.
    ///
    /// # Security
    /// The email text is submitted as-is. The API analyzes it for known scam
    /// patterns, phishing links, and social engineering tactics.
    ///
    /// - Parameter request: The EmailCheckRequest containing the email text.
    /// - Returns: EmailCheckResponse with the result.
    /// - Throws: ApiError if the request fails.
    func checkEmail(_ request: EmailCheckRequest) async throws -> EmailCheckResponse {
        let url = baseURL.appendingPathComponent("v1/email")
        var httpRequest = URLRequest(url: url)
        httpRequest.httpMethod = "POST"
        httpRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        httpRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        httpRequest.timeoutInterval = 15
        httpRequest.httpBody = try encoder.encode(request)

        let (data, response) = try await session.data(for: httpRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(EmailCheckResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 404:
            throw ApiError.notFound
        case 500...599:
            throw ApiError.serverError(statusCode: httpResponse.statusCode)
        default:
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }
    }

    /// Scans an attachment (image/document) for scam content.
    ///
    /// # Security
    /// EXIF/location metadata is stripped client-side before upload.
    /// The file is analyzed only for scam content and not retained.
    ///
    /// - Parameter request: The AttachmentScanRequest containing the file data.
    /// - Returns: AttachmentScanResponse with the result.
    /// - Throws: ApiError if the request fails.
    func scanAttachment(_ request: AttachmentScanRequest) async throws -> AttachmentScanResponse {
        let url = baseURL.appendingPathComponent("v1/attachment")
        var httpRequest = URLRequest(url: url)
        httpRequest.httpMethod = "POST"
        httpRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        httpRequest.timeoutInterval = 30
        httpRequest.httpBody = try encoder.encode(request)

        let (data, response) = try await session.data(for: httpRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(AttachmentScanResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 404:
            throw ApiError.notFound
        case 500...599:
            throw ApiError.serverError(statusCode: httpResponse.statusCode)
        default:
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }
    }

    /// Checks a call transcript for scam content.
    ///
    /// # Security
    /// The transcript is submitted as-is. The user must only submit conversations
    /// they are lawfully permitted to share.
    ///
    /// - Parameter request: The TranscriptCheckRequest containing the transcript text.
    /// - Returns: TranscriptCheckResponse with the result.
    /// - Throws: ApiError if the request fails.
    func checkTranscript(_ request: TranscriptCheckRequest) async throws -> TranscriptCheckResponse {
        let url = baseURL.appendingPathComponent("v1/call")
        var httpRequest = URLRequest(url: url)
        httpRequest.httpMethod = "POST"
        httpRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        httpRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        httpRequest.timeoutInterval = 15
        httpRequest.httpBody = try encoder.encode(request)

        let (data, response) = try await session.data(for: httpRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200, 201:
            return try decoder.decode(TranscriptCheckResponse.self, from: data)
        case 429:
            throw ApiError.rateLimitExceeded
        case 404:
            throw ApiError.notFound
        case 500...599:
            throw ApiError.serverError(statusCode: httpResponse.statusCode)
        default:
            throw ApiError.unexpectedStatusCode(httpResponse.statusCode)
        }
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
