package com.pulse.app.ui.screens.activities

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
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.ui.components.LineChart
import com.pulse.app.ui.theme.PulseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ActivitiesState(val rows: List<ActivityRow> = emptyList())

data class ActivityRow(
    val title: String,
    val whenText: String,
    val durationMin: Long,
    val hr: List<Pair<Long, Double>>
)

@HiltViewModel
class ActivitiesViewModel @Inject constructor(private val db: PulseDatabase) : ViewModel() {
    val state = MutableStateFlow(ActivitiesState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val workouts = db.workoutDao().recent(50)
            val rows = workouts.map { w ->
                val hr = db.hrDao().range(w.startTs, w.endTs)
                ActivityRow(
                    title = w.type.replaceFirstChar { it.uppercase() },
                    whenText = Instant.ofEpochSecond(w.startTs).toString().take(16).replace('T', ' '),
                    durationMin = (w.endTs - w.startTs) / 60,
                    hr = hr.map { it.ts to it.bpm }
                )
            }
            state.value = ActivitiesState(rows)
        }
    }
}

@Composable
fun ActivitiesScreen(vm: ActivitiesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Activities", style = MaterialTheme.typography.headlineSmall) }
        if (state.rows.isEmpty()) {
            item { Text("No workouts yet. They sync from Health Connect or files.", style = MaterialTheme.typography.bodySmall) }
        }
        items(state.rows.size) { i ->
            val row = state.rows[i]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                    Text("${row.whenText} · ${row.durationMin} min", style = MaterialTheme.typography.bodySmall)
                    if (row.hr.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        LineChart(series = row.hr, color = PulseColors.Red)
                    } else {
                        Text("No intraday HR for this workout.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
