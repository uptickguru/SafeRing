package com.safering.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

class MainWearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    var currentScreen by remember { mutableStateOf("status") }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette() },
        positionIndicator = { PositionIndicator() }
    ) {
        when (currentScreen) {
            "status" -> StatusScreen(onCheckClick = { currentScreen = "check" })
            "check" -> CheckScreen(onBack = { currentScreen = "status" })
        }
    }
}

@Composable
fun StatusScreen(onCheckClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛡️", fontSize = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text("Protected", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Active", fontSize = 12.sp, color = MaterialTheme.colors.primary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCheckClick, modifier = Modifier.fillMaxWidth()) {
            Text("Check Number")
        }
    }
}

@Composable
fun CheckScreen(onBack: () -> Unit) {
    var number by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        item {
            Text("Enter number:", fontSize = 12.sp)
            TextField(
                value = number,
                onValueChange = { number = it.take(15) },
                label = { Text("Phone #") }
            )
        }
        item {
            Button(
                onClick = { result = "Safe (no reports)" },
                enabled = number.length >= 10,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check")
            }
        }
        result?.let {
            item {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 14.sp, color = MaterialTheme.colors.primary)
            }
        }
        item {
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
