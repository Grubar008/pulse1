package com.pulse.app.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.prefs.PulseSettings
import com.pulse.app.data.repo.ScoresRepository
import com.pulse.app.core.model.Scores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import javax.inject.Inject

data class ChatMessage(val role: String, val text: String)

data class CoachState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("assistant", "Hi! Your daily metrics are shared with the AI only after you add an API key in Settings.")
    ),
    val apiKeySet: Boolean = false,
    val busy: Boolean = false
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val settings: PulseSettings,
    private val scoresRepository: ScoresRepository,
    private val db: PulseDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(CoachState())
    val state = _state

    private val ok = OkHttpClient()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            refreshKey()
        }
    }

    fun refreshKey() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(apiKeySet = settings.hasCoachKey())
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("user", text), busy = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            val summary = buildDailySummary()
            val reply = runCatching { callLlm(text, summary) }
                .getOrElse { "Could not reach the AI endpoint. Check your key and connection. ($it)" }
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage("assistant", reply),
                busy = false
            )
        }
    }

    private suspend fun buildDailySummary(): String {
        val today = scoresRepository.getDay(LocalDate.now()) ?: scoresRepository.getDay(LocalDate.now().minusDays(1))
        val bases = db.baselineDao().all().joinToString { "${it.metric}=%.1f±%.2f".format(it.mean, it.sd) }
        return "Recovery=${today?.recovery}, Strain=${today?.strain}, SleepPerf=${today?.sleepPerformance}, StressAvg=${today?.stressAvg}. Baselines: $bases"
    }

    private suspend fun callLlm(question: String, summary: String): String {
        val key = settings.coachKey()?.takeIf { it.isNotBlank() }
            ?: return "No API key set. Add one in Settings first."
        val endpoint = settings.coachEndpoint()

        val body = buildJsonObject {
            put("model", "mistral-small-latest")
            put(
                "messages",
                kotlinx.serialization.json.JsonArray(listOf(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive("You are a concise fitness coach. User metrics: $summary. Not medical advice."))
                    },
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(question))
                    }
                ))
            )
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        ok.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return "API error ${resp.code}"
            val obj = Json.decodeFromString<JsonObject>(text)
            val choices = obj["choices"] as? kotlinx.serialization.json.JsonArray ?: return "Unexpected response"
            val msg = (choices.firstOrNull()?.let { Json.decodeFromString<kotlinx.serialization.json.JsonObject>(it.toString()) }) ?: return "Unexpected response"
            val content = (msg["message"] as? JsonObject)?.get("content")?.let { (it as JsonPrimitive).content }
            return content ?: "Unexpected response"
        }
    }
}
