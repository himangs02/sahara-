package com.yourteam.sahara.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the backend JWT in an AES-256-GCM encrypted SharedPreferences file, keyed by an
 * Android Keystore master key -- never plaintext SharedPreferences, per Stage 3 requirements. */
class SecureTokenStore(context: Context) : TokenStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val FILE_NAME = "sahara_backend_session"
        const val KEY_TOKEN = "access_token"
    }
}
