package online.db1k.safering.android.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import online.db1k.safering.android.data.remote.models.CheckResponse
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
 * # Zero PII
 * Phone numbers are never sent in plain text — only SHA-256 hashes.
 * Mirrors the iOS ApiClient.swift functionality.
 */
interface SafeRingApi {

    @GET("v1/check")
    suspend fun checkNumber(@Query("hash") hash: String): CheckResponse

    @GET("v1/prefixes")
    suspend fun fetchPrefixes(): PrefixResponse

    @POST("v1/report")
    suspend fun submitReport(@Body report: ReportRequest): ReportResponse

    @GET("v1/stats")
    suspend fun fetchStats(): Map<String, Any>

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
