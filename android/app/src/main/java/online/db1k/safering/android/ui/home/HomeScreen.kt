package online.db1k.safering.android.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import online.db1k.safering.android.service.HelpReason
import online.db1k.safering.android.service.HelpSignaler
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.service.PhoneRoles
import online.db1k.safering.android.service.TripwireNotifier
import online.db1k.safering.android.ui.theme.CallSage
import online.db1k.safering.android.ui.theme.HelpBurgundy
import online.db1k.safering.android.ui.theme.Ink
import online.db1k.safering.android.ui.theme.Ivory
import online.db1k.safering.android.ui.theme.Mute
import online.db1k.safering.android.ui.theme.SoftGold
import online.db1k.safering.android.ui.theme.SurfaceCard
import online.db1k.safering.android.ui.theme.UnsureBronze

/**
 * Full-bleed senior home — parity with iOS timeless stack:
 * header → expanding HELP → fixed Unsure / Call → Message · Code tiles.
 */
@Composable
fun HomeScreen(
    onOpenCheck: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    val signaler = remember { HelpSignaler(context, household) }
    var showPassword by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val refresh = tick

    val screeningOn = PhoneRoles.holdsCallScreening(context)
    val notifyOn = TripwireNotifier.canNotify(context)
    val contactsOn = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { tick++ }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    val person = household.trustedContactName.ifBlank { "my person" }
    val ready = household.isConfigured
    val setupDone = screeningOn && contactsOn && notifyOn
    val statusLine = when {
        !ready -> "Needs setup"
        setupDone -> "Ready · $person"
        else -> "Almost ready · $person"
    }
    val statusOk = ready && setupDone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_status"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GMG SHIELD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = SoftGold
                )
                Text(
                    text = "Protection",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = Ink
                )
            }
            StatusChip(text = statusLine, ok = statusOk)
        }

        Divider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black.copy(alpha = 0.08f)
        )

        Button(
            onClick = { signaler.send(HelpReason.MONEY); tick++ },
            colors = ButtonDefaults.buttonColors(
                containerColor = HelpBurgundy,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .defaultMinSize(minHeight = 160.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(0.08f))
                .testTag("home_help")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HELP", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                Spacer(Modifier.height(10.dp))
                Text("Text $person", fontSize = 17.sp, fontWeight = FontWeight.Normal)
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { signaler.send(HelpReason.HELP); tick++ },
            colors = ButtonDefaults.buttonColors(containerColor = UnsureBronze, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                "Not sure — still text them",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { signaler.callSaved() },
            colors = ButtonDefaults.buttonColors(containerColor = CallSage, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("home_call")
        ) {
            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Call $person", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTile(
                title = "Message",
                icon = { Icon(Icons.Default.Search, null, tint = SoftGold) },
                onClick = onOpenCheck,
                modifier = Modifier.weight(1f).testTag("home_check")
            )
            ToolTile(
                title = "Code",
                icon = { Icon(Icons.Default.Lock, null, tint = SoftGold) },
                onClick = { showPassword = true },
                modifier = Modifier.weight(1f)
            )
        }

        if (!setupDone || !ready) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Phone setup → Settings",
                fontSize = 15.sp,
                color = Mute,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onOpenSettings)
                    .padding(6.dp)
            )
            if (!screeningOn) {
                TextButton(onClick = {
                    roleLauncher.launch(PhoneRoles.requestCallScreeningIntent(context))
                }) { Text("Make GMG Shield the spam app") }
            }
            val needed = buildList {
                if (!contactsOn) add(Manifest.permission.READ_CONTACTS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifyOn) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (needed.isNotEmpty()) {
                TextButton(onClick = { permissionLauncher.launch(needed.toTypedArray()) }) {
                    Text("Allow contacts / notifications")
                }
            }
        }
    }

    if (showPassword) {
        AlertDialog(
            onDismissRequest = { showPassword = false },
            confirmButton = { TextButton(onClick = { showPassword = false }) { Text("OK") } },
            title = { Text("Family password") },
            text = {
                Text(
                    if (household.hasFamilyPassword) {
                        "Ask them first. Yours is: ${household.familyPassword}\n\nHang up if they do not know it."
                    } else {
                        "No password yet. Add one in Settings."
                    }
                )
            }
        )
    }
}

@Composable
private fun StatusChip(text: String, ok: Boolean) {
    val bg = if (ok) CallSage.copy(alpha = 0.12f) else UnsureBronze.copy(alpha = 0.14f)
    val fg = if (ok) CallSage else UnsureBronze
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(fg)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("home_subtitle")
        )
    }
}

@Composable
private fun ToolTile(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}
