package com.safering.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safering.android.ui.theme.SafeRingColors

/**
 * Full-screen scam warning dialog.
 *
 * Shown when an incoming call or SMS is classified as high-risk.
 * Designed for clarity and urgency with:
 * - Large red warning banner
 * - Clear risk level + scam type
 * - Big touch targets for actions
 * - High contrast text
 */
@Composable
fun ScamAlertDialog(
    visible: Boolean,
    riskScore: Float,
    scamType: String?,
    scamLabel: String?,
    phoneNumber: String?,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Warning icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SafeRingColors.RiskHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 40.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Risk level
                Text(
                    text = if (riskScore >= 0.85f) "DANGER" else "WARNING",
                    color = SafeRingColors.RiskHigh,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scam type
                if (scamType != null) {
                    Text(
                        text = scamType,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Risk score bar
                RiskScoreBar(
                    riskScore = riskScore,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Label
                if (scamLabel != null) {
                    Text(
                        text = scamLabel,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                // Phone number (masked)
                if (phoneNumber != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = maskPhoneNumber(phoneNumber),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action buttons
                // Block button — prominent, red
                Button(
                    onClick = onBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeRingColors.RiskHigh,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "⛔ Block This Call",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Answer button — less prominent
                OutlinedButton(
                    onClick = onAnswer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Answer Anyway",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dismiss (ignore)
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Ignore",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskScoreBar(
    riskScore: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(riskScore.coerceIn(0f, 1f))
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        riskScore >= 0.7f -> SafeRingColors.RiskHigh
                        riskScore >= 0.4f -> SafeRingColors.RiskMedium
                        else -> SafeRingColors.RiskLow
                    }
                )
        )
        Text(
            text = "${(riskScore * 100).toInt()}% Risk",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

private fun maskPhoneNumber(number: String): String {
    if (number.length <= 6) return "****"
    return number.takeLast(4).let { last4 ->
        number.take(2) + "****" + last4
    }
}
