package com.safering.android.domain.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device SMS text classifier using TFLite.
 *
 * Classifies SMS message body text as: LEGITIMATE, SPAM, or SCAM (with type).
 * Uses a quantized DistilBERT or MobileBERT model converted to TFLite.
 *
 * Privacy: ALL classification runs on-device. The SMS body is NEVER
 * transmitted over the network. It is stored locally only for user review
 * and auto-deleted after 7 days.
 *
 * Known scam keyword detection is used as a fast-path fallback when
 * the ML model is not yet downloaded.
 */
class SmsClassifier(private val context: Context) {

    companion object {
        private const val TAG = "SmsClassifier"
        private const val MODEL_FILENAME = "sms_classifier.tflite"
        private const val VOCAB_FILENAME = "sms_vocab.txt"
        private const val MAX_SEQUENCE_LENGTH = 128
        private const val INPUT_SIZE = MAX_SEQUENCE_LENGTH
        private const val OUTPUT_CLASSES = 6 // LEGITIMATE, SPAM, SCAM_IRS, SCAM_TECH, SCAM_PHISHING, SCAM_ROMANCE

        // Scam trigger keywords (fast-path fallback)
        private val SCAM_KEYWORDS = setOf(
            "social security", "ssn", "social security number",
            "irs", "internal revenue service", "tax refund", "tax debt",
            "gift card", "itunes card", "google play card",
            "wire transfer", "western union", "moneygram",
            "your computer", "virus detected", "tech support", "microsoft certified",
            "medicare", "medicaid", "health insurance",
            "you won", "lottery", "prize", "inheritance",
            "uncle sam", "government grant",
            "bank account suspended", "account closed",
            "verify your identity", "confirm your details",
            "urgent", "action required", "immediate response",
            "grandson", "granddaughter", "family emergency",
            "romance", "dating", "love", "single",
            "cryptocurrency", "bitcoin", "investment opportunity",
            "work from home", "make money fast", "passive income"
        )

        private val SCAM_TYPE_MAP = mapOf(
            "social security" to "IRS Impersonation",
            "ssn" to "IRS Impersonation",
            "social security number" to "IRS Impersonation",
            "irs" to "IRS Impersonation",
            "internal revenue service" to "IRS Impersonation",
            "gift card" to "Gift Card Scam",
            "itunes card" to "Gift Card Scam",
            "google play card" to "Gift Card Scam",
            "wire transfer" to "Wire Transfer Scam",
            "your computer" to "Tech Support Scam",
            "virus detected" to "Tech Support Scam",
            "tech support" to "Tech Support Scam",
            "medicare" to "Healthcare Scam",
            "medicaid" to "Healthcare Scam",
            "grandson" to "Grandparent Scam",
            "granddaughter" to "Grandparent Scam",
            "romance" to "Romance Scam",
            "dating" to "Romance Scam",
            "cryptocurrency" to "Crypto Scam",
            "bitcoin" to "Crypto Scam",
            "investment opportunity" to "Investment Scam"
        )
    }

    private var interpreter: Interpreter? = null
    private var isLoaded: Boolean = false
    private val vocab: MutableMap<String, Int> = mutableMapOf()

