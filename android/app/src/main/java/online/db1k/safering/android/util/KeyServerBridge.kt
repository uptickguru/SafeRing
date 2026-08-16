package online.db1k.safering.android.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeyServerBridge {

    private const val KEYSTORE_ALIAS = "safering-hmac-key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    suspend fun provisionKey(): HmacKey {
        Logger.warning("KeyServerBridge.provisionKey() — using dev fallback (backend not ready)", Logger.Category.SECURITY)
        val devKey = "dev-hmac-key-do-not-use-in-production".toByteArray(Charsets.UTF_8)
        return HmacKey(devKey)
    }

    suspend fun rotateKey(): HmacKey {
        Logger.warning("KeyServerBridge.rotateKey() — not yet implemented", Logger.Category.SECURITY)
        throw NotImplementedError("KeyServerBridge.rotateKey() not yet implemented — backend endpoints pending")
    }

    suspend fun checkKeyStatus(): KeyStatus {
        Logger.warning("KeyServerBridge.checkKeyStatus() — not yet implemented", Logger.Category.SECURITY)
        throw NotImplementedError("KeyServerBridge.checkKeyStatus() not yet implemented")
    }

    suspend fun loadOrProvisionKey(): HmacKey {
        val stored = loadFromKeystore(KEYSTORE_ALIAS)
        if (stored != null) {
            Logger.info("HMAC key loaded from Keystore", Logger.Category.SECURITY)
            return stored
        }
        return provisionKey()
    }

    private fun loadFromKeystore(alias: String): HmacKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(alias)) {
                val key = keyStore.getKey(alias, null) as? SecretKey
                key?.encoded?.let { bytes -> HmacKey(bytes) }
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.warning("Failed to load key from Keystore: ${e.message}", Logger.Category.SECURITY)
            null
        }
    }

    fun storeInKeystore(keyBytes: ByteArray, alias: String = KEYSTORE_ALIAS) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
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

data class KeyStatus(
    val keyId: String,
    val algorithm: String,
    val expiresAt: String,
    val status: String
)
