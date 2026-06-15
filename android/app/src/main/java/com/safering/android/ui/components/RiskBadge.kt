package com.safering.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safering.android.ui.theme.SafeRingColors

/**
 * Color-coded risk indicator for scam risk levels.
 *
 * Displays a colored badge with the risk level label.
 * Colors are high-contrast for visibility:
 * - Red: High risk (≥0.7)
 * - Orange: Medium risk (0.4-0.7)
 * - Yellow: Low risk (0.1-0.4)
 * - Green: Safe (0.0)
 * - Gray: Unknown
 */
@Composable
fun RiskBadge(
    riskScore: Float,
    scamLabel: String? = null,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    size: Dp = 48.dp
) {
    val badgeColor by animateColorAsState(
        targetValue = when {
            riskScore >= 0.7f -> SafeRingColors.RiskHigh
            riskScore >= 0.4f -> SafeRingColors.RiskMedium
            riskScore > 0f -> SafeRingColors.RiskLow
            else -> SafeRingColors.RiskSafe
        },
        label = "badgeColor"
    )

    val label = when {
        scamLabel != null -> scamLabel
        riskScore >= 0.7f -> "High Risk"
        riskScore >= 0.4f -> "Medium Risk"
        riskScore > 0f -> "Low Risk"
        else -> "Safe"
    }

    val emoji = when {
        riskScore >= 0.7f -> "🚨"
        riskScore >= 0.4f -> "⚠️"
        riskScore > 0f -> "⚡"
        else -> "✅"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showText) {
            Text(
                text = "$emoji $label",
                color = badgeColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        } else {
            // Dot-only variant for compact displays
            Box(
                modifier = Modifier
                    .size(size * 0.4f)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
        }
    }
}

/**
 * Minimal risk indicator dot — for use in lists and compact views.
 */
@Composable
fun RiskDot(
    riskScore: Float,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val dotColor = when {
        riskScore >= 0.7f -> SafeRingColors.RiskHigh
        riskScore >= 0.4f -> SafeRingColors.RiskMedium
        riskScore > 0f -> SafeRingColors.RiskLow
        else -> SafeRingColors.RiskSafe
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(dotColor)
    )
}
