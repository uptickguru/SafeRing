package online.db1k.safering.android.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.repository.ScamRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen() {
    var phoneNumber by remember { mutableStateOf("") }
    var scamType by remember { mutableStateOf("Phone-Scam") }
    var isSubmitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val scamTypes = listOf(
        "Phone-Scam", "IRS-Impression", "Tech-Support",
        "Grandparent", "Robocall", "Phishing", "Other"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Report a Scam Number",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                placeholder = { Text("+1 (555) 123-4567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scam Type",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scam type chips
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scamTypes.forEach { type ->
                    FilterChip(
                        selected = scamType == type,
                        onClick = { scamType = type },
                        label = { Text(type) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (phoneNumber.isBlank()) return@Button
                    scope.launch {
                        isSubmitting = true
                        try {
                            val context = reportContext ?: return@launch
                            val db = AppDatabase.getInstance(context)
                            val api = SafeRingApi.create()
                            val repo = ScamRepository(api, db)
                            val response = repo.submitReport(
                                phoneNumber = phoneNumber,
                                scamType = scamType
                            )
                            result = "Report submitted! Total reports: ${response.totalReports ?: 1}"
                            phoneNumber = ""
                        } catch (e: Exception) {
                            result = "Failed to submit: ${e.message}"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phoneNumber.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Submit Report")
            }

            result?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 Privacy Protected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Only the SHA-256 hash of the phone number is sent to our servers. Your privacy is guaranteed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// Hack for now — will use DI later
var reportContext: android.content.Context? = null
