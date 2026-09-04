package online.db1k.safering.android.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.DeviceComms
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Caretaker SafeCall hooks — lab Android is caretaker.
 * HITL approve / drop against live edge (best-effort; UI shows status).
 */
object SafeCallCaretaker {
    data class Status(
        val ok: Boolean,
        val raw: String,
        val pending: Boolean = false,
        val summary: String = ""
    )

    suspend fun fetchStatus(context: Context): Status = withContext(Dispatchers.IO) {
        try {
            val url = URL(AppConfig.DEFAULT_BASE_URL.trimEnd('/') + AppConfig.SAFECALL_STATUS_PATH)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "gmg-shield-android")
                connectTimeout = 12000
                readTimeout = 12000
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            val json = runCatching { JSONObject(body) }.getOrNull()
            val pending = json?.optBoolean("pending_approve", false) == true ||
                json?.optJSONArray("pending")?.length()?.let { it > 0 } == true
            val summary = json?.optString("status")?.ifBlank { null }
                ?: json?.optString("state")?.ifBlank { null }
                ?: if (code in 200..299) "Edge reachable" else "HTTP $code"
            DeviceComms.log(context, "app/safecall", "voice", "status", meta = mapOf("http" to code, "pending" to pending))
            Status(ok = code in 200..299, raw = body.take(2000), pending = pending, summary = summary)
        } catch (e: Exception) {
            Status(ok = false, raw = e.message ?: "error", summary = "Offline")
        }
    }

    suspend fun approve(context: Context, householdId: String? = null, decision: String = "approve"): Result<String> =
        postAction(context, "/v1/safecall/approve", decision, householdId)

    suspend fun drop(context: Context, householdId: String? = null): Result<String> =
        postAction(context, "/v1/safecall/hangup", "drop", householdId)

    private suspend fun postAction(
        context: Context,
        path: String,
        action: String,
        householdId: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("action", action)
                .put("platform", "android")
                .put("client", "gmg-shield-android")
            if (!householdId.isNullOrBlank()) body.put("household_id", householdId)
            val url = URL(AppConfig.DEFAULT_BASE_URL.trimEnd('/') + path)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "gmg-shield-android")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            DeviceComms.log(context, "app/safecall", "voice", action, meta = mapOf("http" to code))
            if (code in 200..299) Result.success(resp.ifBlank { "ok" })
            else Result.failure(IllegalStateException("HTTP $code ${resp.take(200)}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
