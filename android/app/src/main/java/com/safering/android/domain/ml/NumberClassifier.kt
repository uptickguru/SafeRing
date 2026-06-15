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
 * On-device phone number classifier using TFLite.
 *
 * Classifies a phone number's scam risk based on:
 * - The SHA-256 hash pattern
 * - Known prefix associations
 * - Aggregated report metadata
 *
 * The model is a small neural network (LightGBM or MobileNet-style)
 * trained on aggregated scam data. Model weights are downloaded from
 * the server and cached locally.
 *
 * Zero PII guarantee: Inference runs entirely on-device.
 * The raw number is processed only within this class and never leaves.
 */
class NumberClassifier(private val context: Context) {

    companion object {
        private const val TAG = "NumberClassifier"
        private const val MODEL_FILENAME = "number_classifier.tflite"
        private const val INPUT_SIZE = 64 // feature vector dimension
        private const val OUTPUT_SIZE = 1 // risk score 0.0-1.0

        // Data normalization constants
        private const val MAX_PREFIX_LENGTH = 10f
        private const val MAX_REPORT_COUNT = 10000f
    }

    private var interpreter: Interpreter? = null
    private var isLoaded: Boolean = false

    /**
     * Load the TFLite model from the app's model directory.
     * If the model file doesn't exist, falls back to rule-based classification.
     */
    fun loadModel(): Boolean {
        return try {
            val modelFile = getModelFile()
            if (modelFile.exists()) {
                val mappedBuffer = loadModelFile(modelFile)
                interpreter = Interpreter(mappedBuffer)
                isLoaded = true
                Log.d(TAG, "Number classifier model loaded successfully")
                true
            } else {
                Log.w(TAG, "Model file not found at ${modelFile.absolutePath}, using rule-based fallback")
                isLoaded = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load number classifier model", e)
            isLoaded = false
            false
        }
    }

    /**
     * Classify a phone number's scam risk.
     *
     * @param phoneNumber Raw phone number in E.164 format.
     * @param knownPrefixRisk Risk weight from prefix match (0.0-1.0).
     * @return Risk score between 0.0 (safe) and 1.0 (definitely scam).
     */
    fun classify(phoneNumber: String, knownPrefixRisk: Float = 0f): Float {
        if (isLoaded && interpreter != null) {
            return try {
                val inputFeatures = extractFeatures(phoneNumber, knownPrefixRisk)
                val outputBuffer = ByteBuffer.allocateDirect(4 * OUTPUT_SIZE)
                    .order(ByteOrder.nativeOrder())

                interpreter?.run(inputFeatures, outputBuffer)

                val risk = outputBuffer.getFloat(0)
                risk.coerceIn(0f, 1f)
            } catch (e: Exception) {
                Log.e(TAG, "TFLite inference failed, falling back to rule-based", e)
                ruleBasedClassification(phoneNumber, knownPrefixRisk)
            }
        } else {
            return ruleBasedClassification(phoneNumber, knownPrefixRisk)
        }
    }

    /**
     * Rule-based fallback classification when ML model is unavailable.
     * Uses heuristics based on number characteristics.
     */
    private fun ruleBasedClassification(phoneNumber: String, prefixRisk: Float): Float {
        var risk = prefixRisk

        // Heuristic: Very short numbers (not valid phone numbers)
        if (phoneNumber.length < 10) {
            risk = (risk + 0.3f).coerceAtMost(1f)
        }

        // Heuristic: Numbers with repeated digits (common in spoofing)
        if (hasRepeatedPattern(phoneNumber)) {
            risk = (risk + 0.2f).coerceAtMost(1f)
        }

        // Heuristic: Toll-free numbers used for scams
        if (phoneNumber.startsWith("1800") || phoneNumber.startsWith("1888") ||
            phoneNumber.startsWith("1877") || phoneNumber.startsWith("1866") ||
            phoneNumber.startsWith("1855") || phoneNumber.startsWith("1844")
        ) {
            risk = (risk + 0.15f).coerceAtMost(1f)
        }

        // Heuristic: International numbers not in contacts (common robocall source)
        if (phoneNumber.startsWith("011") || phoneNumber.startsWith("+")) {
            risk = (risk + 0.1f).coerceAtMost(1f)
        }

        return risk
    }

    /**
     * Extract feature vector for TFLite inference.
     * Converts phone number characteristics to a fixed-size float array.
     */
    private fun extractFeatures(phoneNumber: String, prefixRisk: Float): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
            .order(ByteOrder.nativeOrder())

        val features = FloatArray(INPUT_SIZE)

        // Feature 0: Prefix risk weight
        features[0] = prefixRisk

        // Feature 1: Normalized phone number length
        features[1] = (phoneNumber.length / MAX_PREFIX_LENGTH).coerceIn(0f, 1f)

        // Feature 2-11: First 10 digits as normalized values (0-9 -> 0.0-1.0)
        for (i in 0 until minOf(10, phoneNumber.length)) {
            val digit = phoneNumber[i].digitToIntOrNull() ?: 0
            features[2 + i] = digit / 9f
        }

        // Feature 12: Contains repeated pattern
        features[12] = if (hasRepeatedPattern(phoneNumber)) 1f else 0f

        // Feature 13: Is toll-free
        features[13] = if (phoneNumber.length >= 4 && (
                phoneNumber.startsWith("1800") || phoneNumber.startsWith("1888") ||
                        phoneNumber.startsWith("1877") || phoneNumber.startsWith("1866") ||
                        phoneNumber.startsWith("1855") || phoneNumber.startsWith("1844")
                )) 1f else 0f

        // Remaining features: zero-padded (reserved for future use)
        buffer.asFloatBuffer().put(features)
        buffer.rewind()
        return buffer
    }

    private fun hasRepeatedPattern(phoneNumber: String): Boolean {
        // Check for 4+ consecutive same digits
        return phoneNumber.contains(Regex("(\\d)\\1{3,}"))
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

    /**
     * Release the TFLite interpreter resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isLoaded = false
    }
}
