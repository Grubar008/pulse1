package com.pulse.app.ui.screens.stress

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
import com.pulse.app.core.algorithm.StressCalculator
import com.pulse.app.core.model.*
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.ui.components.BarChart
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

data class StressDetailState(
    val bandPct: Map<StressBand, Double> = emptyMap(),
    val curve: List<Double> = emptyList(),
    val avg: Double? = null
)

@HiltViewModel
class StressDetailViewModel @Inject constructor(private val db: PulseDatabase) : ViewModel() {

    val state = MutableStateFlow(StressDetailState())

    init {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.atStartOfDay(zone).toInstant().epochSecond
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().epochSecond

        val baselines = db.baselineDao().all().associate {
            it.metric to com.pulse.app.core.model.BaselineEntry(it.metric, it.mean, it.sd, Instant.ofEpochSecond(it.updatedAt))
        }
        if (baselines.isEmpty()) { state.value = StressDetailState(); return }

        val hr = db.hrDao().range(start, end).map { HrSample(Instant.ofEpochSecond(it.ts), it.bpm, SourceId.HEALTH_CONNECT) }
        val hrv = db.hrvDao().range(start, end).map { HrvSample(Instant.ofEpochSecond(it.ts), it.rmssd, SourceId.HEALTH_CONNECT) }
        val workouts = db.workoutDao().range(start, end).map {
            Workout(it.type, Instant.ofEpochSecond(it.startTs), Instant.ofEpochSecond(it.endTs), null, null, null, SourceId.HEALTH_CONNECT)
        }
        val sleep = db.sleepDao().sessionsInRange(start - 43200, end).map {
            SleepSession(Instant.ofEpochSecond(it.startTs), Instant.ofEpochSecond(it.endTs), emptyList(), SourceId.HEALTH_CONNECT)
        }

        val result = StressCalculator().calculate(hr, hrv, workouts, sleep, baselines)
        state.value = StressDetailState(
            bandPct = result.bandPct,
            curve = result.windows.map { it.level.toDouble() },
            avg = result.avgLevel.takeIf { result.windows.isNotEmpty() }
        )
    }
}

@Composable
fun StressDetailScreen(onBack: () -> Unit, vm: StressDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Stress", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val avg = state.avg
                        if (avg == null) {
                            Text("Calibrating", color = PulseColors.Yellow)
                            Text("Stress needs a resting-HR baseline (a few days of data).")
                        } else {
                            Text("%.2f / 3".format(avg), style = MaterialTheme.typography.displayMedium, color = PulseColors.Yellow)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Time in bands", style = MaterialTheme.typography.titleMedium)
                        val order = listOf(StressBand.CALM, StressBand.MILD, StressBand.MODERATE, StressBand.HIGH)
                        order.forEach { band ->
                            val pct = state.bandPct[band] ?: 0.0
                            val color = when (band) {
                                StressBand.CALM -> PulseColors.Teal
                                StressBand.MILD -> PulseColors.Green
                                StressBand.MODERATE -> PulseColors.Yellow
                                StressBand.HIGH -> PulseColors.Red
                            }
                            RowBar(band.label, pct, 100.0, color)
                            Spacer(Modifier.height(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Daily stress curve (today, 5-min windows)", style = MaterialTheme.typography.titleSmall)
                        BarChart(values = state.curve, colorFor = { if (it >= 2.0) PulseColors.Red else PulseColors.Yellow })
                    }
                }
            }
        }
    }
}
