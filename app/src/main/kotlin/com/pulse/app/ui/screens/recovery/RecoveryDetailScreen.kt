package com.pulse.app.ui.screens.recovery

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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.core.algorithm.AlgorithmsConfig
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.repo.ScoresRepository
import com.pulse.app.ui.components.LineChart
import com.pulse.app.ui.components.RowBar
import com.pulse.app.ui.theme.PulseColors
import com.pulse.app.ui.theme.recoveryColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class RecoveryDetailState(
    val recovery: Int? = null,
    val contributions: Map<String, Double> = emptyMap(),
    val trend30: List<Pair<Long, Int>> = emptyList(),
    val baselines: Map<String, Double> = emptyMap()
)

@HiltViewModel
class RecoveryDetailViewModel @Inject constructor(
    private val db: PulseDatabase,
    private val scoresRepository: ScoresRepository
) : ViewModel() {

    val state = MutableStateFlow(RecoveryDetailState())

    init {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val today = LocalDate.now()
        val e = scoresRepository.getDay(today) ?: scoresRepository.getDay(today.minusDays(1))
        val trend = scoresRepository.recent(30)
            .mapNotNull { it.recovery?.let { r -> LocalDate.parse(it.date).atStartOfDay().toInstant(ZoneOffset.UTC).epochSecond to r } }
        val bases = db.baselineDao().all().associate { it.metric to it.mean }
        state.value = RecoveryDetailState(
            recovery = e?.recovery,
            contributions = e?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, Double>>(it.inputs) }.getOrNull() } ?: emptyMap(),
            trend30 = trend,
            baselines = bases
        )
    }
}

@Composable
fun RecoveryDetailScreen(onBack: () -> Unit, vm: RecoveryDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recovery = state.recovery

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Recovery", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (recovery == null) {
                            Text("Calibrating", style = MaterialTheme.typography.headlineMedium, color = PulseColors.Yellow)
                            Text("Scores appear after ${AlgorithmsConfig.BASELINE_MIN_DAYS} nights of data.")
                        } else {
                            Text("$recovery", style = MaterialTheme.typography.displayMedium, color = recoveryColor(recovery))
                            val zoneLabel = when {
                                recovery <= 33 -> "Red zone"
                                recovery <= 66 -> "Yellow zone"
                                else -> "Green zone"
                            }
                            Text(zoneLabel, color = recoveryColor(recovery))
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Input breakdown", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (state.contributions.isEmpty()) {
                            Text("No inputs yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.contributions.forEach { (name, z) ->
                                RowBar(name, z * 100.0, 150.0, PulseColors.Teal)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                "Values are z-scores vs your 30-day baseline.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("30-day trend", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = state.trend30.map { it.first to it.second.toDouble() },
                            color = PulseColors.Green
                        )
                    }
                }
            }
        }
    }
}
