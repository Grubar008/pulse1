package com.pulse.app.data.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DbKeyProvider(private val context: Context) {

    fun passphrase(): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "pulse_db_key",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY, null)
        if (existing != null) {
            return decode(existing)
        }
        val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY, encode(fresh)).apply()
        return fresh
    }

    private fun encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun decode(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    companion object { private const val KEY = "db_passphrase" }
}
