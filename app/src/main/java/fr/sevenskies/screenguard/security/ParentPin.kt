package fr.sevenskies.screenguard.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Separate six-digit parent PIN, NOT the child's Android screen PIN.
 * Hashes with an app-private salt and PBKDF2. Failed attempts are throttled.
 * This is not resistant to a child revoking Accessibility or uninstalling
 * the app in Android Settings (Device Owner intentionally not used).
 */
class ParentPin(context: Context) {
    private val prefs = context.getSharedPreferences("screen_guard_parent", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean =
        prefs.contains("salt") && prefs.contains("hash")

    fun setNew(pin: String) {
        require(!isConfigured()) { "Parent PIN already configured" }
        require(isValid(pin)) { "Le code parent doit avoir 6 chiffres." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        check(prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failures", 0)
            .putLong("blocked_until", 0)
            .commit())
    }

    /**
     * Rotate the parent PIN and salt only after checking the previous PIN.
     * The existing retry throttle applies to rotation as well.
     */
    @Synchronized fun changePin(currentPin: String, newPin: String): Boolean {
        require(isValid(newPin)) { "Le nouveau code doit avoir 6 chiffres." }
        require(currentPin != newPin) { "Le nouveau code doit être différent." }
        if (!verify(currentPin)) return false
        val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val newHash = derive(newPin, newSalt)
        check(prefs.edit()
            .putString("salt", Base64.encodeToString(newSalt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(newHash, Base64.NO_WRAP))
            .putInt("failures", 0)
            .putLong("blocked_until", 0L)
            .commit()) { "Impossible d'enregistrer le nouveau code parent." }
        return true
    }

    fun verify(pin: String): Boolean {
        if (!isConfigured() || System.currentTimeMillis() < prefs.getLong("blocked_until", 0L)) return false
        val salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP)
        val hash = Base64.decode(prefs.getString("hash", ""), Base64.NO_WRAP)
        val success = isValid(pin) && MessageDigest.isEqual(derive(pin, salt), hash)
        val failures = if (success) 0 else prefs.getInt("failures", 0) + 1
        val block = if (failures >= 5) System.currentTimeMillis() + 60_000L else 0L
        check(prefs.edit().putInt("failures", if (block > 0) 0 else failures)
            .putLong("blocked_until", block).commit())
        return success
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 160_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        fun isValid(pin: String): Boolean =
            pin.length == 6 && pin.all { it in '0'..'9' }
    }
}
