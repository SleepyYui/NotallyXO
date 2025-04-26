package com.sleepyyui.notallyxo.utils.security

import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Service for handling end-to-end encryption for cloud synchronization.
 *
 * This service provides functionality for:
 * 1. Deriving encryption keys from a master password using PBKDF2
 * 2. Encrypting and decrypting note content for secure cloud storage
 * 3. Managing asymmetric keys for secure note sharing
 */
class CloudEncryptionService {

    companion object {
        // Key derivation parameters
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_SIZE_BITS = 256

        // Encryption parameters
        private const val CIPHER_TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"

        // Salt size for PBKDF2
        private const val SALT_SIZE_BYTES = 16

        // RSA parameters for asymmetric encryption (note sharing)
        private const val RSA_KEY_SIZE = 2048
        private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    }

    /**
     * Derives an encryption key from a user's master password using PBKDF2.
     *
     * @param masterPassword The user's master password
     * @param salt Random salt for key derivation (generate with generateSalt() for new keys)
     * @return A derived SecretKey for AES encryption
     */
    fun deriveKeyFromPassword(masterPassword: String, salt: ByteArray): SecretKey {
        val keySpec =
            PBEKeySpec(masterPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = keyFactory.generateSecret(keySpec).encoded

        return SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
    }

    /**
     * Generates a random salt for key derivation.
     *
     * @return A random salt to use with PBKDF2
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Encrypts data using AES encryption.
     *
     * @param data The data to encrypt
     * @param secretKey The encryption key
     * @return An EncryptedData object containing the encrypted data and IV
     */
    fun encrypt(data: ByteArray, secretKey: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)

        return EncryptedData(encryptedData, iv)
    }

    /**
     * Decrypts data using AES encryption.
     *
     * @param encryptedData The EncryptedData object containing encrypted data and IV
     * @param secretKey The encryption key
     * @return The decrypted data
     */
    fun decrypt(encryptedData: EncryptedData, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(encryptedData.iv))

        return cipher.doFinal(encryptedData.data)
    }

    /**
     * Generates an RSA key pair for asymmetric encryption.
     *
     * @return A KeyPair containing public and private keys
     */
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(RSA_KEY_SIZE, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encrypts a symmetric key using a recipient's public key.
     *
     * @param symmetricKey The symmetric key to encrypt
     * @param publicKey The recipient's public key
     * @return The encrypted symmetric key
     */
    fun encryptSymmetricKey(
        symmetricKey: SecretKey,
        publicKey: java.security.PublicKey,
    ): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(symmetricKey.encoded)
    }

    /**
     * Decrypts a symmetric key using the user's private key.
     *
     * @param encryptedKey The encrypted symmetric key
     * @param privateKey The user's private key
     * @return The decrypted symmetric key
     */
    fun decryptSymmetricKey(
        encryptedKey: ByteArray,
        privateKey: java.security.PrivateKey,
    ): SecretKey {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val keyBytes = cipher.doFinal(encryptedKey)
        return SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
    }

    /**
     * Generates a random symmetric key for AES encryption.
     *
     * @return A random SecretKey for AES encryption
     */
    fun generateSymmetricKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(KEY_SIZE_BITS)
        return keyGenerator.generateKey()
    }

    /**
     * Creates a user ID based on a hash of unique device information. This ID identifies the user
     * for sharing purposes.
     *
     * @return A user ID string
     */
    fun createUserId(): String {
        val deviceInfo =
            android.os.Build.DEVICE +
                android.os.Build.BRAND +
                android.os.Build.MANUFACTURER +
                UUID.randomUUID().toString()

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(deviceInfo.toByteArray())

        return Base64.encodeToString(hash, Base64.URL_SAFE).substring(0, 16)
    }

    /** Data class representing encrypted data along with its initialization vector. */
    data class EncryptedData(val data: ByteArray, val iv: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedData

            if (!data.contentEquals(other.data)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }

        /**
         * Converts the encrypted data and IV to a Base64-encoded string representation.
         *
         * @return Base64-encoded string representation of the encrypted data and IV
         */
        fun toBase64(): String {
            // Format: base64(iv):base64(data)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataBase64 = Base64.encodeToString(data, Base64.NO_WRAP)
            return "$ivBase64:$dataBase64"
        }

        companion object {
            /**
             * Creates an EncryptedData object from a Base64-encoded string.
             *
             * @param base64 Base64-encoded string representation of encrypted data
             * @return An EncryptedData object
             * @throws IllegalArgumentException if the string is not properly formatted
             */
            fun fromBase64(base64: String): EncryptedData {
                val parts = base64.split(":")
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid encrypted data format")
                }

                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val data = Base64.decode(parts[1], Base64.NO_WRAP)

                return EncryptedData(data, iv)
            }
        }
    }
}
