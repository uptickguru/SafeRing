package online.db1k.safering.android.service

import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import online.db1k.safering.android.util.Logger
import online.db1k.safering.android.util.PhoneNumberUtils

/**
 * Free-tier screening: contacts and the trusted person always ring.
 * Unknown numbers are silenced (still appear in Recents).
 * Caller handle → E.164 → HMAC log via [CallIntake] (no raw number in UI).
 */
@RequiresApi(Build.VERSION_CODES.N)
class SafeRingCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(details, allow())
            return
        }

        val raw = details.handle?.schemeSpecificPart.orEmpty()
        val household = HouseholdStore.get(this)
        val trusted = PhoneNumberUtils.normalizeToE164(household.trustedContactNumber)
        val incoming = PhoneNumberUtils.normalizeToE164(raw)

        val isTrusted = incoming.isNotBlank() &&
            PhoneNumberUtils.isPlausibleE164(incoming) &&
            incoming == trusted
        val isContact = isInContacts(this, raw) || isInContacts(this, incoming)

        val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.callerNumberVerificationStatus
        } else {
            0
        }

        if (isTrusted || isContact) {
            val disp = if (isTrusted) CallIntake.Disposition.TRUSTED else CallIntake.Disposition.CONTACT
            CallIntake.record(this, raw.ifBlank { incoming }, disp, silenced = false, stirStatus = verification)
            respondToCall(details, allow())
            Logger.info("Allowed ${disp.name} incoming", Logger.Category.CALL)
            return
        }

        household.recordUnknownCall()
        CallIntake.record(
            this,
            raw.ifBlank { incoming },
            CallIntake.Disposition.SILENCED,
            silenced = true,
            stirStatus = verification
        )
        respondToCall(details, silence())
        TripwireNotifier.notifyUnknownCallSilenced(this)
        Logger.info(
            "Silenced unknown incoming (stir=$verification)",
            Logger.Category.CALL
        )
    }

    private fun allow(): CallResponse = CallResponse.Builder()
        .setDisallowCall(false)
        .setRejectCall(false)
        .build()

    private fun silence(): CallResponse {
        val builder = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setSilenceCall(true)
        }
        return builder.build()
    }

    private fun isInContacts(context: Context, rawNumber: String): Boolean {
        if (rawNumber.isBlank()) return false
        return try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(rawNumber)
                .build()
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        } catch (e: SecurityException) {
            Logger.debug("Contacts lookup skipped: ${e.message}", Logger.Category.CALL)
            false
        } catch (e: Exception) {
            Logger.debug("Contacts lookup failed: ${e.message}", Logger.Category.CALL)
            false
        }
    }
}
