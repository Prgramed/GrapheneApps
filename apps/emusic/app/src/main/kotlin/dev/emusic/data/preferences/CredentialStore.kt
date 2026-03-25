package dev.emusic.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs() }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    fun clear() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            buildEncryptedPrefs()
        } catch (_: Exception) {
            // Keystore can be invalidated on reinstall — delete and recreate
            context.deleteSharedPreferences(PREFS_NAME)
            buildEncryptedPrefs()
        }
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val PREFS_NAME = "emusic_credentials"
        private const val KEY_PASSWORD = "password"
    }
}
