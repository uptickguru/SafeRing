package online.db1k.safering.android.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import online.db1k.safering.android.ui.theme.HelpBurgundy
import online.db1k.safering.android.ui.theme.Ink
import online.db1k.safering.android.ui.theme.Ivory
import online.db1k.safering.android.ui.theme.Mute
import online.db1k.safering.android.ui.theme.SafeRingTheme
import online.db1k.safering.android.ui.theme.SoftGold
import java.util.Locale

/**
 * Protect Call — notice-first then room STT (not simultaneous).
 * Hang-up deterrent + free-tier included. Not a cellular tap.
 */
class ProtectCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeRingTheme {
                ProtectCallScreen(onClose = { finish() })
            }
        }
    }
}

private enum class ProtectPhase { COACH, ANNOUNCING, LISTENING, STOPPED }

@Composable
fun ProtectCallScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(ProtectPhase.COACH) }
    var status by remember { mutableStateOf("Ready") }
    var lastHeard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var speakerOn by remember { mutableStateOf(false) }
    var legalAck by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val announce =
        "This call is being protected by family safety software. Hang up if you are not who you claim to be."

    val riskKeys = remember {
        listOf(
            "gift card", "wire transfer", "social security", "irs", "warrant",
            "bitcoin", "crypto", "remote access", "anydesk", "teamviewer",
            "apple support", "bank freeze", "amazon refund", "medicare"
        )
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { }
        tts = engine
        onDispose {
            recognizer?.destroy()
            engine.stop()
            engine.shutdown()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            error = "Microphone permission denied."
            phase = ProtectPhase.COACH
        } else {
            startAnnounceThenListen(
                context = context,
                tts = tts,
                announce = announce,
                setPhase = { phase = it },
                setStatus = { status = it },
                setError = { error = it },
                setLastHeard = { lastHeard = it },
                riskKeys = riskKeys,
                setRecognizer = { recognizer = it },
                getRecognizer = { recognizer }
            )
        }
    }

    fun begin() {
        error = null
        if (!speakerOn || !legalAck) return
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED -> {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> startAnnounceThenListen(
                context = context,
                tts = tts,
                announce = announce,
                setPhase = { phase = it },
                setStatus = { status = it },
                setError = { error = it },
                setLastHeard = { lastHeard = it },
                riskKeys = riskKeys,
                setRecognizer = { recognizer = it },
                getRecognizer = { recognizer }
            )
        }
    }

    fun stopAll() {
        recognizer?.stopListening()
        recognizer?.cancel()
        tts?.stop()
        phase = ProtectPhase.STOPPED
        status = "Stopped"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { stopAll(); onClose() }) { Text("Close") }
            Text("PROTECT CALL", fontWeight = FontWeight.Bold, color = HelpBurgundy)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(status, color = Mute, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        when (phase) {
            ProtectPhase.COACH -> {
                Text(
                    "We play a short warning first so the other person can hear it. Then this phone listens in the room. Audio is not saved.",
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = speakerOn, onCheckedChange = { speakerOn = it })
                    Text("Speaker is on (I confirmed)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = legalAck, onCheckedChange = { legalAck = it })
                    Text("I understand others may hear a warning")
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { begin() },
                    enabled = speakerOn && legalAck,
                    colors = ButtonDefaults.buttonColors(containerColor = HelpBurgundy),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Start Protect", fontWeight = FontWeight.Bold)
                }
            }
            ProtectPhase.ANNOUNCING -> {
                CircularProgressIndicator(color = SoftGold)
                Spacer(Modifier.height(8.dp))
                Text("Playing warning… listening starts after it finishes.", color = Mute, textAlign = TextAlign.Center)
            }
            ProtectPhase.LISTENING -> {
                if (lastHeard.isNotBlank()) {
                    Text("Heard: $lastHeard", color = Mute, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { stopAll() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop")
                }
            }
            ProtectPhase.STOPPED -> {
                Button(onClick = { phase = ProtectPhase.COACH; status = "Ready" }) { Text("Start again") }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.Red, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Does not secretly tap a normal cell call. Uses this phone’s microphone in the room. Free plan includes Protect.",
            fontSize = 12.sp,
            color = Mute,
            textAlign = TextAlign.Center
        )
    }
}

private fun startAnnounceThenListen(
    context: android.content.Context,
    tts: TextToSpeech?,
    announce: String,
    setPhase: (ProtectPhase) -> Unit,
    setStatus: (String) -> Unit,
    setError: (String?) -> Unit,
    setLastHeard: (String) -> Unit,
    riskKeys: List<String>,
    setRecognizer: (SpeechRecognizer?) -> Unit,
    getRecognizer: () -> SpeechRecognizer?
) {
    if (tts == null) {
        setError("Speech engine not ready. Try again.")
        return
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        setError("Speech recognition not available on this device.")
        return
    }
    setPhase(ProtectPhase.ANNOUNCING)
    setStatus("Playing warning on Speaker…")
    setError(null)

    tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onError(utteranceId: String?) {
            setError("Could not play warning.")
            setPhase(ProtectPhase.COACH)
        }
        override fun onDone(utteranceId: String?) {
            // Main thread for SpeechRecognizer
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                beginListening(
                    context, setPhase, setStatus, setError, setLastHeard, riskKeys,
                    setRecognizer, getRecognizer, tts, announce
                )
            }
        }
    })
    val params = Bundle()
    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "protect_notice")
    tts.language = Locale.US
    tts.speak(announce, TextToSpeech.QUEUE_FLUSH, params, "protect_notice")
}

