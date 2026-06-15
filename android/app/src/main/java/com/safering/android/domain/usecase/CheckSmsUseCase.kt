package com.safering.android.domain.usecase

import com.safering.android.data.local.entity.SmsLogEntity
import com.safering.android.data.repository.ScamRepository
import com.safering.android.domain.ml.SmsClassifier
import com.safering.android.domain.model.SmsRisk
import javax.inject.Inject

/**
 * Use case for classifying an incoming SMS message.
 *
 * Steps:
 * 1. Classify message body using on-device TFLite (or keyword fallback)
 * 2. Hash the sender number for privacy
 * 3. Log the result to sms_logs table
 * 4. Return SmsRisk domain model
 *
 * Privacy: Message body is NEVER transmitted. Classification is on-device only.
 */
class CheckSmsUseCase @Inject constructor(
    private val repository: ScamRepository,
    private val smsClassifier: SmsClassifier
) {

    /**
     * Classify an incoming SMS message.
     *
     * @param senderNumber Raw sender phone number.
     * @param messageBody SMS message body text.
     * @return SmsRisk classification result.
     */
    suspend operator fun invoke(
        senderNumber: String,
        messageBody: String
    ): SmsRisk {
        val senderHash = repository.hashPhoneNumber(senderNumber)

        // Run classification
        val result = smsClassifier.classify(messageBody)

        // Log the SMS
        repository.logSms(
            SmsLogEntity(
                senderHash = senderHash,
                messageBody = messageBody,
                classification = result.label,
                scamType = result.scamType,
                confidence = result.confidence,
                timestamp = System.currentTimeMillis()
            )
        )

        return SmsRisk(
            label = result.label,
            scamType = result.scamType,
            confidence = result.confidence,
            matchedKeywords = result.matchedKeywords
        )
    }
}
