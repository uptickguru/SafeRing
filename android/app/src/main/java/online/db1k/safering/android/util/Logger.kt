package online.db1k.safering.android.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Unified logging system for SafeRing Android.
 *
 * Mirrors the iOS Logger.swift — same categories, same severity levels.
 * All logs go to Logcat (debug) and non-debug levels are forwarded to
 * Firebase Crashlytics for remote collection.
 *
 * # Categories
 * Logs are organized by category for filtering:
 * - `APP`: General app lifecycle events
 * - `UI`: UI navigation and user interactions
 * - `NETWORK`: API calls and network events
 * - `ML`: Machine learning model operations
 * - `BACKGROUND`: Background task execution
 * - `SMS`: SMS classification events
 * - `CALL`: Call screening events
 * - `REPOSITORY`: Data repository operations
 * - `USECASE`: Business logic execution
 * - `DATABASE`: Local storage operations
 */
object Logger {

    private const val TAG = "SafeRing"

    enum class Category(val tag: String) {
        APP("App"),
        UI("UI"),
        NETWORK("Network"),
        ML("ML"),
        BACKGROUND("Background"),
        SMS("SMS"),
        CALL("Call"),
        REPOSITORY("Repository"),
        USECASE("UseCase"),
        DATABASE("Database")
    }

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (_: Exception) {
            null // Not initialized yet
        }

    // -- Info --

    fun info(message: String, category: Category = Category.APP) {
        val full = "[${category.tag}] $message"
        Log.i(TAG, full)
    }

    // -- Warning --

    fun warning(message: String, category: Category = Category.APP) {
        val full = "⚠️ [${category.tag}] $message"
        Log.w(TAG, full)
        crashlytics?.log(full)
    }

    // -- Error --

    fun error(message: String, category: Category = Category.APP, throwable: Throwable? = null) {
        val full = "❌ [${category.tag}] $message"
        Log.e(TAG, full, throwable)
        crashlytics?.apply {
            log(full)
            if (throwable != null) recordException(throwable)
        }
    }

    // -- Debug (release-stripped) --

    fun debug(message: String, category: Category = Category.APP) {
        val full = "🔍 [${category.tag}] $message"
        Log.d(TAG, full)
    }

    // -- Fault / Fatal --

    fun fault(message: String, category: Category = Category.APP, throwable: Throwable? = null) {
        val full = "💥 [${category.tag}] $message"
        Log.wtf(TAG, full, throwable)
        crashlytics?.apply {
            log(full)
            if (throwable != null) recordException(throwable)
            sendUnsentReports()
        }
    }

    // -- Breadcrumb helper (for Crashlytics UserAction tracking) --

    fun breadcrumb(name: String, attributes: Map<String, String> = emptyMap()) {
        crashlytics?.apply {
            attributes.forEach { (key, value) -> setCustomKey(key, value) }
            log("Breadcrumb: $name $attributes")
        }
        debug("Breadcrumb: $name $attributes", Category.UI)
    }
}
