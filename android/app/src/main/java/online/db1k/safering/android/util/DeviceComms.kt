package online.db1k.safering.android.util

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Unified edge log — same job as iOS device_comms + ShieldAnalytics.
 */
object DeviceComms {
    private val exec = Executors.newSingleThreadExecutor()

    fun log(
        context: Context,
        entryPoint: String,
        channel: String,
        action: String? = null,
        sender: String? = null,
        body: String? = null,
        meta: Map<String, Any?> = emptyMap()
    ) {
        val app = context.applicationContext
        exec.execute {
            try {
                val metaJson = JSONObject()
                meta.forEach { (k, v) -> if (v != null) metaJson.put(k, v) }
                metaJson.put("platform", "android")
                metaJson.put("model", Build.MODEL)
                metaJson.put("os", "Android ${Build.VERSION.RELEASE}")
                runCatching {
                    val p = app.packageManager.getPackageInfo(app.packageName, 0)
                    metaJson.put("app_version", p.versionName ?: "")
                    metaJson.put("build", if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong())
                }
                val row = JSONObject()
                    .put("entry_point", entryPoint)
                    .put("channel", channel)
                    .put("action", action)
                    .put("sender", sender)
                    .put("body", body?.take(4000))
                    .put("direction", "inbound")
                    .put("client", "gmg-shield-android")
                    .put("meta", metaJson)
                val payload = JSONObject()
                    .put("events", JSONArray().put(row))
                    .put("platform", "android")
                post(AppConfig.DEFAULT_BASE_URL + AppConfig.DEVICE_COMMS_PATH, payload)
            } catch (e: Exception) {
                Logger.debug("DeviceComms: ${e.message}", Logger.Category.APP)
            }
        }
    }

    fun postJson(path: String, body: JSONObject): Int {
        return try {
            post(AppConfig.DEFAULT_BASE_URL.trimEnd('/') + path, body)
        } catch (_: Exception) {
            -1
        }
    }

    private fun post(urlStr: String, body: JSONObject): Int {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "gmg-shield-android")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 15000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val code = conn.responseCode
        conn.disconnect()
        return code
    }
}
