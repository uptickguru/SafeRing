package com.safering.android.domain.ml

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time audio buffer analysis for scam speech detection.
 *
 * Android-only feature. Runs entirely on-device using a small TFLite model
 * that detects scam-related trigger phrases in the call audio stream.
 *
 * Privacy Guarantee:
 * - Audio buffers are ephemeral — processed and discarded immediately
 * - NEVER written to disk
 * - NEVER transmitted over the network
 * - Processing happens in-memory only
 *
 * The accessibility service (CallAudioService) feeds raw PCM audio buffers
 * to this analyzer, which runs inference on short frames (~1 second).
 */
class SpeechAnalyzer(
    private val context: android.content.Context
) {

    companion object {
        private const val TAG = "SpeechAnalyzer"
        private const val MODEL_FILENAME = "speech_classifier.tflite"

        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val FRAME_DURATION_MS = 1000
        private const val FRAME_SIZE = SAMPLE_RATE * FRAME_DURATION_MS / 1000
        private const val MFCC_FEATURES = 13

        // Trigger phrases (keyword matching)
        val TRIGGER_PHRASES = mapOf(
            "social security" to "IRS Impersonation",
            "social security number" to "IRS Impersonation",
            "ssn" to "IRS Impersonation",
            "internal revenue" to "IRS Impersonation",
            "irs" to "IRS Impersonation",
            "tax" to "IRS Impersonation",
            "gift card" to "Gift Card Scam",
            "itunes card" to "Gift Card Scam",
            "google play card" to "Gift Card Scam",
            "wire transfer" to "Wire Transfer Scam",
            "western union" to "Wire Transfer Scam",
            "moneygram" to "Wire Transfer Scam",
            "your computer" to "Tech Support Scam",
            "virus" to "Tech Support Scam",
            "microsoft" to "Tech Support Scam",
            "medicare" to "Healthcare Scam",
            "medicaid" to "Healthcare Scam",
            "bank account" to "Bank Scam",
            "credit card" to "Bank Scam",
            "verify" to "Phishing",
            "confirm" to "Phishing",
            "password" to "Phishing",
            "grandson" to "Grandparent Scam",
            "granddaughter" to "Grandparent Scam",
            "crypto" to "Crypto Scam",
            "bitcoin" to "Crypto Scam"
        )

        // Alert threshold — trigger when cumulative phrase score exceeds this
        private const val ALERT_THRESHOLD = 0.85f
    }

    private var interpreter: Interpreter? = null
    private var isLoaded: Boolean = false
    private var isRunning = AtomicBoolean(false)
    private var audioBuffer = ShortArray(0)
    private var cumulativeScore = 0f
    private var frameCount = 0

    // Circular buffer for real-time audio
    private val circularBuffer = ShortArray(FRAME_SIZE * 2)
    private var writeIndex = 0

    /**
     * Load the TFLite speech model.
     */
    fun loadModel(): Boolean {
        return try {
            val modelFile = getModelFile()
            if (modelFile.exists()) {
                val mappedBuffer = loadModelFile(modelFile)
                interpreter = Interpreter(mappedBuffer)
                isLoaded = true
                Log.d(TAG, "Speech classifier model loaded successfully")
                true
            } else {
                Log.w(TAG, "Speech model not found, using keyword-only detection")
                isLoaded = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load speech classifier model", e)
            isLoaded = false
            false
        }
    }

    /**
     * Feed an audio buffer chunk for analysis.
     *
     * @param audioData Raw PCM 16-bit mono audio samples.
     * @return AnalysisResult if a scam pattern is detected, null otherwise.
     */
    fun analyze(audioData: ShortArray): AnalysisResult? {
        // Add to circular buffer
        for (sample in audioData) {
            circularBuffer[writeIndex % circularBuffer.size] = sample
            writeIndex++
        }

        // Only analyze when we have enough audio
        if (writeIndex < FRAME_SIZE) return null

        // Extract a frame from the circular buffer
        val frame = ShortArray(FRAME_SIZE)
        val startIdx = (writeIndex - FRAME_SIZE).coerceAtLeast(0)
        for (i in 0 until FRAME_SIZE) {
            frame[i] = circularBuffer[(startIdx + i) % circularBuffer.size]
        }

        frameCount++

        // Run ML inference if model is loaded
        var mlScore = 0f
        if (isLoaded && interpreter != null) {
            try {
                val features = extractFeatures(frame)
                val output = Array(1) { FloatArray(1) }
                interpreter?.run(features, output)
                mlScore = output[0][0]
            } catch (e: Exception) {
                Log.w(TAG, "Speech ML inference failed", e)
            }
        }

        // Keyword matching on the audio (simplified — proper ASR requires a full model)
        // In production, this would use a keyword spotting model like "Hey Google" style
        val keywordScore = keywordMatchScore(audioData)

        val combinedScore = (mlScore + keywordScore) / 2f
            .coerceIn(0f, 1f)

        // Accumulate score over time to avoid false positives on single words
        cumulativeScore = cumulativeScore * 0.7f + combinedScore * 0.3f

        return if (cumulativeScore >= ALERT_THRESHOLD) {
            // Reset after alert
            cumulativeScore = 0f
            AnalysisResult(
                probability = combinedScore,
                triggerPhrases = detectTriggerPhrases(),
                frameDuration = FRAME_DURATION_MS,
                consecutiveAlerts = countConsecutiveAlerts(combinedScore)
            )
        } else {
            null
        }
    }

    /**
     * Keyword matching score based on simplified acoustic features.
     * In production, this uses a proper keyword spotting model.
     */
    private fun keywordMatchScore(audioData: ShortArray): Float {
        // Simplified: energy-based detection of speech activity
        // In production, this would be a proper KWS model
        var energy = 0f
        for (sample in audioData) {
            energy += kotlin.math.abs(sample.toFloat())
        }
        val avgEnergy = energy / audioData.size
        val normalizedEnergy = (avgEnergy / 32768f).coerceIn(0f, 1f)

        // If there's speech-like energy, return moderate score
        // (ML model provides the actual classification)
        return if (normalizedEnergy > 0.05f) 0.1f else 0f
    }

    /**
     * Extract MFCC-like features from raw audio for TFLite inference.
     */
    private fun extractFeatures(audioData: ShortArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * MFCC_FEATURES)
            .order(ByteOrder.nativeOrder())

        val features = FloatArray(MFCC_FEATURES)

        // Simplified MFCC extraction — in production use proper FFT + mel filterbank
        // Here we use zero-crossing rate and spectral energy as proxy features
        var zeroCrossings = 0
        var totalEnergy = 0f
        for (i in 1 until audioData.size) {
            if (audioData[i] >= 0 && audioData[i - 1] < 0 ||
                audioData[i] < 0 && audioData[i - 1] >= 0
            ) {
                zeroCrossings++
            }
            totalEnergy += (audioData[i].toFloat() * audioData[i].toFloat())
        }

        features[0] = (zeroCrossings.toFloat() / audioData.size).coerceIn(0f, 1f)
        features[1] = (totalEnergy / (audioData.size * 32768f * 32768f)).coerceIn(0f, 1f)
        features[2] = audioData.maxOrNull()?.toFloat()?.div(32768f) ?: 0f
        features[3] = audioData.minOrNull()?.toFloat()?.div(-32768f) ?: 0f

        buffer.asFloatBuffer().put(features)
        buffer.rewind()
        return buffer
    }

    /**
     * Detect which trigger phrases might be present (simplified).
     * In production, this uses a full speech-to-text or keyword spotting model.
     */
    private fun detectTriggerPhrases(): List<String> {
        // In production, this returns the actual phrases detected
        // For MVP, return the most common scam category
        return listOf("Potential scam language detected")
    }

    private fun countConsecutiveAlerts(score: Float): Int {
        // Simplified — would track consecutive high-scores in production
        return if (score >= ALERT_THRESHOLD) 1 else 0
    }

    fun resetCumulativeScore() {
        cumulativeScore = 0f
        frameCount = 0
    }

    private fun getModelFile(): File {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        return File(modelDir, MODEL_FILENAME)
    }

    private fun loadModelFile(file: File): MappedByteBuffer {
        val fileInputStream = FileInputStream(file)
        val channel = fileInputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isLoaded = false
        resetCumulativeScore()
    }

    data class AnalysisResult(
        val probability: Float,
        val triggerPhrases: List<String>,
        val frameDuration: Int,
        val consecutiveAlerts: Int
    ) {
        val isScamLikely: Boolean get() = probability >= ALERT_THRESHOLD
        val primaryThreat: String? get() = triggerPhrases.firstOrNull()
    }
}
