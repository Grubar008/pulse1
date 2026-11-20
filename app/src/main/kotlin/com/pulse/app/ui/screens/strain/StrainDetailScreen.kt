package com.pulse.app.ui.screens.strain

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
import com.pulse.app.core.algorithm.StrainCalculator
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.repo.ScoresRepository
import com.pulse.app.ui.components.LineChart
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

data class StrainDetailState(
    val strain: Double? = null,
    val muscular: Double? = null,
    val hrTimeline: List<Pair<Long, Double>> = emptyList(),
    val zoneMinutes: IntArray = IntArray(5),
    val workouts: List<String> = emptyList(),
    val optimalTarget: Double? = null
)

@HiltViewModel
class StrainDetailViewModel @Inject constructor(
    private val db: PulseDatabase,
    private val scoresRepository: ScoresRepository
) : ViewModel() {

    val state = MutableStateFlow(StrainDetailState())
    private val calc = StrainCalculator()

    init {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val e = scoresRepository.getDay(today) ?: scoresRepository.getDay(today.minusDays(1))
        val cycle = db.dailyCycleDao().get(today.toString())
            ?: db.dailyCycleDao().get(today.minusDays(1).toString())

        val start = cycle?.wakeTs ?: today.atStartOfDay(zone).toInstant().epochSecond
        val end = cycle?.nextWakeTs ?: today.plusDays(1).atStartOfDay(zone).toInstant().epochSecond

        val hr = db.hrDao().range(start, end)
        val rhr = db.baselineDao().all().firstOrNull { it.metric == "rhr" }?.mean ?: 55.0
        val profile = db.profileDao().get()
        val hrMax = profile?.hrMax ?: 190

        val workouts = db.workoutDao().range(start, end)
        val zones = calc.zoneMinutes(hr.map { com.pulse.app.core.model.HrSample(Instant.ofEpochSecond(it.ts), it.bpm, com.pulse.app.core.model.SourceId.HEALTH_CONNECT) }, rhr, hrMax)

        state.value = StrainDetailState(
            strain = e?.strain,
            muscular = e?.muscularLoad,
            hrTimeline = hr.map { it.ts to it.bpm },
            zoneMinutes = zones,
            workouts = workouts.map { "%s · %d min".format(it.type, (it.endTs - it.startTs) / 60) },
            optimalTarget = e?.recovery?.let { calc.optimalTarget(when { it <= 33 -> 0; it <= 66 -> 1; else -> 2 }) }
        )
    }
}

@Composable
fun StrainDetailScreen(onBack: () -> Unit, vm: StrainDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Strain", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("%.1f / 21".format(state.strain ?: 0.0), style = MaterialTheme.typography.displayMedium, color = PulseColors.Yellow)
                        Text("Optimal target today: ${state.optimalTarget?.let { "%.0f".format(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Muscular load: ${state.muscular?.let { "%.0f".format(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Heart-rate timeline (zones)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LineChart(series = state.hrTimeline, color = PulseColors.Red)
                        Spacer(Modifier.height(8.dp))
                        val names = listOf("Z1", "Z2", "Z3", "Z4", "Z5")
                        names.forEachIndexed { i, n ->
                            RowBar(n, state.zoneMinutes[i].toDouble(), state.zoneMinutes.max().toDouble().coerceAtLeast(1.0), PulseColors.Teal)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Workouts", style = MaterialTheme.typography.titleMedium)
                        if (state.workouts.isEmpty()) Text("None recorded today.", style = MaterialTheme.typography.bodySmall)
                        else state.workouts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