    /**
     * Load the TFLite model and vocabulary.
     */
    fun loadModel(): Boolean {
        return try {
            val modelFile = getModelFile()
            if (modelFile.exists()) {
                val mappedBuffer = loadModelFile(modelFile)
                interpreter = Interpreter(mappedBuffer)
                loadVocabulary()
                isLoaded = true
                Log.d(TAG, "SMS classifier model loaded successfully")
                true
            } else {
                Log.w(TAG, "SMS model file not found, using keyword-based fallback")
                isLoaded = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SMS classifier model", e)
            isLoaded = false
            false
        }
    }

    /**
     * Classify an SMS message body.
     *
     * @param messageBody The raw SMS text.
     * @return ClassificationResult with label, scam type, and confidence.
     */
    fun classify(messageBody: String): ClassificationResult {
        if (isLoaded && interpreter != null) {
            return try {
                val input = tokenize(messageBody)
                val output = Array(1) { FloatArray(OUTPUT_CLASSES) }
                interpreter?.run(input, output)

                val scores = output[0]
                val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
                val confidence = scores[maxIdx]

                ClassificationResult(
                    label = LABELS[maxIdx],
                    scamType = if (maxIdx >= 2) SCAM_TYPE_LABELS[maxIdx - 2] else null,
                    confidence = confidence
                )
            } catch (e: Exception) {
                Log.e(TAG, "TFLite SMS inference failed, falling back to keyword", e)
                keywordClassification(messageBody)
            }
        } else {
            return keywordClassification(messageBody)
        }
    }

    /**
     * Fast keyword-based classification fallback.
     */
    private fun keywordClassification(messageBody: String): ClassificationResult {
        val lowerBody = messageBody.lowercase()
        val matchedKeywords = SCAM_KEYWORDS.filter { lowerBody.contains(it) }
        val detectedTypes = matchedKeywords.mapNotNull { SCAM_TYPE_MAP[it] }.distinct()

        if (matchedKeywords.isNotEmpty()) {
            val confidence = (matchedKeywords.size.toFloat() / 10f).coerceIn(0.5f, 0.95f)
            return ClassificationResult(
                label = "SCAM",
                scamType = detectedTypes.firstOrNull() ?: "Unknown Scam",
                confidence = confidence,
                matchedKeywords = matchedKeywords.take(5)
            )
        }

        // Check for generic spam indicators
        val spamIndicators = listOf(
            "free", "win", "winner", "click", "subscribe",
            "limited time", "offer", "act now", "don't miss",
            "congratulations", "selected", "exclusive"
        )
        val matchedSpam = spamIndicators.count { lowerBody.contains(it) }

        return if (matchedSpam >= 3) {
            ClassificationResult(
                label = "SPAM",
                confidence = (matchedSpam.toFloat() / 10f).coerceIn(0.5f, 0.8f)
            )
        } else {
            ClassificationResult(
                label = "LEGITIMATE",
                confidence = 0.9f
            )
        }
    }

    /**
     * Tokenize message text for ML model input.
     * Simplified tokenization — in production, use the model's tokenizer.
     */
    private fun tokenize(text: String): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
            .order(ByteOrder.nativeOrder())

        val tokens = IntArray(INPUT_SIZE) { 0 } // zero = [PAD]
        val words = text.lowercase().split(Regex("\\s+"))

        // [CLS] token
        tokens[0] = 101

        var pos = 1
        for (word in words) {
            if (pos >= MAX_SEQUENCE_LENGTH - 1) break
            val tokenId = vocab[word] ?: 100 // 100 = [UNK]
            tokens[pos++] = tokenId
        }
        // [SEP] token
        tokens[pos] = 102

        buffer.asIntBuffer().put(tokens)
        buffer.rewind()
        return buffer
    }

    private fun loadVocabulary() {
        // In production, load from assets/tflite/sms_vocab.txt
        // For MVP, use a minimal built-in vocabulary
        vocab["[PAD]"] = 0
        vocab["[CLS]"] = 101
        vocab["[SEP]"] = 102
        vocab["[UNK]"] = 100
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
    }

    data class ClassificationResult(
        val label: String, // LEGITIMATE, SPAM, or SCAM
        val scamType: String? = null,
        val confidence: Float,
        val matchedKeywords: List<String> = emptyList()
    ) {
        val isScam: Boolean get() = label == "SCAM"
        val isSpam: Boolean get() = label == "SPAM"
    }

    companion object {
        private val LABELS = arrayOf("LEGITIMATE", "SPAM", "SCAM", "SCAM", "SCAM", "SCAM")
        private val SCAM_TYPE_LABELS = arrayOf("IRS Impersonation", "Tech Support", "Phishing", "Romance Scam")
    }
}
