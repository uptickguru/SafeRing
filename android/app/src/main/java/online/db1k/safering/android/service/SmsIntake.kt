package online.db1k.safering.android.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.local.models.ScamNumberEntity
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
                val db = AppDatabase.getInstance(app)
                db.smsLogDao().insert(
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
                if (sender != null && result.verdict != ScamVerdict.LOOKS_OKAY) {
                    seedScamFilter(db, hash, result.score, result.reasons.firstOrNull() ?: label)
                }
                Logger.info(
                    "SMS check logged hash=${hash.take(8)}… verdict=${result.verdict} phones=${fromBody.size}",
                    Logger.Category.SMS
                )
            } catch (e: Exception) {
                Logger.debug("SmsIntake failed: ${e.message}", Logger.Category.SMS)
            }
        }
    }

    /**
     * Notification-path intake: extract sender, score snippet, log + seed local filter.
     * Does **not** upload raw numbers. Optional local "garbage" seed when high risk.
     */
    fun recordFromNotification(
        context: Context,
        packageName: String,
        senderRaw: String?,
        title: String,
        snippet: String,
        allText: String
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val contentResult = OnDeviceScamChecker.check(
                    if (snippet.isNotBlank()) snippet else allText
                )
                val sender = senderRaw?.takeIf { PhoneNumberUtils.isPlausibleE164(it) }
                    ?.let { PhoneNumberUtils.normalizeToE164(it) }

                // Reputation: known hash in local filter
                val db = AppDatabase.getInstance(app)
                val hash = if (sender != null) {
                    HmacHashUtils.hmacSHA256(sender, AppConfig.HMAC_KEY)
                } else {
                    "notif-${HmacHashUtils.hmacSHA256(allText.take(200), AppConfig.HMAC_KEY).take(16)}"
                }
                val known = db.scamNumberDao().getByHash(hash)
                var score = contentResult.score
                val reasons = contentResult.reasons.toMutableList()
                if (known != null) {
                    score = maxOf(score, known.riskScore)
                    reasons.add(0, "Number on local filter (${known.scamLabel})")
                }
                if (sender == null) {
                    reasons.add("No phone in notification (name-only / RCS)")
                }

                val verdict = when {
                    score >= 0.6 -> ScamVerdict.LIKELY_SCAM
                    score >= 0.3 -> ScamVerdict.SUSPICIOUS
                    else -> ScamVerdict.LOOKS_OKAY
                }
                val label = when (verdict) {
                    ScamVerdict.LIKELY_SCAM -> "Likely scam text"
                    ScamVerdict.SUSPICIOUS -> "Suspicious text"
                    ScamVerdict.LOOKS_OKAY -> if (sender != null) "Text seen" else "Text seen · no #"
                }

                db.smsLogDao().insert(
                    SmsLogEntity(
                        numberHash = hash,
                        messageBody = null, // never store notif body by default
                        riskScore = score,
                        riskLabel = label,
                        scamType = reasons.firstOrNull() ?: packageName,
                        timestamp = System.currentTimeMillis(),
                        wasBlocked = verdict == ScamVerdict.LIKELY_SCAM || (known?.shouldBlock == true)
                    )
                )

                if (sender != null && verdict != ScamVerdict.LOOKS_OKAY) {
                    seedScamFilter(
                        db,
                        hash,
                        score,
                        reasons.firstOrNull() ?: "notif_scam"
                    )
                }

                if (verdict == ScamVerdict.LIKELY_SCAM || (known?.shouldBlock == true)) {
                    TripwireNotifier.notifySuspiciousSms(
                        app,
                        hasNumber = sender != null
                    )
                }

                Logger.info(
                    "SMS notif pkg=$packageName hash=${hash.take(8)}… sender=${sender != null} score=$score",
                    Logger.Category.SMS
                )
            } catch (e: Exception) {
                Logger.debug("recordFromNotification failed: ${e.message}", Logger.Category.SMS)
            }
        }
    }

    /** Local filter seed — same table used for future call/SMS reputation. */
    private suspend fun seedScamFilter(
        db: AppDatabase,
        hash: String,
        score: Double,
        label: String
    ) {
        val existing = db.scamNumberDao().getByHash(hash)
        val nextScore = maxOf(existing?.riskScore ?: 0.0, score)
        db.scamNumberDao().upsert(
            ScamNumberEntity(
                numberHash = hash,
                riskScore = nextScore,
                scamLabel = label.take(80),
                confidence = nextScore,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                firstReportedAt = existing?.firstReportedAt ?: System.currentTimeMillis(),
                reportCount = (existing?.reportCount ?: 0) + 1,
                shouldBlock = nextScore >= 0.6 || (existing?.shouldBlock == true)
            )
        )
    }
}
