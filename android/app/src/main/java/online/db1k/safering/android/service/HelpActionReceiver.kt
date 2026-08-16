package online.db1k.safering.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HelpActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_HELP -> {
                val household = HouseholdStore.get(context)
                val reason = intent.getStringExtra(TripwireNotifier.EXTRA_HELP_REASON)
                    ?.let { runCatching { HelpReason.valueOf(it) }.getOrNull() }
                    ?: HelpReason.AFTER_CALL
                HelpSignaler(context.applicationContext, household).send(reason)
                TripwireNotifier.cancelUnknownCall(context)
            }
            ACTION_OK -> {
                HouseholdStore.get(context).consumeUnknownCallCheckIn()
                TripwireNotifier.cancelUnknownCall(context)
            }
        }
    }

    companion object {
        const val ACTION_HELP = "online.db1k.safering.android.HELP"
        const val ACTION_OK = "online.db1k.safering.android.OK"
    }
}
