package online.db1k.safering.android.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.local.models.CallLogEntity
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.HmacHashUtils
import online.db1k.safering.android.util.DeviceComms
import online.db1k.safering.android.util.Logger
import online.db1k.safering.android.util.PhoneNumberUtils

/**
 * Privacy-preserving intake for screened calls.
 * Stores HMAC hash + senior-safe labels only — never raw caller ID in History UI.
 */
object CallIntake {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class Disposition(val label: String) {
        TRUSTED("Trusted person"),
        CONTACT("In Contacts"),
        SILENCED("Unknown · silenced"),
        ALLOWED("Allowed")
    }

    fun record(
        context: Context,
        rawHandle: String,
        disposition: Disposition,
        silenced: Boolean,
        stirStatus: Int = 0
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val e164 = PhoneNumberUtils.normalizeToE164(rawHandle)
                val hash = if (PhoneNumberUtils.isPlausibleE164(e164)) {
                    HmacHashUtils.hmacSHA256(e164, AppConfig.HMAC_KEY)
                } else {
                    "unknown-${System.currentTimeMillis()}"
                }
                val risk = when (disposition) {
                    Disposition.TRUSTED, Disposition.CONTACT -> 0.0
                    Disposition.SILENCED -> 0.55
                    Disposition.ALLOWED -> 0.15
                }
                val scamType = when {
                    silenced -> "unknown_silenced"
                    disposition == Disposition.TRUSTED -> "trusted"
                    disposition == Disposition.CONTACT -> "contact"
                    else -> null
                }
                val label = buildString {
                    append(disposition.label)
                    if (stirStatus != 0) append(" · network check $stirStatus")
                }
                AppDatabase.getInstance(app).callLogDao().insert(
                    CallLogEntity(
                        numberHash = hash,
                        callerName = label,
                        riskScore = risk,
                        riskLabel = if (silenced) "Review" else "OK",
                        scamType = scamType,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0,
                        wasAnswered = false,
                        wasBlocked = silenced
                    )
                )
                Logger.info(
                    "Call logged hash=${hash.take(8)}… disp=${disposition.name} silenced=$silenced",
                    Logger.Category.CALL
                )
                DeviceComms.log(
                    app,
                    entryPoint = "call-screening",
                    channel = "voice",
                    action = disposition.name.lowercase(),
                    sender = hash.take(16),
                    meta = mapOf("silenced" to silenced, "stir" to stirStatus)
                )
            } catch (e: Exception) {
                Logger.debug("CallIntake failed: ${e.message}", Logger.Category.CALL)
            }
        }
    }
}
