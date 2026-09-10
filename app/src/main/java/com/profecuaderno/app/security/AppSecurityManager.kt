package com.profecuaderno.app.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object AppSecurityManager {
    private const val PREFS = "security"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_BIOMETRIC = "biometric_enabled"

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        require(pin.matches(Regex("""\d{4,8}""")))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val saltText = prefs(context).getString(KEY_SALT, null) ?: return false
        val hashText = prefs(context).getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, hash(pin, salt))
    }

    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .putBoolean(KEY_BIOMETRIC, false)
            .apply()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC, false) && hasPin(context)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC, enabled && hasPin(context)).apply()
    }

    fun isLockEnabled(context: Context): Boolean = hasPin(context)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }
}
