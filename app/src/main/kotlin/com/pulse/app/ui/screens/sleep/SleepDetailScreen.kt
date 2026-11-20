package com.pulse.app.ui.screens.sleep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pulse.app.core.algorithm.SleepCalculator
import com.pulse.app.core.model.SleepSession
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.repo.ScoresRepository
import com.pulse.app.ui.components.Hypnogram
import com.pulse.app.ui.components.RowBar
import com.pulse.app.ui.theme.PulseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class SleepDetailState(
    val performance: Int? = null,
    val efficiency: Int? = null,
    val consistency: Int? = null,
    val asleepH: Double? = null,
    val needH: Double? = null,
    val stageMinutes: Map<String, Long> = emptyMap(),
    val hypnogram: List<List<Any>> = emptyList(),
    val planner: Map<Int, String> = emptyMap(),
    val naps: List<String> = emptyList()
)

@HiltViewModel
class SleepDetailViewModel @Inject constructor(
    private val db: PulseDatabase,
    private val scoresRepository: ScoresRepository
) : ViewModel() {

    val state = MutableStateFlow(SleepDetailState())
    private val calc = SleepCalculator()

    init {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val e = scoresRepository.getDay(today) ?: scoresRepository.getDay(today.minusDays(1))
        val from = today.minusDays(2).atStartOfDay(zone).toInstant().epochSecond
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        val sessions = db.sleepDao().sessionsInRange(from, to)
        val stages = db.sleepDao().stagesInRange(from, to)

        val domainSessions = sessions.map { s ->
            SleepSession(
                Instant.ofEpochSecond(s.startTs), Instant.ofEpochSecond(s.endTs),
                stages.filter { it.startTs >= s.startTs && it.endTs <= s.endTs }
                    .map { com.pulse.app.core.model.SleepStage(it.type, Instant.ofEpochSecond(it.startTs), Instant.ofEpochSecond(it.endTs)) },
                com.pulse.app.core.model.SourceId.HEALTH_CONNECT
            )
        }
        val metrics = calc.computeMetrics(domainSessions)

        val hypno = metrics.mainSleep?.let { m ->
            domainSessions.firstOrNull { it.start == m.start }?.stages
                ?.map { listOf<Any>(it.start.epochSecond, it.end.epochSecond, it.type.name) }
                ?: emptyList()
        } ?: emptyList()

        val profile = db.profileDao().get()
        val strainYesterday = scoresRepository.getDay(today.minusDays(1))?.strain ?: 0.0
        val need = calc.sleepNeed(
            (profile?.sleepNeedBaseMin ?: 480).toDouble(),
            strainYesterday,
            emptyList(),
            metrics.naps.sumOf { it.asleepMin }.toDouble()
        )

        val wake = metrics.wakeTime?.atZone(zone)
        val planner = if (wake != null) {
            val wakeMin = wake.hour * 60 + wake.minute
            calc.planner(need.needMin, wakeMin.toDouble()).mapValues { (_, bed) ->
                "%02d:%02d".format((bed.toInt() / 60) % 24, bed.toInt() % 60)
            }
        } else emptyMap()

        state.value = SleepDetailState(
            performance = e?.sleepPerformance,
            efficiency = e?.sleepEfficiency,
            consistency = null,
            asleepH = metrics.asleepMin / 60.0,
            needH = need.needMin / 60.0,
            stageMinutes = mapOf(
                "Deep" to metrics.deepMin, "REM" to metrics.remMin,
                "Light" to metrics.lightMin, "Awake" to metrics.awakeMin
            ),
            hypnogram = hypno,
            planner = planner,
            naps = metrics.naps.map { "Nap: %d min".format(it.durationMin) }
        )
    }
}

@Composable
fun SleepDetailScreen(onBack: () -> Unit, vm: SleepDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Sleep", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (state.performance == null) {
                            Text("Calibrating", color = PulseColors.Yellow)
                        } else {
                            Text("${state.performance}", style = MaterialTheme.typography.displayMedium, color = PulseColors.Purple)
                            Text(
                                "%.1f h asleep vs %.1f h needed".format(state.asleepH ?: 0.0, state.needH ?: 0.0),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        state.efficiency?.let { Text("Efficiency: $it%", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Hypnogram", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Hypnogram(stages = state.hypnogram)
                        Spacer(Modifier.height(8.dp))
                        state.stageMinutes.forEach { (n, m) ->
                            RowBar(n, m.toDouble(), state.stageMinutes.values.max().toDouble().coerceAtLeast(1.0), PulseColors.Purple)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sleep planner", style = MaterialTheme.typography.titleMedium)
                        state.planner.forEach { (target, bed) ->
                            Text("$target% performance → bedtime $bed", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.planner.isEmpty()) Text("Needs a few nights of data.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.naps.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Naps", style = MaterialTheme.typography.titleMedium)
                            state.naps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}
