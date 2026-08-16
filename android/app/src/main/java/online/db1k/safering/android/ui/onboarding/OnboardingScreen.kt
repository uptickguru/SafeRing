package online.db1k.safering.android.ui.onboarding

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.service.HouseholdStore

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    var step by remember { mutableIntStateOf(0) }
    var owner by remember { mutableStateOf(household.ownerDisplayName) }
    var trustedName by remember { mutableStateOf(household.trustedContactName) }
    var trustedNumber by remember { mutableStateOf(household.trustedContactNumber) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestCallScreening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else {
                roleLauncher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = (step + 1) / 5f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> StepCopy(
                title = "A person, not a database",
                body = "Your phone and carrier already block most junk. SafeRing texts someone you trust when a call or message still gets through."
            )
            1 -> {
                StepCopy("What should we call you?", "Your person sees this name in the alert.")
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            2 -> {
                StepCopy("Who do we text?", "Use your other number for this test. Help never dials the incoming caller.")
                OutlinedTextField(
                    value = trustedName,
                    onValueChange = { trustedName = it },
                    label = { Text("Their name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = trustedNumber,
                    onValueChange = { trustedNumber = it },
                    label = { Text("Their phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            3 -> {
                StepCopy("Family password", "This stays on this phone. If someone claims to be family, you ask them this.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Type it again") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                StepCopy(
                    "Let the phone do the blocking",
                    "Turn SafeRing on as the caller ID / spam app. Contacts still ring. Unknown numbers are silenced."
                )
                Checklist(
                    title = "SafeRing is the call screening app",
                    checked = household.callScreeningConfirmed,
                    onChecked = { household.callScreeningConfirmed = it },
                    actionLabel = "Request call screening",
                    onAction = { requestCallScreening() }
                )
                Checklist(
                    title = "Carrier scam block is on",
                    checked = household.carrierProtectionConfirmed,
                    onChecked = { household.carrierProtectionConfirmed = it }
                )
                Checklist(
                    title = "I understand unknown callers may be silenced",
                    checked = household.silenceUnknownConfirmed,
                    onChecked = { household.silenceUnknownConfirmed = it }
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                error = null
                when (step) {
                    0 -> step = 1
                    1 -> {
                        if (owner.trim().length < 2) {
                            error = "Type the name your person should see."
                        } else {
                            household.ownerDisplayName = owner.trim()
                            step = 2
                        }
                    }
                    2 -> {
                        if (HouseholdStore.normalizeToE164(trustedNumber).filter { it.isDigit() }.length < 10) {
                            error = "Enter your person's real phone number."
                        } else {
                            household.trustedContactName = trustedName.ifBlank { "My person" }
                            household.trustedContactNumber = trustedNumber
                            step = 3
                        }
                    }
                    3 -> {
                        if (password.trim().length < 3) {
                            error = "Pick a password only your family knows."
                        } else if (password != confirm) {
                            error = "The two passwords do not match."
                        } else {
                            household.familyPassword = password.trim()
                            step = 4
                        }
                    }
                    else -> {
                        contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                        household.hasCompletedOnboarding = true
                        onFinished()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(if (step == 4) "Start protection" else "Continue", fontWeight = FontWeight.Bold)
        }
        if (step > 0) {
            TextButton(onClick = { step -= 1; error = null }) { Text("Back") }
        }
    }
}

@Composable
private fun StepCopy(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Checklist(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onChecked)
                Text(title, modifier = Modifier.weight(1f))
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}
