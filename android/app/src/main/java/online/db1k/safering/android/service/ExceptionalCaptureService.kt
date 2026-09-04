package online.db1k.safering.android.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.db1k.safering.android.util.HashUtils
import online.db1k.safering.android.util.DeviceComms
import online.db1k.safering.android.util.Logger
import online.db1k.safering.android.util.PhoneNumberUtils
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Exceptional family OSINT assist — same contract as iOS.
 * Does **not** need Notification Listener.
 */
object ExceptionalCaptureService {
    const val CONSENT_VERSION = "exceptional-v1"
    private const val DEFAULT_BASE = "https://safering.gulfmeridiangroup.com"

    suspend fun submit(
        context: Context,
        senderRaw: String,
        messageBody: String,
        note: String,
        householdLabel: String,
        baseUrl: String = DEFAULT_BASE
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!HouseholdStore.get(context).exceptionalCaptureEnabled) {
                return@withContext Result.failure(IllegalStateException("Turn on Exceptional investigation in Settings first."))
            }
            val e164 = PhoneNumberUtils.normalizeToE164(senderRaw)
            if (!PhoneNumberUtils.isPlausibleE164(e164)) {
                return@withContext Result.failure(IllegalArgumentException("Need a valid phone number."))
            }
            if (messageBody.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Paste the message text."))
            }

            val digits = e164.filter { it.isDigit() }
            val senderHash = HashUtils.sha256(digits)

            // Seed local block / filter immediately
            runCatching {
                val store = context.getSharedPreferences("safering_filter", Context.MODE_PRIVATE)
                val set = store.getStringSet("block_senders", emptySet())?.toMutableSet() ?: mutableSetOf()
                set.add(digits)
                store.edit().putStringSet("block_senders", set).apply()
            }
            SmsIntake.recordFromNotification(
                context = context,
                packageName = "exceptional",
                senderRaw = e164,
                title = e164,
                snippet = messageBody.take(280),
                allText = messageBody
            )

            val body = JSONObject().apply {
                put("consent_version", CONSENT_VERSION)
                put("household_label", householdLabel)
                put("channel", "sms")
                put("sender_hash", senderHash)
                put("alg", "https-only-v0")
                put("ciphertext_b64", "")
                put("created_at", Instant.now().toString())
                put(
                    "debug_plain",
                    JSONObject().apply {
                        put("sender_e164", e164)
                        put("message_body", messageBody)
                        put("note", note)
                    }
                )
            }

            val url = URL(baseUrl.trimEnd('/') + "/v1/exceptional/capture")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-SafeRing-Exceptional", "1")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                Logger.debug("exceptional capture HTTP $code $resp", Logger.Category.SMS)
                return@withContext Result.failure(IllegalStateException("Server error $code"))
            }
            val caseId = runCatching { JSONObject(resp).optString("case_id") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: senderHash
            DeviceComms.log(
                context,
                entryPoint = "app/exceptional",
                channel = "sms",
                action = "capture",
                sender = e164,
                body = messageBody.take(1500),
                meta = mapOf("case_id" to caseId)
            )
            Result.success(caseId)
        } catch (e: Exception) {
            Logger.debug("exceptional capture failed: ${e.message}", Logger.Category.SMS)
            Result.failure(e)
        }
    }
}
