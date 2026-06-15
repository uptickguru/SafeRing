package com.safering.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.safering.android.domain.ml.SpeechAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Accessibility Service for mid-call scam speech detection.
 *
 * This service listens to the phone's audio stream during active calls
 * and runs on-device speech analysis to detect scam-related language.
 *
 * Privacy Guarantee:
 * - Audio is processed in ephemeral buffers — NEVER written to disk
 * - NEVER transmitted over the network
 * - Buffers are discarded immediately after analysis
 * - User is notified of potential scams via overlay/notification
 *
 * This feature is Android-only and opt-in via settings.
 */
@AndroidEntryPoint
class CallAudioService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAudioService"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 4096
    }

    @Inject
    lateinit var speechAnalyzer: SpeechAnalyzer

    private var audioRecord: AudioRecord? = null
    private var isAnalyzing = false
    private var isInCall = false
    private var analysisThread: Thread? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

            // Only interested in the Phone app
            packageNames = arrayOf(
                "com.android.dialer",
                "com.google.android.dialer",
                "com.android.incallui",
                "com.google.android.apps.messaging"
            )

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100 // ms

            // Don't take focus away from other accessibility services
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        serviceInfo = info

        // Initialize the speech analyzer
        speechAnalyzer.loadModel()
        Log.d(TAG, "CallAudioService connected and speech analyzer initialized")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return

                // Detect when phone app is in foreground (call active)
                if (packageName.contains("dialer") || packageName.contains("incallui")) {
                    if (!isInCall) {
                        Log.d(TAG, "Call detected, starting audio analysis")
                        isInCall = true
                        startAudioAnalysis()
                    }
                } else {
                    if (isInCall) {
                        Log.d(TAG, "Call ended, stopping audio analysis")
                        isInCall = false
                        stopAudioAnalysis()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        stopAudioAnalysis()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioAnalysis()
        speechAnalyzer.close()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAudioAnalysis()
        speechAnalyzer.close()
        return super.onUnbind(intent)
    }

    private fun startAudioAnalysis() {
        if (isAnalyzing) return

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(BUFFER_SIZE)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isAnalyzing = true

            analysisThread = Thread({
                analyzeAudioLoop(bufferSize)
            }, "SpeechAnalysisThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.d(TAG, "Audio analysis started")
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio analysis", e)
        }
    }

    private fun analyzeAudioLoop(bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)

        while (isAnalyzing && isInCall) {
            try {
                val audioRecord = audioRecord ?: break
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    val audioData = if (bytesRead < buffer.size) {
                        buffer.copyOf(bytesRead)
                    } else {
                        buffer
                    }

                    val result = speechAnalyzer.analyze(audioData)

                    if (result != null && result.isScamLikely) {
                        Log.w(TAG, "Possible scam language detected: " +
                                "probability=${result.probability} " +
                                "phrases=${result.triggerPhrases}")

                        // Trigger an overlay notification
                        showScamAlert(result)
                    }
                }

                // Small delay to prevent CPU saturation
                Thread.sleep(50)
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio analysis loop", e)
                break
            }
        }
    }

    private fun stopAudioAnalysis() {
        isAnalyzing = false

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }

        audioRecord = null
        analysisThread = null
        speechAnalyzer.resetCumulativeScore()
        Log.d(TAG, "Audio analysis stopped")
    }

    /**
     * Show a high-priority notification when scam language is detected.
     */
    private fun showScamAlert(result: SpeechAnalyzer.AnalysisResult) {
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        val notificationChannelId = "scam_alert"

        // Create notification channel (safe to call multiple times)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                notificationChannelId,
                "Scam Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected scam language during calls"
                enableVibration(true)
                setSound(null, null) // Silent notification with vibration
            }
            notificationManager.createNotificationChannel(channel)
        }

        val primaryThreat = result.primaryThreat ?: "Potential scam detected"

        val notification = android.app.Notification.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Scam Language Detected")
            .setContentText(primaryThreat)
            .setStyle(android.app.Notification.BigTextStyle()
                .bigText("SafeRing detected potential scam language during your call: $primaryThreat"))
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
