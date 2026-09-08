package online.db1k.safering.android.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEBUG/lab only: fire every edge payload shape Shield is supposed to throw.
 * Trigger: adb shell am start -n online.db1k.safering.android/.MainActivity --es lab_fire_comms 1
 */
object LabCommsFire {
    private const val TAG = "LabCommsFire"
    private val edge = AppConfig.DEFAULT_BASE_URL.trimEnd('/')

    fun runAll(context: Context) {
        Thread {
            val results = mutableListOf<String>()
            fun note(s: String) {
                Log.i(TAG, s)
                results += s
            }
            try {
                postComms("app/analytics", "app", "app_launch", meta = baseMeta(context))
                    .also { note("app_launch=$it") }
                postComms("app/analytics", "app", "app_active", meta = baseMeta(context))
                    .also { note("app_active=$it") }

                listOf(
                    "silenced" to "+15557001001",
                    "trusted" to "+12147087808",
                    "contact" to "+15557001002",
                    "allowed" to "+15557001003"
                ).forEach { (action, num) ->
                    postComms(
                        "call-screening", "voice", action, sender = num,
                        meta = baseMeta(context) + mapOf(
                            "silenced" to (action == "silenced"),
                            "disposition" to action,
                            "lab" to true
                        )
                    ).also { note("call-$action=$it") }
                }

                listOf("ok", "help", "call_person").forEach { a ->
                    postComms(
                        "after-call", "voice", a,
                        meta = baseMeta(context) + mapOf("duration_s" to 42, "lab" to true)
                    ).also { note("after_call_$a=$it") }
                }

                postComms(
                    "sms-intake", "sms", "keyword_hit",
                    sender = "+15557002001",
                    body = "LAB: Your IRS refund needs gift cards. Call now.",
                    meta = baseMeta(context) + mapOf("lab" to true)
                ).also { note("sms-intake=$it") }

                postJson(
                    "/v1/message-filter",
                    JSONObject()
                        .put("sender", "+15557002002")
                        .put("body", "LAB filter path gift card wire transfer")
                        .put("action", "junk")
                        .put("lab", true)
                ).also { note("message-filter=${it.first}") }

                postJson(
                    AppConfig.UNWANTED_REPORT_PATH,
                    JSONObject()
                        .put("source", "android_lab")
                        .put("app", "gmg_shield")
                        .put("action", "junk")
                        .put("kind", "sms")
                        .put("sender", "+15557002003")
                        .put("sender_digits", "15557002003")
                        .put("message_body", "LAB unwanted full message gift card")
                        .put("lab", true)
                ).also { note("unwanted-report=${it.first}") }

                postJson(
                    AppConfig.EXCEPTIONAL_PATH,
                    JSONObject()
                        .put("sender", "+15557002004")
                        .put("channel", "sms")
                        .put("notes", "LAB exceptional OSINT capture")
                        .put("message_body", "LAB sister flagged this number")
                        .put("lab", true)
                ).also { note("exceptional=${it.first}") }

                postJson(
                    "/v1/event",
                    JSONObject()
                        .put("platform", "android")
                        .put("action", "warn")
                        .put("event_type", "call")
                        .put("hash_prefix", "labdead01")
                        .put("risk_score", 0.7)
                        .put("lab", true)
                ).also { note("event=${it.first}") }

                postJson(
                    "/v1/call",
                    JSONObject()
                        .put("action", "block")
                        .put("channel", "voice")
                        .put("sender", "+15557003001")
                        .put("lab", true)
                ).also { note("call=${it.first}") }

                val st = get("/v1/safecall/status")
                note("safecall_status=${st.first}")
                postComms(
                    "app/safecall", "voice", "status",
                    meta = baseMeta(context) + mapOf("http" to st.first, "lab" to true)
                )
                val ap = postJson("/v1/safecall/approve", JSONObject())
                note("safecall_approve=${ap.first}")
                postComms(
                    "app/safecall", "voice", "approve",
                    meta = baseMeta(context) + mapOf("http" to ap.first, "lab" to true)
                )
                val hu = postJson("/v1/safecall/hangup", JSONObject())
                note("safecall_hangup=${hu.first}")
                postComms(
                    "app/safecall", "voice", "drop",
                    meta = baseMeta(context) + mapOf("http" to hu.first, "lab" to true)
                )

                listOf(
                    "protect_start", "protect_stop", "print_card_open",
                    "feature_demo_open", "falls_tips_open", "help_sms", "help_call"
                ).forEach { a ->
                    postComms("app/analytics", "app", a, meta = baseMeta(context) + mapOf("lab" to true))
                        .also { note("$a=$it") }
                }

                val ok = results.count { Regex("""=200\b""").containsMatchIn(it) }
                note("LAB_FIRE_DONE ok200=$ok total=${results.size}")
            } catch (e: Exception) {
                Log.e(TAG, "LAB_FIRE_FAIL", e)
            }
        }.start()
    }

    private fun baseMeta(ctx: Context): Map<String, Any?> {
        val p = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }.getOrNull()
        return mapOf(
            "platform" to "android",
            "model" to android.os.Build.MODEL,
            "os" to "Android ${android.os.Build.VERSION.RELEASE}",
            "app_version" to (p?.versionName ?: ""),
            "lab" to true,
            "lab_run" to "emu-fire-comms"
        )
    }

    private fun postComms(
        entry: String,
        channel: String,
        action: String,
        sender: String? = null,
        body: String? = null,
        meta: Map<String, Any?> = emptyMap()
    ): Int {
        val metaJson = JSONObject()
        meta.forEach { (k, v) -> if (v != null) metaJson.put(k, v) }
        val row = JSONObject()
            .put("entry_point", entry)
            .put("channel", channel)
            .put("action", action)
            .put("sender", sender)
            .put("body", body)
            .put("direction", "inbound")
            .put("client", "gmg-shield-android-lab")
            .put("meta", metaJson)
        val payload = JSONObject().put("events", JSONArray().put(row)).put("platform", "android")
        return postJson(AppConfig.DEVICE_COMMS_PATH, payload).first
    }

    private fun postJson(path: String, body: JSONObject): Pair<Int, String> {
        val url = if (path.startsWith("http")) path else edge + path
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "gmg-shield-android-lab")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val code = conn.responseCode
        val text = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
        } catch (_: Exception) {
            ""
        }
        conn.disconnect()
        return code to text.take(200)
    }

    private fun get(path: String): Pair<Int, String> {
        val conn = (URL(edge + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "gmg-shield-android-lab")
            connectTimeout = 15000
            readTimeout = 20000
        }
        val code = conn.responseCode
        val text = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            ""
        }
        conn.disconnect()
        return code to text.take(200)
    }
}
