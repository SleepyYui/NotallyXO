package com.sleepyyui.notallyxo.utils.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Helper class for securely storing and retrieving cryptographic keys using Android KeyStore.
 *
 * This class uses Android's KeyStore system and EncryptedSharedPreferences for handling encryption
 * keys in a secure, production-ready manner.
 */
class AndroidKeyStoreHelper private constructor(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTED_PREFS_FILE = "secure_sync_prefs"
        private const val AES_KEY_SIZE = 256

        @Volatile private var INSTANCE: AndroidKeyStoreHelper? = null

        fun getInstance(context: Context): AndroidKeyStoreHelper {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: AndroidKeyStoreHelper(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    // Create or retrieve master key for EncryptedSharedPreferences
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    // Encrypted shared preferences for storing sensitive information
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Gets an existing secret key from the Android KeyStore or creates a new one if it doesn't
     * exist.
     *
     * @param alias The alias for the key in the KeyStore
     * @return The SecretKey for encryption/decryption
     */
    fun getOrCreateSecretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // First check if the key already exists
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            return entry?.secretKey ?: createSecretKey(alias)
        }

        // Key doesn't exist, create a new one
        return createSecretKey(alias)
    }

    /**
     * Creates a new secret key in the Android KeyStore.
     *
     * @param alias The alias for the key in the KeyStore
     * @return The newly created SecretKey
     */
    private fun createSecretKey(alias: String): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)

        val keyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .apply {
                    setKeySize(AES_KEY_SIZE)
                    setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    // The key can be used without user authentication for a smoother user
                    // experience
                    // For higher security, you could require user authentication
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setUnlockedDeviceRequired(true)
                    }
                    // Key is not exportable outside of the secure hardware
                    setIsStrongBoxBacked(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBoxAvailable()
                    )
                }
                .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Checks if StrongBox security is available on this device.
     *
     * @return true if StrongBox is available, false otherwise
     */
    private fun isStrongBoxAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
        } else {
            false
        }
    }

    /**
     * Stores sensitive string data securely using EncryptedSharedPreferences.
     *
     * @param key The preference key
     * @param value The value to store
     */
    fun storeSecureString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    /**
     * Retrieves sensitive string data from EncryptedSharedPreferences.
     *
     * @param key The preference key
     * @param defaultValue The default value to return if the key doesn't exist
     * @return The stored string value or defaultValue if not found
     */
    fun getSecureString(key: String, defaultValue: String = ""): String {
        return encryptedPrefs.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * Deletes a key from the Android KeyStore.
     *
     * @param alias The alias of the key to delete
     */
    fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
