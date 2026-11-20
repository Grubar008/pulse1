package com.pulse.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulse.app.core.model.SourceId
import com.pulse.app.data.source.gateway.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "pulse_settings")

data class SourceSettings(
    val enabled: Set<SourceId>,
    val priority: List<SourceId>,
    val backendUrlGarmin: String,
    val backendUrlZepp: String
)

data class AppSettings(
    val demoMode: Boolean,
    val notificationsEnabled: Boolean,
    val onboarded: Boolean,
    val source: SourceSettings
)

class PulseSettings(private val context: Context) {

    private val tokens by lazy { SecureTokenStore(context) }

    private object Keys {
        val COACH_ENDPOINT = stringPreferencesKey("coach_endpoint")
        val DEMO = booleanPreferencesKey("demo_mode")
        val NOTIF = booleanPreferencesKey("notifications")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val ENABLED_SOURCES = stringPreferencesKey("enabled_sources")
        val PRIORITY = stringPreferencesKey("source_priority")
        val URL_GARMIN = stringPreferencesKey("url_garmin")
        val URL_ZEPP = stringPreferencesKey("url_zepp")
    }

    private companion object {
        const val TOKEN_SOURCE = "coach"
        const val TOKEN_KEY = "api_key"
        const val DEFAULT_COACH_ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            demoMode = p[Keys.DEMO] ?: false,
            notificationsEnabled = p[Keys.NOTIF] ?: true,
            onboarded = p[Keys.ONBOARDED] ?: false,
            source = SourceSettings(
                enabled = parseIds(p[Keys.ENABLED_SOURCES] ?: "HEALTH_CONNECT"),
                priority = parseIds(p[Keys.PRIORITY] ?: "FILE_IMPORT,ZEPP,GARMIN,HEALTH_CONNECT").reversed(),
                backendUrlGarmin = p[Keys.URL_GARMIN] ?: "",
                backendUrlZepp = p[Keys.URL_ZEPP] ?: ""
            )
        )
    }

    suspend fun setDemoMode(on: Boolean) = context.dataStore.edit { it[Keys.DEMO] = on }
    suspend fun setNotifications(on: Boolean) = context.dataStore.edit { it[Keys.NOTIF] = on }
    suspend fun setOnboarded(on: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = on }
    suspend fun setEnabledSources(ids: Set<SourceId>) =
        context.dataStore.edit { it[Keys.ENABLED_SOURCES] = ids.joinToString(",") { s -> s.name } }
    suspend fun setPriority(ids: List<SourceId>) =
        context.dataStore.edit { it[Keys.PRIORITY] = ids.joinToString(",") { s -> s.name } }
    suspend fun setBackendUrls(garmin: String, zepp: String) =
        context.dataStore.edit { it[Keys.URL_GARMIN] = garmin; it[Keys.URL_ZEPP] = zepp }
    suspend fun setCoachKey(key: String) = withContext(Dispatchers.IO) {
        tokens.save(TOKEN_SOURCE, TOKEN_KEY, key)
    }

    suspend fun setCoachEndpoint(url: String) = context.dataStore.edit { it[Keys.COACH_ENDPOINT] = url }

    suspend fun coachKey(): String? = withContext(Dispatchers.IO) {
        tokens.load(TOKEN_SOURCE, TOKEN_KEY)
    }

    suspend fun coachEndpoint(): String =
        context.dataStore.data.first()[Keys.COACH_ENDPOINT]?.takeIf { it.isNotBlank() }
            ?: DEFAULT_COACH_ENDPOINT

    suspend fun hasCoachKey(): Boolean = coachKey()?.isNotBlank() ?: false

    private fun parseIds(s: String): Set<SourceId> =
        s.split(",").mapNotNull { runCatching { SourceId.valueOf(it.trim()) }.getOrNull() }.toSet()
}
