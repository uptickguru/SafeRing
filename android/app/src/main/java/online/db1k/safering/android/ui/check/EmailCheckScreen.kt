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
 * Screen for checking an email address for scam content.
 *
 * # Security
 * The email text is submitted as-is. The API analyzes it for known scam
 * patterns, phishing links, and social engineering tactics.
 *
 */
@Composable
fun EmailCheckScreen(
    viewModel: SubmitToCheckViewModel,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check Email") },
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

            // Text Field
            emailTextFieldSection
                .padding(16.dp)

            // Check Button
            if (viewModel.isCheckingEmail) {
                loadingIndicator
                    .padding(16.dp)
            } else if (viewModel.emailResult != null) {
                resultSection
                    .padding(16.dp)
            } else {
                checkButton
                    .padding(16.dp)
            }

            // Error Message
            if (viewModel.emailError != null) {
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
            imageVector = Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = SafeGreen
        )

        Text(
            text = "Check Email",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Text(
            text = "Paste or forward the email you want to check for scam content.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MARK: - Text Field Section

@Composable
private fun emailTextFieldSection() {
    var emailText by remember { mutableStateOf(viewModel.emailText) }

    OutlinedTextField(
        value = emailText,
        onValueChange = { emailText = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        placeholder = { Text("Paste email text here...") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.MultiLine),
        maxLines = 10,
        minLines = 3,
        textStyle = MaterialTheme.typography.bodyLarge
    )

    // Placeholder hint
    if (emailText.isEmpty) {
        Text(
            text = "Paste or forward the email you want to check...",
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
    val result = viewModel.emailResult!!

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
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
                    // Report the email as a scam
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

// MARK: - Check Button

@Composable
private fun checkButton() {
    Button(
        onClick = {
            // Check the email
            viewModel.checkEmail()
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
                text = "Check Email",
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = CriticalRed
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = viewModel.emailError ?: "Unknown error",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

// MARK: - Helpers

@Composable
private fun Spacer() {
    Spacer(modifier = Modifier.width(8.dp))
}
