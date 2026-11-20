package com.pulse.app.ui.screens.trends

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pulse.app.data.repo.ScoresRepository
import com.pulse.app.ui.components.BarChart
import com.pulse.app.ui.components.LineChart
import com.pulse.app.ui.theme.PulseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TrendsState(
    val range: Int = 30,
    val recovery: List<Double> = emptyList(),
    val strain: List<Double> = emptyList(),
    val sleep: List<Double> = emptyList(),
    val labels: List<String> = emptyList(),
    val summary: String = ""
)

@HiltViewModel
class TrendsViewModel @Inject constructor(private val scoresRepository: ScoresRepository) : ViewModel() {

    val state = MutableStateFlow(TrendsState())

    init { load(30) }

    fun load(days: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = scoresRepository.recent(days).sortedBy { it.date }
            state.value = TrendsState(
                range = days,
                recovery = list.mapNotNull { it.recovery?.toDouble() },
                strain = list.mapNotNull { it.strain },
                sleep = list.mapNotNull { it.sleepPerformance?.toDouble() },
                labels = list.takeLast(5).map { it.date.substring(5) },
                summary = if (list.isEmpty()) "No data yet." else {
                    val rec = list.mapNotNull { it.recovery }.map { it.toDouble() }
                    val st = list.mapNotNull { it.strain }
                    "Recovery avg %.0f · Strain avg %.1f · Nights %d".format(
                        if (rec.isEmpty()) 0.0 else rec.average(),
                        if (st.isEmpty()) 0.0 else st.average(),
                        list.count { it.sleepPerformance != null }
                    )
                }
            )
        }
    }
}

@Composable
fun TrendsScreen(vm: TrendsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Trends", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90, 365).forEach { d ->
                FilterChip(selected = state.range == d, onClick = { vm.load(d) }, label = { Text("${d}d") })
            }
        }
        Text(state.summary, style = MaterialTheme.typography.bodySmall)

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Recovery", style = MaterialTheme.typography.titleMedium)
                LineChart(series = state.recovery.mapIndexed { i, v -> i.toLong() to v }, color = PulseColors.Green)
            }
        }
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Strain", style = MaterialTheme.typography.titleMedium)
                BarChart(values = state.strain, colorFor = { if (it > 14) PulseColors.Red else PulseColors.Yellow })
            }
        }
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Sleep performance", style = MaterialTheme.typography.titleMedium)
                LineChart(series = state.sleep.mapIndexed { i, v -> i.toLong() to v }, color = PulseColors.Purple)
            }
        }
    }
}
