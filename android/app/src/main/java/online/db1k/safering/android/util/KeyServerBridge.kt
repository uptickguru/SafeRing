package online.db1k.safering.android.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Bridge to the backend key server for HMAC key provisioning.
 *
 * # Architecture
 * The HMAC-SHA256 keys used for phone number pseudonymization are
 * provisioned by the Go backend key server (HSM-backed). This bridge
 * handles provisioning, rotation, and secure storage (Android Keystore).
 *
 * # Current State
 * This is a **bridge stub**. The Go backend key server endpoints
 * (`/v1/keys/provision`, `/v1/keys/rotate`, `/v1/keys/status`) are
 * not yet implemented. Until then, a development fallback key is used.
 *
 * See: docs/KEY_SERVER_BRIDGE.md
 */
object KeyServerBridge {

    private const val KEYSTORE_ALIAS = "safering-hmac-key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Provision a new HMAC key from the backend key server.
     * Stores in Android Keystore on success.
     *
     * @throws NotImplementedError until Go backend key server is ready.
     */
    suspend fun provisionKey(): HmacKey {
        // TODO: Call POST /v1/keys/provision when Go backend is ready
        Logger.warning("KeyServerBridge.provisionKey() — using dev fallback (backend not ready)", Logger.Category.SECURITY)

        val devKey = "dev-hmac-key-do-not-use-in-production".toByteArray(Charsets.UTF_8)
        return HmacKey(provisionedKey = devKey)
    }

    /**
     * Rotate the current HMAC key.
     *
     * @throws NotImplementedError until Go backend key server is ready.
     */
    suspend fun rotateKey(): HmacKey {
        // TODO: Call POST /v1/keys/rotate when Go backend is ready
        Logger.warning("KeyServerBridge.rotateKey() — not yet implemented", Logger.Category.SECURITY)
        throw NotImplementedError("KeyServerBridge.rotateKey() not yet implemented — backend endpoints pending")
    }

    /**
     * Check key status with the backend.
     *
     * @throws NotImplementedError until Go backend key server is ready.
     */
    suspend fun checkKeyStatus(): KeyStatus {
        // TODO: Call GET /v1/keys/status when Go backend is ready
        Logger.warning("KeyServerBridge.checkKeyStatus() — not yet implemented", Logger.Category.SECURITY)
        throw NotImplementedError("KeyServerBridge.checkKeyStatus() not yet implemented")
    }

    /**
     * Load key from Keystore, provisioning if absent.
     *
     * This is the main entry point for getting a usable HMAC key.
     * It first tries Keystore, then provisions from the backend.
     */
    suspend fun loadOrProvisionKey(): HmacKey {
        // Try Keystore first
        val stored = loadFromKeystore(KEYSTORE_ALIAS)
        if (stored != null) {
            Logger.info("HMAC key loaded from Keystore", Logger.Category.SECURITY)
            return stored
        }

        // Provision from backend
        return provisionKey()
    }

    /**
     * Load HMAC key from Android Keystore.
     *
     * @param alias The Keystore alias.
     * @return HmacKey if found, null otherwise.
     */
    private fun loadFromKeystore(alias: String): HmacKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(alias)) {
                val key = keyStore.getKey(alias, null) as? SecretKey
                key?.let { HmacKey(provisionedKey = it.encoded ?: return null) }
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.warning("Failed to load key from Keystore: ${e.message}", Logger.Category.SECURITY)
            null
        }
    }

    /**
     * Store HMAC key in Android Keystore.
     *
     * @param keyBytes The raw key bytes.
     * @throws Exception if storage fails.
     */
    fun storeInKeystore(keyBytes: ByteArray, alias: String = KEYSTORE_ALIAS) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Generate a key in Keystore (preferred) or import
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                ANDROID_KEYSTORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()

            Logger.info("HMAC key stored in Keystore", Logger.Category.SECURITY)
        } catch (e: Exception) {
            Logger.error("Failed to store key in Keystore: ${e.message}", Logger.Category.SECURITY)
            throw e
        }
    }

    /**
     * Delete key from Keystore (for testing/rotation).
     */
    fun deleteKey(alias: String = KEYSTORE_ALIAS) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            Logger.warning("Failed to delete key from Keystore: ${e.message}", Logger.Category.SECURITY)
        }
    }
}

/**
 * Key status from the backend key server.
 */
data class KeyStatus(
    val keyId: String,
    val algorithm: String,
    val expiresAt: String,
    val status: String // "active", "expired", "revoked"
)
