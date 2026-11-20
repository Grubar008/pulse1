package com.pulse.app.ui.screens.journal

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
import com.pulse.app.core.model.BehaviorDef
import com.pulse.app.core.model.JournalEffect
import com.pulse.app.core.model.JournalEntry
import com.pulse.app.core.model.ValueType
import com.pulse.app.data.repo.JournalRepository
import com.pulse.app.ui.theme.PulseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class JournalState(
    val date: LocalDate = LocalDate.now(),
    val behaviors: List<BehaviorDef> = emptyList(),
    val today: List<JournalEntry> = emptyList(),
    val effects: List<JournalEffect> = emptyList(),
    val showEffects: Boolean = false
)

@HiltViewModel
class JournalViewModel @Inject constructor(private val journalRepository: JournalRepository) : ViewModel() {

    val state = MutableStateFlow(JournalState())

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val behaviors = journalRepository.behaviors()
            val entries = journalRepository.forDate(state.value.date)
            val effects = runCatching { journalRepository.effects() }.getOrDefault(emptyList())
            state.value = JournalState(
                date = state.value.date, behaviors = behaviors, today = entries,
                effects = effects, showEffects = state.value.showEffects
            )
        }
    }

    fun setDate(d: LocalDate) {
        state.value = state.value.copy(date = d)
        load()
    }

    fun toggleShowEffects() {
        state.value = state.value.copy(showEffects = !state.value.showEffects)
    }

    fun setBehavior(behavior: BehaviorDef, value: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (value == null) journalRepository.remove(state.value.date, behavior.id)
            else journalRepository.save(JournalEntry(state.value.date, behavior.id, value))
            load()
        }
    }

    fun addCustom(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            journalRepository.addCustomBehavior(label, ValueType.BOOLEAN)
            load()
        }
    }
}

@Composable
fun JournalScreen(vm: JournalViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Journal", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { vm.toggleShowEffects() }) {
                Text(if (state.showEffects) "Today" else "Insights")
            }
        }

        if (!state.showEffects) {
            var customText by remember { mutableStateOf("") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(customText, onValueChange = { customText = it }, label = { Text("Custom behavior") }, modifier = Modifier.weight(1f))
                Button(onClick = { if (customText.isNotBlank()) { vm.addCustom(customText); customText = "" } }) { Text("Add") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.behaviors.size) { i ->
                    val b = state.behaviors[i]
                    val entry = state.today.firstOrNull { it.behaviorId == b.id }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(b.label, style = MaterialTheme.typography.bodyMedium)
                        when (b.valueType) {
                            ValueType.BOOLEAN -> Switch(
                                checked = entry != null && entry.value > 0,
                                onCheckedChange = { on -> vm.setBehavior(b, if (on) 1.0 else null) }
                            )
                            ValueType.SCALE -> Row {
                                (1..5).forEach { v ->
                                    FilterChip(
                                        selected = entry?.value?.toInt() == v,
                                        onClick = { vm.setBehavior(b, v.toDouble()) },
                                        label = { Text("$v") }
                                    )
                                }
                            }
                            else -> OutlinedTextField(
                                value = entry?.value?.toString() ?: "",
                                onValueChange = { t -> t.toDoubleOrNull()?.let { vm.setBehavior(b, it) } },
                                label = { Text(b.valueType.name.lowercase()) },
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.effects.isEmpty()) {
                    item { Text("Insights appear after at least 5 days with and 5 days without a behavior.", style = MaterialTheme.typography.bodySmall) }
                }
                items(state.effects.size) { i ->
                    val e = state.effects[i]
                    val color = if (e.deltaRecovery >= 0) PulseColors.Green else PulseColors.Red
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.behaviorId, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Next-day recovery %+ .1f pts (d=%.2f)".format(e.deltaRecovery, e.effectSize),
                                color = color
                            )
                            Text(
                                "${e.samplesWith} with / ${e.samplesWithout} without · p=%.3f · ${e.confidence}".format(e.pValue),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
