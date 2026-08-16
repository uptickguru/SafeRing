package online.db1k.safering.android.util

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 hashing utility for phone number privacy.
 *
 * # Security
 * Phone numbers are hashed with HMAC-SHA256 using a per-install secret key.
 * This key is provisioned by the backend at enrollment and stored securely:
 * - iOS: Keychain
 * - Android: Keystore
 *
 * # Threat Model
 * Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
 * makes it trivially reversible. HMAC-SHA256 with a secret key provides
 * pseudonymization, making it computationally infeasible to recover the
 * original number from the hash.
 *
 * # Long-Term Path (TODO)
 * This approach uses a server-provisioned key, meaning the server can still
 * correlate phone numbers with their hashes. The preferred long-term solution
 * is iOS Live Caller ID Lookup over oblivious HTTP (see M2), where the number
 * never leaves the device in a correlatable form.
 *
 * # Usage
 * ```kotlin
 * // Key provisioned at enrollment by backend
 * val hmacKey = HmacKey(provisionedKey = "your-provisioned-key".toByteArray())
 * val hash = hmacKey.hash("+15551234567")
 * // hash = "a3b5c7d9..." (hex-encoded HMAC-SHA256)
 * ```
 */
object HmacHashUtils {

    private val hmacSHA256Digest: MessageDigest by lazy {
        MessageDigest.getInstance("HmacSHA256")
    }

    /**
     * Computes the HMAC-SHA256 hash of a phone number string.
     *
     * Input should be a normalized E.164 phone number
     * (e.g., "+15551234567") before hashing.
     */
    fun hmacSHA256(input: String, key: ByteArray): String {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(key, "HmacSHA256"))
        val hashBytes = hmac.doFinal(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the HMAC-SHA256 hash of a byte array.
     */
    fun hmacSHA256(data: ByteArray, key: ByteArray): String {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(key, "HmacSHA256"))
        val hashBytes = hmac.doFinal(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that a string matches a known HMAC hash.
     */
    fun verify(input: String, hash: String, key: ByteArray): Boolean {
        return hmacSHA256(input, key) == hash
    }
}

/**
 * Represents an HMAC key for phone number hashing.
 * Keys are provisioned by the backend at enrollment and stored in Keystore.
 */
class HmacKey(private val keyBytes: ByteArray) {

    constructor(provisionedKey: String) : this(provisionedKey.toByteArray(Charsets.UTF_8))

    /**
     * Hash a phone number using this key.
     */
    fun hash(input: String): String {
        return HmacHashUtils.hmacSHA256(input, keyBytes)
    }

    /**
     * Hash a phone number using this key.
     */
    fun hash(data: ByteArray): String {
        return HmacHashUtils.hmacSHA256(data, keyBytes)
    }
}