package online.db1k.safering.android.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.local.models.SmsLogEntity
import online.db1k.safering.android.util.AppConfig
import online.db1k.safering.android.util.HmacHashUtils
import online.db1k.safering.android.util.Logger
import online.db1k.safering.android.util.PhoneNumberUtils

object SmsIntake {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recordCheck(
        context: Context,
        body: String,
        senderRaw: String?,
        result: ScamCheckResult,
        storeBody: Boolean = false
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val fromBody = PhoneNumberUtils.extractPhones(body)
                val sender = when {
                    !senderRaw.isNullOrBlank() && PhoneNumberUtils.isPlausibleE164(senderRaw) ->
                        PhoneNumberUtils.normalizeToE164(senderRaw)
                    fromBody.isNotEmpty() -> fromBody.first()
                    else -> null
                }
                val hash = if (sender != null) {
                    HmacHashUtils.hmacSHA256(sender, AppConfig.HMAC_KEY)
                } else {
                    "body-${HmacHashUtils.hmacSHA256(body.take(200), AppConfig.HMAC_KEY).take(16)}"
                }
                val label = when (result.verdict) {
                    ScamVerdict.LIKELY_SCAM -> "Likely scam"
                    ScamVerdict.SUSPICIOUS -> "Suspicious"
                    ScamVerdict.LOOKS_OKAY -> "Looks okay"
                }
                AppDatabase.getInstance(app).smsLogDao().insert(
                    SmsLogEntity(
                        numberHash = hash,
                        messageBody = if (storeBody) body.take(500) else null,
                        riskScore = result.score,
                        riskLabel = label,
                        scamType = result.reasons.firstOrNull(),
                        timestamp = System.currentTimeMillis(),
                        wasBlocked = result.verdict == ScamVerdict.LIKELY_SCAM
                    )
                )
                Logger.info(
                    "SMS check logged hash=${hash.take(8)}… verdict=${result.verdict} phones=${fromBody.size}",
                    Logger.Category.SMS
                )
            } catch (e: Exception) {
                Logger.debug("SmsIntake failed: ${e.message}", Logger.Category.SMS)
            }
        }
    }
}
