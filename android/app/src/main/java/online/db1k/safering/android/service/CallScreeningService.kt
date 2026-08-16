package online.db1k.safering.android.service

import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import online.db1k.safering.android.util.Logger

/**
 * Free-tier screening: contacts and the trusted person always ring.
 * Unknown / unverified numbers are silenced (still appear in Recents).
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
        val trusted = HouseholdStore.normalizeToE164(household.trustedContactNumber)
        val incoming = HouseholdStore.normalizeToE164(raw)

        val isTrusted = incoming.isNotBlank() && incoming == trusted
        val isContact = isInContacts(this, raw)

        val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.callerNumberVerificationStatus
        } else {
            0
        }

        if (isTrusted || isContact) {
            respondToCall(details, allow())
            Logger.info("Allowed contact/trusted incoming", Logger.Category.CALL)
            return
        }

        // Silence unknown. Do not reject — they can still find it in Recents.
        household.recordUnknownCall()
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
