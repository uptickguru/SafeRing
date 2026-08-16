package online.db1k.safering.android.ui.circle

import androidx.compose.runtime.Composable
import online.db1k.safering.android.ui.threat.ThreatActionScreen as RealThreatActionScreen
import online.db1k.safering.android.ui.threat.ThreatAction
import online.db1k.safering.android.ui.threat.SavedContact

/** Back-compat wrapper. Use ui.threat.ThreatActionScreen. */
@Composable
fun ThreatActionScreen(
    recommendedAction: ThreatAction = ThreatAction.LoopTrustedContact,
    callerLabel: String = "Suspicious call",
    savedContact: SavedContact? = null,
    userOptedIn: Boolean = true,
    numberHash: String = "",
    wasBlocked: Boolean = false
) {
    RealThreatActionScreen(
        recommendedAction = recommendedAction,
        callerLabel = callerLabel,
        savedContact = savedContact,
        userOptedIn = userOptedIn,
        numberHash = numberHash,
        wasBlocked = wasBlocked,
        onHumanAction = {}
    )
}