private fun beginListening(
    context: android.content.Context,
    setPhase: (ProtectPhase) -> Unit,
    setStatus: (String) -> Unit,
    setError: (String?) -> Unit,
    setLastHeard: (String) -> Unit,
    riskKeys: List<String>,
    setRecognizer: (SpeechRecognizer?) -> Unit,
    getRecognizer: () -> SpeechRecognizer?,
    tts: TextToSpeech,
    announce: String
) {
    getRecognizer()?.destroy()
    val sr = SpeechRecognizer.createSpeechRecognizer(context)
    setRecognizer(sr)
    setPhase(ProtectPhase.LISTENING)
    setStatus("Listening in the room…")

    var riskCooldownUntil = 0L

    sr.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            // restart listen window
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (getRecognizer() === sr) {
                    try {
                        sr.startListening(listenIntent())
                    } catch (_: Exception) {}
                }
            }, 400)
        }
        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try { sr.startListening(listenIntent()) } catch (_: Exception) {}
                }, 600)
            }
        }
        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val joined = texts.joinToString(" ")
            if (joined.isNotBlank()) {
                setLastHeard(joined.takeLast(180))
                val lower = joined.lowercase()
                if (riskKeys.any { lower.contains(it) } && System.currentTimeMillis() > riskCooldownUntil) {
                    riskCooldownUntil = System.currentTimeMillis() + 12_000
                    // notice again then listen — sequential
                    setPhase(ProtectPhase.ANNOUNCING)
                    setStatus("Possible scam language — warning again…")
                    sr.stopListening()
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "protect_risk")
                    tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) {
                            beginListening(context, setPhase, setStatus, setError, setLastHeard, riskKeys, setRecognizer, getRecognizer, tts, announce)
                        }
                        override fun onDone(utteranceId: String?) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                beginListening(context, setPhase, setStatus, setError, setLastHeard, riskKeys, setRecognizer, getRecognizer, tts, announce)
                            }
                        }
                    })
                    tts.speak(announce, TextToSpeech.QUEUE_FLUSH, params, "protect_risk")
                    return
                }
            }
            try { sr.startListening(listenIntent()) } catch (_: Exception) {}
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val joined = texts.joinToString(" ")
            if (joined.isNotBlank()) setLastHeard(joined.takeLast(180))
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    try {
        sr.startListening(listenIntent())
    } catch (e: Exception) {
        setError(e.message ?: "Could not start listening")
        setPhase(ProtectPhase.COACH)
    }
}

private fun listenIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }
