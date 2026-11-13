package com.zentryx.music.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AES-256-GCM encryption and decryption backed by Android KeyStore.
 * Securely protects sensitive Nextcloud passwords and app tokens on device.
 * Replaces the deprecated and unstable EncryptedSharedPreferences.
 */
@Singleton
class KeystoreCryptoManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "zentryx_nextcloud_auth_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$ENCRYPTION_PADDING"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE_PROVIDER)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(ENCRYPTION_PADDING)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string into a Base64-encoded string containing both IV and ciphertext.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Prepend 12-byte IV to ciphertext
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64-encoded combined IV + ciphertext back into plaintext string.
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH) return ""

        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
