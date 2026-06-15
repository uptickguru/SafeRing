package com.safering.android.data.remote

import com.safering.android.data.remote.dto.CheckResponse
import com.safering.android.data.remote.dto.ModelInfoResponse
import com.safering.android.data.remote.dto.PrefixResponse
import com.safering.android.data.remote.dto.ReportRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * SafeRing API service interface.
 *
 * Zero PII guarantee: All phone number queries send only the SHA-256 hash.
 * The raw number is never transmitted over the network.
 */
interface ApiService {

    /**
     * Look up a phone number hash in the scam database.
     * @param hash SHA-256 hash of the phone number (hex string).
     * @return Risk assessment for the queried number.
     */
    @GET("check")
    suspend fun checkNumber(
        @Query("hash") hash: String
    ): Response<CheckResponse>

    /**
     * Get all known scam caller ID prefixes.
     * Used to initialize and refresh the local prefix cache.
     */
    @GET("prefixes")
    suspend fun getPrefixes(): Response<List<PrefixResponse>>

    /**
     * Get the latest model version info.
     * Returns version, download URL, and SHA-256 checksum of the model file.
     */
    @GET("model/latest")
    suspend fun getLatestModelInfo(
        @Query("type") type: String = "number"
    ): Response<ModelInfoResponse>

    /**
     * Submit a scam report.
     * Body contains only the SHA-256 hash + scam type tag — no PII.
     */
    @POST("report")
    suspend fun reportNumber(
        @Body request: ReportRequest
    ): Response<Unit>

    /**
     * Download a model file by type and version.
     * Returns the raw TFLite model binary.
     */
    @GET("model")
    suspend fun downloadModel(
        @Query("type") type: String,
        @Query("version") version: Int
    ): Response<ResponseBody>

    /**
     * Get anonymous aggregate statistics.
     */
    @GET("stats")
    suspend fun getStats(): Response<ResponseBody>
}
