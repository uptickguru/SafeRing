package online.db1k.safering.android.ui.lessons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lessons screen with senior-friendly, offline-readable scam awareness content.
 *
 * # Security
 * This screen is OFFLINE-ONLY. No data is collected. No analytics. No PII.
 *
 * # Senior-Friendly Design
 * - Large text (≥18sp) for readability
 * - One idea per card
 * - Simple language
 * - No login required
 * - Offline-readable
 */
@Composable
fun LessonsScreen() {
    val selectedTab = remember { mutableIntStateOf(0) }

    val tabs = listOf(
        LessonsTab.CallbackRules,
        LessonsTab.WarningSigns,
        LessonsTab.NeverGive,
        LessonsTab.FamilyPassword
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tab navigation
        TabRow(
            selectedTabIndex = selectedTab.value,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab.value == index,
                    onClick = { selectedTab.value = index },
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when (tabs[selectedTab.value]) {
            LessonsTab.CallbackRules -> callbackRulesContent()
            LessonsTab.WarningSigns -> warningSignsContent()
            LessonsTab.NeverGive -> neverGiveContent()
            LessonsTab.FamilyPassword -> familyPasswordContent()
        }

        // No-login badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Info,
                contentDescription = "No login required",
                tint = Color(0xFF16A34A)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "No login required",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

private enum class LessonsTab(val title: String) {
    CallbackRules("Callback Rules"),
    WarningSigns("Warning Signs"),
    NeverGive("Never Give"),
    FamilyPassword("Family Password")
}

@Composable
private fun callbackRulesContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Header(title = "Callback Rules", icon = "📞", color = MaterialTheme.colorScheme.primary)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Rule 1: Never Call Back",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "If someone asks you to call back, DON'T. Just hang up.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Rule 2: Use Your Own Number",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Only dial numbers you ALREADY have saved in your contacts.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Rule 3: Call the Family",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "If you're unsure, call a family member you trust. They can help you decide.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "These rules protect you from scammers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun warningSignsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Header(title = "Warning Signs", icon = "⚠️", color = MaterialTheme.colorScheme.tertiary)

        WarningCard(
            modifier = Modifier.fillMaxWidth(),
            icon = "🕐",
            title = "Urgency",
            body = "Scammers push you to act FAST. They say 'you'll lose it' or 'act now!'",
            accentColor = Color(0xFFFFC107)
        )

        WarningCard(
            modifier = Modifier.fillMaxWidth(),
            icon = "🔒",
            title = "Secrecy",
            body = "Scammers say 'don't tell anyone' or 'this is private.'",
            accentColor = Color(0xFFFFC107)
        )

        WarningCard(
            modifier = Modifier.fillMaxWidth(),
            icon = "💳",
            title = "Odd Payment",
            body = "Scammers want payment in weird ways (gift cards, crypto, wire transfers).",
            accentColor = Color(0xFFFFC107)
        )
    }
}

@Composable
private fun neverGiveContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Header(title = "Never Give", icon = "🔒", color = MaterialTheme.colorScheme.primary)

        NeverGiveItem(
            modifier = Modifier.fillMaxWidth(),
            icon = "💳",
            title = "Bank Account Numbers",
            body = "Never give your bank account number to a phone call."
        )

        NeverGiveItem(
            modifier = Modifier.fillMaxWidth(),
            icon = "🔑",
            title = "Passwords",
            body = "Never give your passwords to anyone over the phone."
        )

        NeverGiveItem(
            modifier = Modifier.fillMaxWidth(),
            icon = "📧",
            title = "Security Questions",
            body = "Never answer security questions to a stranger."
        )

        NeverGiveItem(
            modifier = Modifier.fillMaxWidth(),
            icon = "👤",
            title = "Personal Info",
            body = "Never share your address or birthday with a caller."
        )
    }
}

@Composable
private fun Header(title: String, icon: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            modifier = Modifier.width(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun WarningCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    body: String,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = icon,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NeverGiveItem(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    body: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 24.sp,
                modifier = Modifier.width(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SuccessCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    body: String,
    accentColor: Color = Color(0xFF16A34A)
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 24.sp,
                modifier = Modifier.width(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun familyPasswordContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Header(title = "Family Password", icon = "👨‍👩‍👧‍👦", color = MaterialTheme.colorScheme.primary)

        SuccessCard(
            modifier = Modifier.fillMaxWidth(),
            icon = "👤",
            title = "Pick a Trusted Family Member",
            body = "Choose one person you trust. They can help you when you're unsure."
        )

        SuccessCard(
            modifier = Modifier.fillMaxWidth(),
            icon = "📱",
            title = "Use the App to Check",
            body = "When in doubt, use GMG Shield to check the number first."
        )

        SuccessCard(
            modifier = Modifier.fillMaxWidth(),
            icon = "🛡️",
            title = "Ask Before You Call",
            body = "Before calling anyone, ask your trusted person: 'Should I call this number?'",
            accentColor = Color(0xFF16A34A)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your family password is a safety net.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}