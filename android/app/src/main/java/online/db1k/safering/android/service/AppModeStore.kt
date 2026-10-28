package online.db1k.safering.android.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import online.db1k.safering.android.BuildConfig

/**
 * Personal / Just me / Trusted contact + Free plan.
 * Just me = screen calls & junk only — no trusted person required.
 */
class AppModeStore private constructor(private val prefs: SharedPreferences) {

    enum class Role(val key: String, val title: String, val subtitle: String) {
        SENIOR("senior", "Personal", "This is your phone — big HELP, Protect, one person to reach."),
        SOLO("solo", "Just me", "Screen calls & junk mail only — no trusted person to set up."),
        CARETAKER("caretaker", "Trusted contact", "You're their backup — alerts, approve talk, stay in the loop.");

        val requiresTrustedContact: Boolean get() = this != SOLO

        companion object {
            fun fromKey(key: String?): Role =
                entries.firstOrNull { it.key == key } ?: SENIOR
        }
    }

    enum class Plan(val key: String, val title: String, val blurb: String) {
        FREE("free", "Free", "Protect Call, call/SMS filters free. Add a person anytime for HELP."),
        FAMILY("family", "Family", "More trusted contacts + full SafeCall trunk when unlocked.");

        companion object {
            fun fromKey(key: String?): Plan =
                entries.firstOrNull { it.key == key } ?: FREE
        }
    }

    enum class DisplayDensity(val key: String, val title: String, val subtitle: String) {
        EASY("easy", "Easy", "Giant HELP. Fewer choices on the home screen."),
        STANDARD("standard", "Standard", "More tools on home — Protect and status first. Clean modern look.");

        companion object {
            fun fromKey(key: String?): DisplayDensity =
                entries.firstOrNull { it.key == key } ?: EASY
        }
    }

    var role: Role
        get() = Role.fromKey(prefs.getString(KEY_ROLE, null))
        set(value) { prefs.edit().putString(KEY_ROLE, value.key).apply() }

    var plan: Plan
        get() = if (BuildConfig.FREE_ONLY) Plan.FREE else Plan.fromKey(prefs.getString(KEY_PLAN, null))
        set(value) {
            val stored = if (BuildConfig.FREE_ONLY) Plan.FREE else value
            prefs.edit().putString(KEY_PLAN, stored.key).apply()
        }

    var displayDensity: DisplayDensity
        get() = DisplayDensity.fromKey(prefs.getString(KEY_DENSITY, null))
        set(value) { prefs.edit().putString(KEY_DENSITY, value.key).apply() }

    val isEasyHome: Boolean get() = displayDensity == DisplayDensity.EASY
    val isSolo: Boolean get() = role == Role.SOLO

    fun suggestDensityForCurrentRole() {
        displayDensity = when (role) {
            Role.SENIOR -> DisplayDensity.EASY
            Role.SOLO, Role.CARETAKER -> DisplayDensity.STANDARD
        }
    }

    var seniorLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ON, false)
        set(value) { prefs.edit().putBoolean(KEY_LOCK_ON, value).apply() }

    val hasSeniorLockPin: Boolean
        get() = !prefs.getString(KEY_PIN, null).isNullOrBlank()

    val isFree: Boolean get() = plan == Plan.FREE
    val protectIncluded: Boolean get() = true
    val maxTrustedContacts: Int
        get() = when {
            role == Role.SOLO -> 0
            isFree -> 1
            else -> 8
        }

    fun setSeniorLockPin(pin: String): Boolean {
        val digits = pin.filter { it.isDigit() }
        if (digits.length != 6) return false
        prefs.edit().putString(KEY_PIN, digits).putBoolean(KEY_LOCK_ON, true).apply()
        return true
    }

    fun clearSeniorLockPin() {
        prefs.edit().remove(KEY_PIN).putBoolean(KEY_LOCK_ON, false).apply()
    }

    fun verifySeniorLockPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        return pin.filter { it.isDigit() } == stored
    }

    fun trySetRole(newRole: Role, pin: String? = null): Boolean {
        if (newRole == role) return true
        if (role == Role.SENIOR && seniorLockEnabled && hasSeniorLockPin) {
            if (pin == null || !verifySeniorLockPin(pin)) return false
        }
        role = newRole
        return true
    }

    fun reset() {
        prefs.edit()
            .remove(KEY_ROLE)
            .remove(KEY_PLAN)
            .remove(KEY_DENSITY)
            .remove(KEY_PIN)
            .remove(KEY_LOCK_ON)
            .apply()
    }

    companion object {
        private const val PREFS = "gmg_app_mode"
        private const val KEY_ROLE = "role"
        private const val KEY_PLAN = "plan"
        private const val KEY_DENSITY = "display_density"
        private const val KEY_PIN = "senior_pin"
        private const val KEY_LOCK_ON = "senior_lock_on"

        @Volatile private var instance: AppModeStore? = null

        fun get(context: Context): AppModeStore {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val app = context.applicationContext
                val master = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                val prefs = try {
                    EncryptedSharedPreferences.create(
                        app,
                        PREFS,
                        master,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (_: Exception) {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                }
                return AppModeStore(prefs).also { instance = it }
            }
        }
    }
}
