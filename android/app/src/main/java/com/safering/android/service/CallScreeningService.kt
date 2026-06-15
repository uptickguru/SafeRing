package com.safering.android.service

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.safering.android.data.repository.ScamRepository
import com.safering.android.data.local.AppDatabase
import com.safering.android.domain.ml.NumberClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CallScreeningService for pre-call number lookup.
 *
 * This service is called by the Android Telecom framework BEFORE a call
 * is shown to the user. It allows SafeRing to:
 * 1. Look up the caller number against the local scam database
 * 2. Check against known scam prefixes
 * 3. Block or warn about high-risk calls
 * 4. Respond to the system with screening results
 *
 * The service runs the number through the local ML classifier for
 * ultra-low-latency response (must complete within ~100ms).
 *
 * Privacy: The phone number is hashed immediately. Only the hash
 * is used for database lookups and network calls.
 */
@AndroidEntryPoint
class CallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallScreeningService"
    }

    @Inject
    lateinit var repository: ScamRepository

    @Inject
    lateinit var numberClassifier: NumberClassifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return
        if (phoneNumber.isEmpty()) return

        Log.d(TAG, "Screening call from: ${maskNumber(phoneNumber)}")

        scope.launch {
            try {
                val hash = repository.hashPhoneNumber(phoneNumber)

                // Quick check against local cache (ultra-fast path)
                val dbResult = repository.checkNumber(phoneNumber)

                // Run through on-device ML classifier for additional scoring
                val prefixRisk = if (dbResult.scamLabel != null) 0.5f else 0f
                val mlScore = numberClassifier.classify(phoneNumber, prefixRisk)

                // Combined risk score (weighted)
                val combinedRisk = maxOf(dbResult.risk, mlScore)

                Log.d(TAG, "Risk assessment for number: risk=$combinedRisk " +
                        "type=${dbResult.scamType} label=${dbResult.scamLabel}")

                // Build the screening response
                val response = CallResponse.Builder()

                if (dbResult.isBlocked || combinedRisk >= 0.85f) {
                    // High risk — block the call
                    response.setDisallowCall(true)
                    response.setRejectCall(true)
                    response.setSkipCallLog(false)
                    response.setSkipNotification(false)

                    Log.w(TAG, "BLOCKED scam call: ${dbResult.scamType ?: "Unknown type"} " +
                            "(risk=$combinedRisk)")
                } else if (dbResult.isHighRisk || combinedRisk >= 0.7f) {
                    // High risk — warn but don't block
                    response.setDisallowCall(false)
                    response.setRejectCall(false)
                    response.setSkipCallLog(false)
                    response.setSkipNotification(false)

                    Log.w(TAG, "WARNED scam call: ${dbResult.scamType ?: "Unknown type"} " +
                            "(risk=$combinedRisk)")
                } else if (dbResult.isMediumRisk) {
                    // Medium risk — let through but mark in call log
                    response.setDisallowCall(false)
                    response.setRejectCall(false)
                    response.setSkipCallLog(false)
                    response.setSkipNotification(false)
                } else {
                    // Low risk or safe — normal handling
                    response.setDisallowCall(false)
                    response.setRejectCall(false)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    response.setSilenceCall(combinedRisk >= 0.7f)
                }

                // Provide the response back to the Telecom framework
                respondToCall(callDetails, response.build())

            } catch (e: Exception) {
                Log.e(TAG, "Error screening call", e)
                // On error, allow the call through
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }

    /**
     * Mask phone number for logging — never log raw numbers.
     * Shows only first 3 and last 2 digits.
     */
    private fun maskNumber(number: String): String {
        if (number.length <= 5) return "****"
        return number.takeLast(2).let { last2 ->
            number.take(3) + "****" + last2
        }
    }
}
