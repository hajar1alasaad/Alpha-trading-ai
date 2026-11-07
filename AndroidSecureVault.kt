package com.alphatrade.ai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted-at-rest vault for app-owned secrets (for example a future
 * Phantom deeplink session). It is NOT a wallet implementation and must never
 * be used to store a user's Phantom recovery phrase or primary wallet key.
 *
 * Values are encrypted with AES-256-GCM. The AES key is generated inside
 * Android Keystore and is never exported by this class. Preference values hold
 * only Base64-encoded IV/ciphertext; the record name is authenticated as AAD.
 */
class AndroidSecureVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun put(recordName: String, value: String) {
        validateRecordName(recordName)
        require(value.isNotEmpty()) { "Secret value must not be empty" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(recordName.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

        val saved = preferences.edit()
            .putString(ivKey(recordName), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(ciphertextKey(recordName), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(saved) { "Unable to persist encrypted value" }
    }

    @Synchronized
    fun get(recordName: String): String? {
        validateRecordName(recordName)
        val ivText = preferences.getString(ivKey(recordName), null) ?: return null
        val ciphertextText = preferences.getString(ciphertextKey(recordName), null) ?: return null
        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextText, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(recordName.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    @Synchronized
    fun remove(recordName: String) {
        validateRecordName(recordName)
        val saved = preferences.edit()
            .remove(ivKey(recordName))
            .remove(ciphertextKey(recordName))
            .commit()
        check(saved) { "Unable to remove encrypted value" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun validateRecordName(name: String) {
        require(name.matches(Regex("[a-zA-Z0-9_.-]{1,80}"))) { "Invalid vault record name" }
    }

    private fun ivKey(name: String) = "iv_$name"
    private fun ciphertextKey(name: String) = "ct_$name"

    private companion object {
        const val PREFS_NAME = "alpha_encrypted_vault"
        const val KEY_ALIAS = "alpha_trade_ai_vault_aes_gcm_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
