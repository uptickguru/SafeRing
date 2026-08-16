package online.db1k.safering.android.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

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
        DATABASE("Database"),
        SECURITY("Security"),
        ENTITLEMENT("Entitlement"),
        CIRCLE("Circle")
    }

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (_: Exception) {
            null
        }

    fun info(message: String, category: Category = Category.APP) {
        val full = "[${category.tag}] $message"
        Log.i(TAG, full)
    }

    fun warning(message: String, category: Category = Category.APP) {
        val full = "⚠️ [${category.tag}] $message"
        Log.w(TAG, full)
        crashlytics?.log(full)
    }

    fun error(message: String, category: Category = Category.APP, throwable: Throwable? = null) {
        val full = "❌ [${category.tag}] $message"
        Log.e(TAG, full, throwable)
        crashlytics?.apply {
            log(full)
            if (throwable != null) recordException(throwable)
        }
    }

    fun debug(message: String, category: Category = Category.APP) {
        val full = "🔍 [${category.tag}] $message"
        Log.d(TAG, full)
    }

    fun fault(message: String, category: Category = Category.APP, throwable: Throwable? = null) {
        val full = "💥 [${category.tag}] $message"
        Log.wtf(TAG, full, throwable)
        crashlytics?.apply {
            log(full)
            if (throwable != null) recordException(throwable)
            sendUnsentReports()
        }
    }

    fun breadcrumb(name: String, attributes: Map<String, String> = emptyMap()) {
        crashlytics?.apply {
            attributes.forEach { (key, value) -> setCustomKey(key, value) }
            log("Breadcrumb: $name $attributes")
        }
        debug("Breadcrumb: $name $attributes", Category.UI)
    }
}
