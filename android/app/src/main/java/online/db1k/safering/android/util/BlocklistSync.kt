package online.db1k.safering.android.util

import android.content.Context
import android.util.Log
import online.db1k.safering.android.service.FilterRulesStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Pull fleet blocklist + keywords from Caruso; expand local block/keyword filters.
 */
object BlocklistSync {
    private const val TAG = "BlocklistSync"
    private val exec = Executors.newSingleThreadExecutor()
    private val edge = AppConfig.DEFAULT_BASE_URL.trimEnd('/')

    fun runOnLaunch(context: Context) {
        val app = context.applicationContext
        exec.execute {
            try {
                pullAndApply(app)
            } catch (e: Exception) {
                Log.w(TAG, "sync fail: ${e.message}")
            }
        }
    }

    private fun pullAndApply(context: Context) {
        val conn = (URL("$edge/v1/blocklist").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "gmg-shield-android")
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
        if (code !in 200..299) {
            ShieldAnalytics.event(context, "blocklist_sync_fail", mapOf("http" to code.toString()))
            return
        }
        val obj = JSONObject(text)
        val numbers = obj.optJSONArray("numbers") ?: JSONArray()
        val keywords = obj.optJSONArray("keywords") ?: JSONArray()
        var addedNum = 0
        var addedKw = 0
        val beforeBlock = FilterRulesStore.blockDigits(context).size
        val beforeKw = FilterRulesStore.keywords(context).size
        for (i in 0 until numbers.length()) {
            val n = numbers.optString(i)
            if (n.isNotBlank()) {
                FilterRulesStore.addBlockDigits(context, n)
                addedNum++
            }
        }
        for (i in 0 until keywords.length()) {
            val k = keywords.optString(i)
            if (k.isNotBlank()) {
                FilterRulesStore.addKeyword(context, k)
                addedKw++
            }
        }
        val afterBlock = FilterRulesStore.blockDigits(context).size
        val afterKw = FilterRulesStore.keywords(context).size
        DeviceComms.log(
            context,
            entryPoint = "blocklist/sync",
            channel = "app",
            action = "pull",
            meta = mapOf(
                "http" to code,
                "numbers" to numbers.length(),
                "keywords" to keywords.length(),
                "block_before" to beforeBlock,
                "block_after" to afterBlock,
                "kw_before" to beforeKw,
                "kw_after" to afterKw,
                "added_numbers" to addedNum,
                "added_keywords" to addedKw
            )
        )
        ShieldAnalytics.event(
            context,
            "blocklist_sync_ok",
            mapOf(
                "numbers" to numbers.length().toString(),
                "keywords" to keywords.length().toString(),
                "block_after" to afterBlock.toString()
            )
        )
        Log.i(TAG, "blocklist applied nums=${numbers.length()} kw=${keywords.length()} block=$afterBlock")
    }
}
