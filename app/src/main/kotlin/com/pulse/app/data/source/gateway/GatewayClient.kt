package com.pulse.app.data.source.gateway

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "pulse_tokens", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(source: String, key: String, value: String) {
        prefs.edit().putString("$source:$key", value).apply()
    }

    fun load(source: String, key: String): String? = prefs.getString("$source:$key", null)

    fun clearAll(source: String) {
        prefs.edit().clear().apply()
    }
}

object TokenCipher {

    fun encrypt(plain: String, key: SecretKey): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(blob: String, key: SecretKey): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, raw.copyOfRange(0, 12))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }
}

@Serializable
data class GarminLoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String)

@Serializable
data class GarminDaily(
    val date: String,
    val restingHeartRate: Double? = null,
    val hrvEveningRmssd: Double? = null,
    val stressAverage: Double? = null,
    val bodyBatteryMax: Int? = null,
    val steps: Int? = null
)

@Serializable
data class GarminSleepDto(
    val date: String,
    val startTs: Long,
    val endTs: Long,
    val deepMin: Int = 0,
    val lightMin: Int = 0,
    val remMin: Int = 0,
    val awakeMin: Int = 0,
    val restingHeartRate: Double? = null,
    val rmssd: Double? = null
)

@Serializable
data class ZeppSummaryDto(
    val date: String,
    val steps: Int? = null,
    val restingHeartRate: Double? = null,
    val hrvRmssd: Double? = null,
    val spo2Avg: Double? = null,
    val respRate: Double? = null,
    val sleepStartTs: Long? = null,
    val sleepEndTs: Long? = null,
    val deepMin: Int = 0,
    val lightMin: Int = 0,
    val remMin: Int = 0,
    val awakeMin: Int = 0
)
