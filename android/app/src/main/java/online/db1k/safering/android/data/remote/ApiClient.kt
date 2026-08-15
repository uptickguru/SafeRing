package online.db1k.safering.android.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import online.db1k.safering.android.data.remote.models.CheckResponse
import online.db1k.safering.android.data.remote.models.CircleAlertRequest
import online.db1k.safering.android.data.remote.models.CircleAlertResponse
import online.db1k.safering.android.data.remote.models.CircleAcceptRequest
import online.db1k.safering.android.data.remote.models.CircleAcceptResponse
import online.db1k.safering.android.data.remote.models.CircleContact
import online.db1k.safering.android.data.remote.models.CircleInviteRequest
import online.db1k.safering.android.data.remote.models.CircleInviteResponse
import online.db1k.safering.android.data.remote.models.CircleRevokeRequest
import online.db1k.safering.android.data.remote.models.CircleRevokeResponse
import online.db1k.safering.android.data.remote.models.Entitlement
import online.db1k.safering.android.data.remote.models.EmailCheckRequest
import online.db1k.safering.android.data.remote.models.EmailCheckResponse
import online.db1k.safering.android.data.remote.models.AttachmentScanRequest
import online.db1k.safering.android.data.remote.models.AttachmentScanResponse
import online.db1k.safering.android.data.remote.models.TranscriptCheckRequest
import online.db1k.safering.android.data.remote.models.TranscriptCheckResponse
import online.db1k.safering.android.data.remote.models.EventRequest
import online.db1k.safering.android.data.remote.models.EventResponse
import online.db1k.safering.android.data.remote.models.PrefixResponse
import online.db1k.safering.android.data.remote.models.ReportRequest
import online.db1k.safering.android.data.remote.models.ReportResponse
import online.db1k.safering.android.util.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Retrofit-based API client for SafeRing backend services.
 *
 * # Security
 * Phone numbers are hashed with **HMAC-SHA256** (not plain SHA-256) before sending.
 * HMAC uses a per-install secret key provisioned at enrollment, making the hash
 * computationally infeasible to reverse.
 * Mirrors the iOS ApiClient.swift functionality.
 *
 * # Threat Model
 * Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
 * HMAC-SHA256 with a per-install secret key provides pseudonymization.
 * makes it trivially reversible. HMAC-SHA256 with a secret key provides
 * pseudonymization, making it computationally infeasible to recover the
 * original number from the hash.
 */
interface SafeRingApi {

    @GET("v1/check")
    suspend fun checkNumber(@Query("hash") hash: String): CheckResponse
        // hash is HMAC-SHA256, not plain SHA-256

    @GET("v1/prefixes")
    suspend fun fetchPrefixes(): PrefixResponse

    @POST("v1/report")
    suspend fun submitReport(@Body report: ReportRequest): ReportResponse

    @GET("v1/stats")
    suspend fun fetchStats(): Map<String, Any>

    @POST("v1/event")
    suspend fun postEvent(@Body event: EventRequest): EventResponse

    // MARK: - Circle APIs

    /// Invites a contact to the trusted circle.
    ///
    /// # Security
    /// The phoneHash is HMAC-SHA256 — NEVER store or send plaintext numbers.
    ///
    /// @param invite The CircleInviteRequest containing the hashed phone number.
    /// @return CircleInviteResponse with the invitation ID.
    suspend fun inviteCircleContact(@Body invite: CircleInviteRequest): CircleInviteResponse

    /// Accepts an invitation to the trusted circle.
    ///
    /// @param accept The CircleAcceptRequest containing the invitation ID.
    /// @return CircleAcceptResponse confirming acceptance.
    suspend fun acceptCircleContact(@Body accept: CircleAcceptRequest): CircleAcceptResponse

    /// Revokes a trusted circle membership.
    ///
    /// Either party can revoke (DELETE /v1/circle/{id}) anytime.
    ///
    /// @param id The invitation ID to revoke.
    /// @return CircleRevokeResponse confirming revocation.
    @DELETE("v1/circle/{id}")
    suspend fun revokeCircleContact(@Path("id") id: String): CircleRevokeResponse

    /// Sends a REDACTED trusted circle alert to a trusted contact.
    ///
    /// # Security
    /// The alert payload is REDACTED — it contains ONLY category + reason + who asked for help.
    /// NEVER include full phone numbers, message bodies, or account details.
    ///
    /// @param alert The CircleAlertRequest containing the redacted alert data.
    /// @return CircleAlertResponse confirming delivery.
    suspend fun sendCircleAlert(@Body alert: CircleAlertRequest): CircleAlertResponse

    /// Fetches the user's subscription entitlement.
    ///
    /// @return Entitlement with tier information.
    @GET("v1/entitlement")
    suspend fun getEntitlement(): Entitlement

    // MARK: - Submit-to-Check APIs

    /// Checks an email address for scam content.
    ///
    /// # Security
    /// The email text is submitted as-is. The API analyzes it for known scam
    /// patterns, phishing links, and social engineering tactics.
    ///
    /// @param request The EmailCheckRequest containing the email text.
    /// @return EmailCheckResponse with the result.
    suspend fun checkEmail(@Body request: EmailCheckRequest): EmailCheckResponse

    /// Scans an attachment (image/document) for scam content.
    ///
    /// # Security
    /// EXIF/location metadata is stripped client-side before upload.
    /// The file is analyzed only for scam content and not retained.
    ///
    /// @param request The AttachmentScanRequest containing the file data.
    /// @return AttachmentScanResponse with the result.
    suspend fun scanAttachment(@Body request: AttachmentScanRequest): AttachmentScanResponse

    /// Checks a call transcript for scam content.
    ///
    /// # Security
    /// The transcript is submitted as-is. The user must only submit conversations
    /// they are lawfully permitted to share.
    ///
    /// @param request The TranscriptCheckRequest containing the transcript text.
    /// @return TranscriptCheckResponse with the result.
    suspend fun checkTranscript(@Body request: TranscriptCheckRequest): TranscriptCheckResponse

    companion object {
        fun create(baseUrl: String = AppConfig.DEFAULT_BASE_URL): SafeRingApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(AppConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AppConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(AppConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SafeRingApi::class.java)
        }
    }
}
