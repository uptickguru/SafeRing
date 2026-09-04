package online.db1k.safering.android.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Ship-week analytics: Firebase Analytics (already in app) + our edge device-comms.
 */
object ShieldAnalytics {
    private const val EDGE = "https://safering.gulfmeridiangroup.com/v1/device-comms"
    private val exec = Executors.newSingleThreadExecutor()

    fun event(context: Context, name: String, params: Map<String, String> = emptyMap()) {
        try {
            val fa = FirebaseAnalytics.getInstance(context.applicationContext)
            val b = Bundle()
            params.forEach { (k, v) -> b.putString(k.take(40), v.take(100)) }
            b.putString("platform", "android")
            fa.logEvent(name.take(40), b)
        } catch (_: Exception) { }

        exec.execute {
            try {
                val meta = JSONObject()
                params.forEach { (k, v) -> meta.put(k, v) }
                meta.put("platform", "android")
                val row = JSONObject()
                    .put("entry_point", "app/analytics")
                    .put("channel", "app")
                    .put("action", name)
                    .put("direction", "inbound")
                    .put("client", "gmg-shield-android")
                    .put("meta", meta)
                val body = JSONObject().put("events", JSONArray().put(row)).put("platform", "android")
                val conn = (URL(EDGE).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "gmg-shield-android")
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 12000
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) { }
        }
    }
}
