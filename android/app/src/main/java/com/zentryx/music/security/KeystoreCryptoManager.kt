package com.zentryx.music.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AES-256-GCM encryption and decryption backed by Android KeyStore,
 * with an automatic device-bound AES-256-GCM software fallback for third-party
 * Chinese headunit ROMs whose AndroidKeyStore daemon is broken or restricted.
 */
@Singleton
class KeystoreCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "zentryx_nextcloud_auth_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$ENCRYPTION_PADDING"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PREFIX_KEYSTORE = "KS:"
        private const val PREFIX_SOFTWARE = "SW:"
    }

    private fun getKeyStore(): KeyStore? {
        return try {
            KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey {
        val ks = getKeyStore() ?: throw IllegalStateException("AndroidKeyStore unavailable")
        val existingKey = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
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

    @SuppressLint("HardwareIds")
    private fun getDeviceFallbackSecretKey(): SecretKey {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "zentryx-headunit"
        } catch (_: Throwable) {
            "zentryx-headunit"
        }
        val pkg = try {
            context.packageName
        } catch (_: Throwable) {
            "com.zentryx.music"
        }
        val material = "$androidId:$pkg:zentryx_aes_v1".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return SecretKeySpec(digest, ALGORITHM)
    }

    /**
     * Encrypts plaintext string into a prefixed Base64 string containing both IV and ciphertext.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            PREFIX_KEYSTORE + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            // Safe software AES-256-GCM fallback on headunits with buggy TEE/Keymaster
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getDeviceFallbackSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            PREFIX_SOFTWARE + Base64.encodeToString(combined, Base64.NO_WRAP)
        }
    }

    /**
     * Decrypts Base64-encoded combined IV + ciphertext back into plaintext string.
     * Supports both prefixed (KS: / SW:) and legacy unprefixed KeyStore ciphertexts.
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        val isSoftware = encryptedText.startsWith(PREFIX_SOFTWARE)
        val payload = when {
            encryptedText.startsWith(PREFIX_KEYSTORE) -> encryptedText.removePrefix(PREFIX_KEYSTORE)
            encryptedText.startsWith(PREFIX_SOFTWARE) -> encryptedText.removePrefix(PREFIX_SOFTWARE)
            else -> encryptedText
        }

        val combined = Base64.decode(payload, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_LENGTH) return ""

        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val key = if (isSoftware) getDeviceFallbackSecretKey() else getOrCreateKeystoreSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
