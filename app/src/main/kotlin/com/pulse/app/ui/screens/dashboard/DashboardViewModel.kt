package com.pulse.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.prefs.PulseSettings
import com.pulse.app.data.repo.ScoresRepository
import com.pulse.app.data.source.health.HealthConnectSource
import com.pulse.app.work.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val calibrating: Boolean = false,
    val recovery: Int? = null,
    val strain: Double? = null,
    val sleepPerformance: Int? = null,
    val stressAvg: Double? = null,
    val steps: Double? = null,
    val sleepAsleepMin: Double? = null,
    val sleepNeedMin: Double? = null,
    val trend7: List<Pair<Long, Int>> = emptyList(),
    val hcNotInstalled: Boolean = false,
    val hcOutdated: Boolean = false,
    val demoMode: Boolean = false,
    val insight: String = ""
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val db: PulseDatabase,
    private val scoresRepository: ScoresRepository,
    private val syncCoordinator: SyncCoordinator,
    private val settings: PulseSettings,
    private val healthSource: HealthConnectSource
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        refresh()
        triggerSync()
    }

    fun triggerSync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncCoordinator.sync(listOf(healthSource))
            } catch (_: Exception) { }
            load()
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val cfg = settings.settings.first()
        val today = LocalDate.now()
        val baselineCount = db.baselineDao().all().size
        val scores = scoresRepository.getDay(today) ?: scoresRepository.getDay(today.minusDays(1))

        val trend = scoresRepository.recent(7)
            .mapNotNull { e -> e.recovery?.let { LocalDate.parse(e.date).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).epochSecond to it } }

        val jsonInputs = scores?.inputs?.let {
            runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, Double>>(it) }.getOrDefault(emptyMap())
        } ?: emptyMap()

        val status = runCatching { healthSource.availabilityStatus() }.getOrDefault(-1)

        _state.value = DashboardUiState(
            loading = false,
            calibrating = baselineCount < com.pulse.app.core.algorithm.AlgorithmsConfig.BASELINE_MIN_DAYS,
            recovery = scores?.recovery,
            strain = scores?.strain,
            sleepPerformance = scores?.sleepPerformance,
            stressAvg = scores?.stressAvg,
            steps = jsonInputs["steps"],
            sleepAsleepMin = jsonInputs["asleepMin"],
            sleepNeedMin = jsonInputs["needMin"],
            trend7 = trend,
            hcNotInstalled = status == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE,
            hcOutdated = status == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            demoMode = cfg.demoMode,
            insight = buildInsight(scores?.recovery, scores?.strain, jsonInputs["steps"])
        )
    }

    private fun buildInsight(recovery: Int?, strain: Double?, steps: Double?): String {
        if (recovery == null) return "Connect a data source or enable demo mode to see scores."
        return when {
            recovery <= 33 -> "Low recovery — keep today light and hydrate."
            recovery <= 66 -> "Moderate recovery — a steady training day works."
            else -> if ((strain ?: 0.0) > 14) "High recovery but a heavy day — recover well tonight."
                    else "Green recovery — good day to push."
        }
    }
}
