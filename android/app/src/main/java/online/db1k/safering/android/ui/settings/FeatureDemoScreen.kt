package online.db1k.safering.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class DemoPage(val title: String, val body: String, val howTo: String)

private val ivory = Color(0xFFF7F6F4)
private val gold = Color(0xFF9E8557)
private val ink = Color(0xFF292624)
private val burgundy = Color(0xFF7A383D)

val gmgDemoPages = listOf(
    DemoPage("What Shield watches", "GMG Shield watches for scams on this phone — unknown callers, suspicious SMS, and HELP to your person. It does not take over the dialer while a call is ringing.", "Open the app once after every update."),
    DemoPage("Call screening (Android strength)", "When Shield is the Caller ID and spam app, unknown callers can be silenced while contacts still ring. This is stronger than iPhone for stopping the first ring.", "Settings → Apps → Special app access → Caller ID and spam → GMG Shield."),
    DemoPage("SMS tripwire", "Optional Notification access can catch plain SMS senders. Cheap phones often need Allow restricted settings first. Investigate a number always works without NLS.", "Prefer Investigate if the OS blocks notification access."),
    DemoPage("HELP and Call person", "Giant HELP texts your trusted person. Call uses the real phone number so Android and iPhone families both work.", "Set your person under Settings. Tap HELP or Call on Home."),
    DemoPage("Reporting", "Report junk so the family network can log the full message when the phone sends it.", "Use Investigate or report in Settings."),
    DemoPage("More secure on cheap Android", "Screen lock ON. Install only from family or Play. Play Protect ON. Deny becoming default SMS app for strangers. Disable unknown sources after install.", "Settings → Security → screen lock."),
    DemoPage("Print the fridge card", "A small card lists what is watched and the toggles to turn on.", "Settings → Print protection card → Share or Print.")
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeatureDemoScreen(onClose: () -> Unit) {
    val pages = gmgDemoPages
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Feature demo") }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } })
    }, containerColor = ivory) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text(p.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink)
                    Spacer(Modifier.height(12.dp))
                    Text(p.body, fontSize = 16.sp, color = ink.copy(alpha = 0.9f), lineHeight = 22.sp)
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.85f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("How to use", fontWeight = FontWeight.SemiBold, color = burgundy)
                            Spacer(Modifier.height(6.dp))
                            Text(p.howTo, color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Back") }
                else Spacer(Modifier.width(1.dp))
                Text("${pager.currentPage + 1} / ${pages.size}", color = Color.Gray)
                if (pager.currentPage < pages.lastIndex) Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, colors = ButtonDefaults.buttonColors(containerColor = ink)) { Text("Next") }
                else Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF47665C))) { Text("Done") }
            }
        }
    }
}

class FeatureDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { FeatureDemoScreen(onClose = { finish() }) } }
    }
}

class ProtectionCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ProtectionCardScreen(onClose = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionCardScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("Printable card") }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } })
    }, containerColor = ivory) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Surface(shape = RoundedCornerShape(12.dp), color = ivory, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("GMG SHIELD", color = gold, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontSize = 13.sp)
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = gold.copy(alpha = 0.4f))
                    Text("What this phone watches", fontWeight = FontWeight.SemiBold, color = ink)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Unknown calls silenced when screening is ON",
                        "Contacts still ring",
                        "Suspicious SMS tripwire when allowed",
                        "HELP reaches your person by text/call",
                        "Reports can log full message to family network"
                    ).forEach { Text("• $it", color = ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Text("Turn on (once)", fontWeight = FontWeight.SemiBold, color = ink)
                    listOf(
                        "1. Caller ID and spam app = GMG Shield",
                        "2. Contacts permission ON",
                        "3. Notifications ON",
                        "4. Screen lock PIN/pattern ON",
                        "5. Open Shield once after updates"
                    ).forEach { Text(it, fontSize = 12.sp, color = ink, modifier = Modifier.padding(vertical = 2.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("gulfmeridiangroup.com", fontSize = 10.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { shareProtectionCardPdf(context) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ink)) {
                Text("Share / print PDF card")
            }
            Text("On the share sheet pick Print, Drive, or a printer app.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

fun shareProtectionCardPdf(context: android.content.Context) {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(360, 540, 1).create()
    val page = doc.startPage(pageInfo)
    val c: Canvas = page.canvas
    val title = Paint().apply { textSize = 14f; isFakeBoldText = true; color = android.graphics.Color.rgb(158, 133, 87) }
    val head = Paint().apply { textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.rgb(41, 38, 36) }
    val body = Paint().apply { textSize = 12f; color = android.graphics.Color.rgb(41, 38, 36) }
    var y = 36f
    c.drawColor(android.graphics.Color.rgb(247, 246, 244))
    c.drawText("GMG SHIELD", 24f, y, title); y += 28f
    c.drawText("What this phone watches", 24f, y, head); y += 22f
    listOf(
        "Unknown calls silenced when screening ON",
        "Contacts still ring",
        "SMS tripwire when notification access allowed",
        "HELP texts/calls your person",
        "Reports can send full message to family network",
        "",
        "Turn on once:",
        "1. Caller ID and spam = GMG Shield",
        "2. Contacts permission",
        "3. Notifications ON",
        "4. Screen lock ON",
        "5. Open app after updates",
        "",
        "gulfmeridiangroup.com"
    ).forEach { c.drawText(it, 24f, y, body); y += 18f }
    doc.finishPage(page)
    val file = File(context.cacheDir, "GMG-Shield-protection-card.pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_SUBJECT, "GMG Shield protection card")
    }
    context.startActivity(Intent.createChooser(intent, "Share / print card"))
}
