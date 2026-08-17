package online.db1k.safering.android.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.db1k.safering.android.service.HouseholdStore

private val Green = Color(0xFF2E7D32)

/**
 * Senior-first onboarding: one question per screen, huge type.
 * Phone/carrier checklist is deferred to Settings — not a wall of text here.
 */
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

    val runtimePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val total = 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(total) { i ->
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(if (i == step) 28.dp else 10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (i <= step) Green else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (step) {
                0 -> WelcomePane()
                1 -> NamePane(owner) { owner = it }
                2 -> PersonPane(
                    name = trustedName,
                    number = trustedNumber,
                    onName = { trustedName = it },
                    onNumber = { trustedNumber = it }
                )
                else -> PasswordPane(
                    password = password,
                    confirm = confirm,
                    onPassword = { password = it },
                    onConfirm = { confirm = it }
                )
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                error = null
                when (step) {
                    0 -> step = 1
                    1 -> {
                        if (owner.trim().length < 2) {
                            error = "Type your name."
                        } else {
                            household.ownerDisplayName = owner.trim()
                            step = 2
                        }
                    }
                    2 -> {
                        if (HouseholdStore.normalizeToE164(trustedNumber).filter { it.isDigit() }.length < 10) {
                            error = "Enter a real phone number."
                        } else {
                            household.trustedContactName = trustedName.ifBlank { "My person" }
                            household.trustedContactNumber = trustedNumber
                            step = 3
                        }
                    }
                    else -> {
                        if (password.trim().length < 3) {
                            error = "Password needs at least 3 characters."
                        } else if (password != confirm) {
                            error = "Passwords do not match."
                        } else {
                            household.familyPassword = password.trim()
                            val needed = buildList {
                                add(Manifest.permission.READ_CONTACTS)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            runtimePermissions.launch(needed.toTypedArray())
                            household.hasCompletedOnboarding = true
                            onFinished()
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                if (step == total - 1) "Start" else "Next",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (step > 0) {
            TextButton(onClick = { step -= 1; error = null }) {
                Text("Back", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WelcomePane() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SafeRing", fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        Text(
            "Texts someone you trust\nwhen something feels wrong.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 32.sp
        )
    }
}

@Composable
private fun NamePane(value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        Text("Your name", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your person sees this in the alert.",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("Example: Helen", fontSize = 22.sp) },
            textStyle = LocalTextStyle.current.copy(fontSize = 26.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PersonPane(
    name: String,
    number: String,
    onName: (String) -> Unit,
    onNumber: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        Text("Who gets help texts?", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Usually a child or spouse.",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Their name") },
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = number,
            onValueChange = onNumber,
            label = { Text("Their phone number") },
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PasswordPane(
    password: String,
    confirm: String,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        Text("Family password", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask them this if someone claims to be family. Not a bank PIN.",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("Password") },
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = onConfirm,
            label = { Text("Type it again") },
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
