package online.db1k.safering.android.ui.check

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Screen for checking a call transcript for scam content.
 *
 * # Security
 * The transcript is submitted as-is. The user must only submit conversations
 * they are lawfully permitted to share.
 *
 */
@Composable
fun TranscriptCheckScreen(
    viewModel: SubmitToCheckViewModel,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check Transcript") },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header
            headerSection
                .padding(16.dp)

            // Consent Notice
            consentNoticeSection
                .padding(16.dp)

            // Text Field
            textFieldSection
                .padding(16.dp)

            // Check Button
            if (viewModel.isCheckingTranscript) {
                loadingIndicator
                    .padding(16.dp)
            } else if (viewModel.transcriptResult != null) {
                resultSection
                    .padding(16.dp)
            } else {
                checkButton
                    .padding(16.dp)
            }

            // Error Message
            if (viewModel.transcriptError != null) {
                errorSection
                    .padding(16.dp)
            }
        }
    }
}

// MARK: - Header Section

@Composable
private fun headerSection() {
    Column(spacing = 16.dp) {
        Icon(
            imageVector = Icons.Default.ChatBubble,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = SafeGreen
        )

        Text(
            text = "Check Transcript",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Text(
            text = "Paste the conversation you want to check for scam content. You must only submit conversations you are lawfully permitted to share.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MARK: - Consent Notice Section

@Composable
private fun consentNoticeSection() {
    var consentAcknowledged by remember { mutableStateOf(viewModel.consentAcknowledged) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarningYellow.opacity(0.1))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = WarningYellow
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Consent Notice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You must only submit conversations you are lawfully permitted to share. By submitting, you confirm you have the right to do so.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!consentAcknowledged) {
                Button(
                    onClick = {
                        consentAcknowledged = true
                        viewModel.setConsentAcknowledged(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("I Understand")
                }
            }
        }
    }
}

// MARK: - Text Field Section

@Composable
private fun textFieldSection() {
    var transcriptText by remember { mutableStateOf(viewModel.transcriptText) }

    OutlinedTextField(
        value = transcriptText,
        onValueChange = { transcriptText = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        placeholder = { Text("Paste transcript text here...") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.MultiLine),
        maxLines = 20,
        minLines = 5,
        textStyle = MaterialTheme.typography.bodyLarge
    )

    // Placeholder hint
    if (transcriptText.isEmpty) {
        Text(
            text = "Paste the conversation you want to check...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// MARK: - Loading Indicator

@Composable
private fun loadingIndicator() {
    Column(spacing = 16.dp) {
        CircularProgressIndicator()

        Text(
            text = "Checking for scams...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MARK: - Result Section

@Composable
private fun resultSection() {
    val result = viewModel.transcriptResult!!

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Risk Score
            Text(
                text = "Risk Score",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.riskScore != null) {
                Text(
                    text = "${Int(result.riskScore * 100)}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            // Scam Type
            if (result.scamType != null) {
                Text(
                    text = result.scamType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Result Text
            if (result.text != null) {
                Text(
                    text = result.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Action Buttons
            if (result.isScam) {
                Button(
                    onClick = {
                        // Report the transcript as a scam
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Exclamation,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Report as Scam",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        // Mark as safe
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Looks Safe",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Check Button

@Composable
private fun checkButton() {
    Button(
        onClick = {
            // Check the transcript
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Check Transcript",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// MARK: - Error Section

@Composable
private fun errorSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = CriticalRed
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = viewModel.transcriptError ?: "Unknown error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// MARK: - Helpers

@Composable
private fun Spacer() {
    Spacer(modifier = Modifier.width(8.dp))
}
