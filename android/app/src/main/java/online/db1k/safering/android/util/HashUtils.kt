package online.db1k.safering.android.util

import java.security.MessageDigest

/**
 * SHA-256 hashing utility for phone number privacy.
 *
 * # Zero PII Policy
 * Phone numbers are hashed with SHA-256 before any network call.
 * The hash is one-way — the original number cannot be recovered.
 * Mirrors the iOS HashUtils.swift exactly.
 */
object HashUtils {

    private val sha256Digest: MessageDigest by lazy {
        MessageDigest.getInstance("SHA-256")
    }

    /**
     * Computes the SHA-256 hash of a phone number string.
     *
     * Input should be a normalized E.164 phone number
     * (e.g., "+15551234567") before hashing.
     */
    fun sha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val hashBytes = sha256Digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 hash of a byte array.
     */
    fun sha256(data: ByteArray): String {
        val hashBytes = sha256Digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that a string matches a known hash.
     */
    fun verify(input: String, hash: String): Boolean {
        return sha256(input) == hash
    }
}
