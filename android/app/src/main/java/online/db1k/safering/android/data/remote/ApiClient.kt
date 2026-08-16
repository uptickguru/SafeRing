package online.db1k.safering.android.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import online.db1k.safering.android.data.remote.models.AttachmentScanRequest
import online.db1k.safering.android.data.remote.models.AttachmentScanResponse
import online.db1k.safering.android.data.remote.models.CheckResponse
import online.db1k.safering.android.data.remote.models.CircleAcceptRequest
import online.db1k.safering.android.data.remote.models.CircleAcceptResponse
import online.db1k.safering.android.data.remote.models.CircleAlertRequest
import online.db1k.safering.android.data.remote.models.CircleAlertResponse
import online.db1k.safering.android.data.remote.models.CircleInviteRequest
import online.db1k.safering.android.data.remote.models.CircleInviteResponse
import online.db1k.safering.android.data.remote.models.CircleRevokeResponse
import online.db1k.safering.android.data.remote.models.EmailCheckRequest
import online.db1k.safering.android.data.remote.models.EmailCheckResponse
import online.db1k.safering.android.data.remote.models.Entitlement
import online.db1k.safering.android.data.remote.models.EventRequest
import online.db1k.safering.android.data.remote.models.EventResponse
import online.db1k.safering.android.data.remote.models.PrefixResponse
import online.db1k.safering.android.data.remote.models.ReportRequest
import online.db1k.safering.android.data.remote.models.ReportResponse
import online.db1k.safering.android.data.remote.models.TranscriptCheckRequest
import online.db1k.safering.android.data.remote.models.TranscriptCheckResponse
import online.db1k.safering.android.util.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SafeRingApi {
    @GET("v1/check")
    suspend fun checkNumber(@Query("hash") hash: String): CheckResponse

    @GET("v1/prefixes")
    suspend fun fetchPrefixes(): PrefixResponse

    @POST("v1/report")
    suspend fun submitReport(@Body report: ReportRequest): ReportResponse

    @GET("v1/stats")
    suspend fun fetchStats(): Map<String, Any>

    @POST("v1/event")
    suspend fun postEvent(@Body event: EventRequest): EventResponse

    suspend fun inviteCircleContact(@Body invite: CircleInviteRequest): CircleInviteResponse
    suspend fun acceptCircleContact(@Body accept: CircleAcceptRequest): CircleAcceptResponse

    @DELETE("v1/circle/{id}")
    suspend fun revokeCircleContact(@Path("id") id: String): CircleRevokeResponse

    suspend fun sendCircleAlert(@Body alert: CircleAlertRequest): CircleAlertResponse

    @GET("v1/entitlement")
    suspend fun getEntitlement(): Entitlement

    suspend fun checkEmail(@Body request: EmailCheckRequest): EmailCheckResponse
    suspend fun scanAttachment(@Body request: AttachmentScanRequest): AttachmentScanResponse
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
