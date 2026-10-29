package online.db1k.safering.android.ui.settings

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.service.FilterRulesStore
import online.db1k.safering.android.ui.theme.SafeRingTheme
import online.db1k.safering.android.util.ShieldAnalytics

class FilterWordsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        setContent {
            SafeRingTheme { FilterWordsScreen(onClose = { finish() }) }
        }
    }
}

class BlockListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        setContent {
            SafeRingTheme { BlockListScreen(onClose = { finish() }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterWordsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var words by remember { mutableStateOf(FilterRulesStore.keywords(context)) }
    var draft by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filter words") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("A text that contains one of these words is treated as junk. Android has no system Junk folder.")
            words.forEach { word ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(word, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        FilterRulesStore.removeKeyword(context, word)
                        words = FilterRulesStore.keywords(context)
                    }) { Text("Remove") }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Add a word") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    FilterRulesStore.addKeyword(context, draft)
                    draft = ""
                    words = FilterRulesStore.keywords(context)
                    ShieldAnalytics.event(context, "filter_keyword_add")
                },
                enabled = draft.trim().length >= 2,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add word") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockListScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var numbers by remember { mutableStateOf(FilterRulesStore.blockDigits(context).toList().sorted()) }
    var draft by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Block list") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("These numbers stay quiet when HumanAnswer is the Caller ID and spam app.")
            Text("Blocked: ${numbers.size}")
            numbers.forEach { digits ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(digits, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        FilterRulesStore.removeBlockDigits(context, digits)
                        numbers = FilterRulesStore.blockDigits(context).toList().sorted()
                    }) { Text("Remove") }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Add a number") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    FilterRulesStore.addBlockDigits(context, draft)
                    draft = ""
                    numbers = FilterRulesStore.blockDigits(context).toList().sorted()
                    ShieldAnalytics.event(context, "block_list_add")
                },
                enabled = draft.filter { it.isDigit() }.length >= 10,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add to block list") }
        }
    }
}
