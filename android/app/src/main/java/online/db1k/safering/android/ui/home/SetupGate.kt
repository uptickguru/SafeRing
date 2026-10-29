package online.db1k.safering.android.ui.home

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import online.db1k.safering.android.MainActivity
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.service.PhoneRoles

private const val RETURN_CHANNEL = "humananswer_return"
private const val RETURN_ID = 4401

/** Blocks Home until the phone setup and a contact are done. */
@Composable
fun SetupGate(trustedContactHome: Boolean) {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    var tick by remember { mutableIntStateOf(0) }
    val refresh = tick
    val contactsOn = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
    val screeningOn = PhoneRoles.holdsCallScreening(context)
    val roleAvailable = PhoneRoles.callScreeningAvailable(context)
    val hasPerson = household.trustedContactNumber.filter { it.isDigit() }.length >= 10
    val ready = contactsOn && hasPerson && (!roleAvailable || screeningOn)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        cancelReturnNote(context)
        tick++
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0).orEmpty()
                    val number = cursor.getString(1).orEmpty()
                    household.trustedContactName = name
                    household.trustedContactNumber = number
                    if (trustedContactHome || household.ownerDisplayName.isBlank()) {
                        household.ownerDisplayName = name
                    }
                }
            }
        }
        tick++
    }
    val life = LocalLifecycleOwner.current
    DisposableEffect(life) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cancelReturnNote(context)
                tick++
            }
        }
        life.lifecycle.addObserver(observer)
        onDispose { life.lifecycle.removeObserver(observer) }
    }

    if (!ready) AlertDialog(
        onDismissRequest = { },
        title = { Text(if (trustedContactHome) "Add your person" else "Finish phone setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This stays up until it is done. Home is underneath.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (hasPerson) "Person: ${household.trustedContactName.ifBlank { "saved" }}"
                    else "No person from contacts yet.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (!roleAvailable) "This phone has no spam-app choice."
                    else if (screeningOn) "Spam app: on"
                    else "Spam app: still needed",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!contactsOn) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Allow contacts") }
                }
                Button(
                    onClick = {
                        if (!contactsOn) {
                            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            pickLauncher.launch(
                                Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add from contacts") }
                if (roleAvailable && !screeningOn) {
                    Button(
                        onClick = {
                            val intent = PhoneRoles.requestCallScreeningIntent(context)
                            if (intent != null) {
                                postReturnNote(context)
                                roleLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Make HumanAnswer the spam app") }
                    Text(
                        "The phone's screen has a back arrow. If you do not see it, pull down the shade and tap Return to HumanAnswer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                @Suppress("UNUSED_EXPRESSION")
                refresh
            }
        },
        confirmButton = {
            TextButton(onClick = { tick++ }, enabled = ready) { Text("Continue") }
        }
    )
}

private fun postReturnNote(context: Context) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= 26) {
        manager.createNotificationChannel(
            NotificationChannel(RETURN_CHANNEL, "Return to HumanAnswer", NotificationManager.IMPORTANCE_HIGH)
        )
    }
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val note = NotificationCompat.Builder(context, RETURN_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_revert)
        .setContentTitle("Return to HumanAnswer")
        .setContentText("Tap here if the phone settings screen has no back.")
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(RETURN_ID, note)
}

private fun cancelReturnNote(context: Context) {
    NotificationManagerCompat.from(context).cancel(RETURN_ID)
}
