package online.db1k.safering.android.util

import java.security.MessageDigest

/**
 * ⚠️ DEPRECATED — Use HmacHashUtils instead.
 *
 * Raw SHA-256 is NOT suitable for phone number privacy.
 * The search space (~10^10 US numbers) makes SHA-256(number) trivially
 * reversible via precomputed rainbow tables.
 *
 * This file is kept ONLY for backward compatibility with any
 * non-phone-number hashing. For phone numbers, use HmacHashUtils.
 *
 * Will be removed in a future release.
 */
@Deprecated(
    message = "Raw SHA-256 is insecure for phone numbers. Use HmacHashUtils.hmacSHA256() instead.",
    replaceWith = ReplaceWith("HmacHashUtils.hmacSHA256(input)")
)
object HashUtils {

    private val sha256Digest: MessageDigest by lazy {
        MessageDigest.getInstance("SHA-256")
    }

    @Deprecated("Use HmacHashUtils.hmacSHA256() for phone numbers")
    fun sha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val hashBytes = sha256Digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    @Deprecated("Use HmacHashUtils for sensitive data")
    fun sha256(data: ByteArray): String {
        val hashBytes = sha256Digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
