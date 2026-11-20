package com.pulse.app.ui.screens.coverage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pulse.app.core.model.DataCoverage
import com.pulse.app.core.model.MetricType
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.source.health.HealthConnectSource
import com.pulse.app.ui.theme.PulseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CoverageViewModel @Inject constructor(
    private val db: PulseDatabase,
    private val healthSource: HealthConnectSource
) : ViewModel() {

    val coverage = MutableStateFlow<List<DataCoverage>>(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val from = Instant.now().minusSeconds(14L * 86400).epochSecond
            val until = Instant.now().epochSecond
            fun metric(name: String, has: Boolean, lastTs: Long?): DataCoverage =
                DataCoverage(
                    metric = MetricType.valueOf(name),
                    sources = if (has) listOf(com.pulse.app.core.model.SourceId.HEALTH_CONNECT) else emptyList(),
                    lastSeen = lastTs?.let { Instant.ofEpochSecond(it) }
                )

            val hr = db.hrDao().range(from, until)
            val hrv = db.hrvDao().range(from, until)
            val spo2 = db.spo2Dao().range(from, until)
            val resp = db.respDao().range(from, until)
            val skin = db.skinTempDao().range(from, until)
            val steps = db.stepsDao().range(from, until)
            val sleep = db.sleepDao().sessionsInRange(from, until)
            val workouts = db.workoutDao().range(from, until)

            coverage.value = listOf(
                metric("HR", hr.isNotEmpty(), hr.maxOfOrNull { it.ts }),
                metric("HRV", hrv.isNotEmpty(), hrv.maxOfOrNull { it.ts }),
                metric("SPO2", spo2.isNotEmpty(), spo2.maxOfOrNull { it.ts }),
                metric("RESP_RATE", resp.isNotEmpty(), resp.maxOfOrNull { it.ts }),
                metric("SKIN_TEMP", skin.isNotEmpty(), skin.maxOfOrNull { it.ts }),
                metric("STEPS", steps.isNotEmpty(), steps.maxOfOrNull { it.startTs }),
                metric("SLEEP", sleep.isNotEmpty(), sleep.maxOfOrNull { it.endTs }),
                metric("WORKOUT", workouts.isNotEmpty(), workouts.maxOfOrNull { it.startTs })
            )
        }
    }
}

@Composable
fun DataCoverageScreen(onBack: () -> Unit, vm: CoverageViewModel = hiltViewModel()) {
    val list by vm.coverage.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Data coverage (14 days)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Scoring degrades gracefully: when a metric is missing its weight is redistributed.",
            style = MaterialTheme.typography.bodySmall
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(list.size) { i ->
                val c = list[i]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(c.metric.name.replace('_', ' '))
                        Text(
                            if (c.sources.isEmpty()) "missing" else "ok · ${c.lastSeen.toString().take(10)}",
                            color = if (c.sources.isEmpty()) PulseColors.Red else PulseColors.Green
                        )
                    }
                }
            }
        }
    }
}
