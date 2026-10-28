package fr.sevenskies.screenguard.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stable Android machine ID is app-scoped, NOT a hardware tracking identifier.
 * Pairing bearer token is encrypted using an Android Keystore AES-GCM key.
 * Data backups are disabled in AndroidManifest.xml.
 */
class AgentStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("screen_guard_agent", Context.MODE_PRIVATE)

    val machineId: String
        get() {
            val saved = prefs.getString("machine_id", null)
            if (saved != null) return saved
            val generated = UUID.randomUUID().toString()
            check(prefs.edit().putString("machine_id", generated).commit())
            return generated
        }

    var serverUrl: String
        get() = prefs.getString("server_url", DEFAULT_URL) ?: DEFAULT_URL
        set(value) { prefs.edit().putString("server_url", value).apply() }

    var child: String
        get() = prefs.getString("child", "") ?: ""
        set(value) {
            val name = value.trim()
            require(name.isNotBlank() && name.length <= 64 && name.none { it.isISOControl() }) {
                "Profile name must be 1–64 printable characters."
            }
            check(prefs.edit().putString("child", name).commit())
        }

    fun saveToken(token: String) {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString("token", encoded).commit())
    }

    fun loadToken(): String? {
        val saved = prefs.getString("token", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            require(bytes.size > IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES))
            )
            String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clearToken() { prefs.edit().remove("token").apply() }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_URL = ""
        private const val KEY_ALIAS = "fr.sevenskies.screenguard.agent.pairing"
        private const val IV_BYTES = 12
    }
}
