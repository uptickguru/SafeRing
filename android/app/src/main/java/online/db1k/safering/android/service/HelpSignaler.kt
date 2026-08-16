package online.db1k.safering.android.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

enum class HelpReason(val shortLabel: String) {
    MONEY("someone asking for money"),
    HELP("tapped Help"),
    AFTER_CALL("a call just ended and they want a check-in"),
    PASTE_SCAM("they pasted a message that looks like a scam"),
    VERIFY("they want you to verify a call or message");
}

class HelpSignaler(
    private val context: Context,
    private val household: HouseholdStore
) {
    fun draftBody(reason: HelpReason): String {
        val name = household.ownerDisplayName.ifBlank { "Your person" }
        val trusted = household.trustedContactName.ifBlank { "there" }
        return """
            SafeRing alert for $trusted:

            $name — ${reason.shortLabel}.

            Call them on their saved number. Do not call back an unknown number. Do not send money or gift cards.

            This is a redacted family alert. No caller ID or message content is included.
        """.trimIndent()
    }

    fun send(reason: HelpReason) {
        when (household.preferredChannel) {
            SignalChannel.SMS -> openSms(reason)
            SignalChannel.WHATSAPP -> openWhatsApp(reason)
            SignalChannel.SIGNAL -> openSignal()
            SignalChannel.PHONE -> callSaved()
        }
        household.recordHelpSent()
    }

    fun openSms(reason: HelpReason) {
        val number = household.e164Number
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", draftBody(reason))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun callSaved() {
        val number = household.e164Number
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSignal() {
        val number = household.e164Number
        val uri = Uri.parse("sgnl://signal.me/#p/$number")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canHandle(intent)) {
            context.startActivity(intent)
            return
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(web)
    }

    fun openWhatsApp(reason: HelpReason) {
        val digits = household.e164Number.filter { it.isDigit() }
        val text = Uri.encode(draftBody(reason))
        val uri = Uri.parse("https://wa.me/$digits?text=$text")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isInstalled(channel: SignalChannel): Boolean {
        val pkg = when (channel) {
            SignalChannel.SIGNAL -> "org.thoughtcrime.securesms"
            SignalChannel.WHATSAPP -> "com.whatsapp"
            else -> return true
        }
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun canHandle(intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }
}
