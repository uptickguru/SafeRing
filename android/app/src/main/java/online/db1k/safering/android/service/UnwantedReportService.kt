package online.db1k.safering.android.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.DeviceComms
import online.db1k.safering.android.util.Logger
import online.db1k.safering.android.util.PhoneNumberUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Full-message report — parity with iOS Unwanted Communication → /v1/unwanted-report. */
object UnwantedReportService {
    suspend fun submit(
        context: Context,
        senderRaw: String,
        messageBody: String,
        channel: String = "sms",
        note: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val e164 = PhoneNumberUtils.normalizeToE164(senderRaw)
            val digits = e164.filter { it.isDigit() }
            if (messageBody.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Paste the full message."))
            }
            if (digits.length >= 10) {
                FilterRulesStore.addBlockDigits(context, digits)
            }
            val messages = JSONArray().put(
                JSONObject()
                    .put("sender", if (e164.isNotBlank()) e164 else senderRaw)
                    .put("body", messageBody)
                    .put("channel", channel)
            )
            val body = JSONObject()
                .put("platform", "android")
                .put("channel", channel)
                .put("sender", if (e164.isNotBlank()) e164 else senderRaw)
                .put("sender_digits", digits)
                .put("message_body", messageBody)
                .put("messages", messages)
                .put("note", note)
                .put("reported_at", Instant.now().toString())
                .put("client", "gmg-shield-android")
            val url = URL(AppConfig.DEFAULT_BASE_URL.trimEnd('/') + AppConfig.UNWANTED_REPORT_PATH)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "gmg-shield-android")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            DeviceComms.log(
                context,
                entryPoint = "app/unwanted-report",
                channel = channel,
                action = "report",
                sender = digits.ifBlank { senderRaw },
                body = messageBody.take(2000),
                meta = mapOf("http" to code)
            )
            if (code !in 200..299) {
                Logger.debug("unwanted report HTTP $code $resp", Logger.Category.SMS)
                return@withContext Result.failure(IllegalStateException("Server error $code"))
            }
            val id = runCatching { JSONObject(resp).optString("id") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "ok"
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
