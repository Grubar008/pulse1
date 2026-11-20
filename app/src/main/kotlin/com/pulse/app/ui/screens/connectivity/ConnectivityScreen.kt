package com.pulse.app.ui.screens.connectivity

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
import com.pulse.app.core.model.SourceId
import com.pulse.app.core.model.SourceStatus
import com.pulse.app.data.repo.IngestRepository
import com.pulse.app.data.source.health.HealthConnectSource
import com.pulse.app.work.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceRow(val id: SourceId, val status: SourceStatus, val lastSyncTs: Long?, val error: String?)

data class ConnectivityState(
    val rows: List<SourceRow> = emptyList(),
    val syncing: Boolean = false,
    val priority: List<SourceId> = emptyList()
)

@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    private val ingestRepository: IngestRepository,
    private val healthSource: HealthConnectSource,
    private val syncCoordinator: SyncCoordinator,
    private val prefs: com.pulse.app.data.prefs.PulseSettings
) : ViewModel() {

    val state = MutableStateFlow(ConnectivityState())

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val conns = ingestRepository.allConnections().associateBy { it.sourceId }
            val hcStatus = runCatching { healthSource.status() }.getOrDefault(SourceStatus.NotInstalled)
            val rows = SourceId.entries.map { id ->
                val conn = conns[id.name]
                SourceRow(
                    id = id,
                    status = when (id) {
                        SourceId.HEALTH_CONNECT -> hcStatus
                        else -> if (conn?.enabled == true) SourceStatus.Connected else SourceStatus.Disconnected
                    },
                    lastSyncTs = conn?.lastSyncTs,
                    error = conn?.lastError
                )
            }
            val pr = prefs.settings.first()
            state.value = ConnectivityState(
                rows = rows,
                priority = pr.source.priority.ifEmpty { listOf(SourceId.HEALTH_CONNECT, SourceId.GARMIN, SourceId.ZEPP, SourceId.FILE_IMPORT) },
                syncing = state.value.syncing
            )
        }
    }

    fun toggle(sourceId: SourceId) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = ingestRepository.allConnections().firstOrNull { it.sourceId == sourceId.name }
            if (conn?.enabled == true) ingestRepository.disconnect(sourceId) else ingestRepository.connect(sourceId, 1)
            load()
        }
    }

    fun setPriorityFirst(sourceId: SourceId) {
        viewModelScope.launch(Dispatchers.IO) {
            val newPriority = listOf(sourceId) + state.value.priority.filter { it != sourceId }
            prefs.setPriority(newPriority)
            load()
        }
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            state.value = state.value.copy(syncing = true)
            runCatching { syncCoordinator.sync(listOf(healthSource)) }
            state.value = state.value.copy(syncing = false)
            load()
        }
    }
}


@Composable
fun ConnectivityScreen(onBack: () -> Unit, vm: ConnectivityViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Connectivity", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pulse reads local data only. Garmin/Zepp direct sources are experimental and need your own backend.",
            style = MaterialTheme.typography.labelSmall
        )
        Button(onClick = { vm.syncNow() }, enabled = !state.syncing, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(if (state.syncing) "Syncing…" else "Sync now")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.rows.size) { i ->
                val row = state.rows[i]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(row.id.name.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                        val statusText = when (row.status) {
                            SourceStatus.Connected -> "Connected"
                            SourceStatus.PermissionsMissing -> "Needs permissions"
                            SourceStatus.Outdated -> "Update required"
                            SourceStatus.NotInstalled -> "Not installed"
                            SourceStatus.Disconnected -> "Disconnected"
                            is SourceStatus.Error -> row.status.message
                        }
                        Text(statusText, style = MaterialTheme.typography.bodySmall)
                        row.error?.let { Text("Error: $it", color = androidx.compose.ui.graphics.Color(0xFFE5484D), style = MaterialTheme.typography.bodySmall) }
                        row.lastSyncTs?.let {
                            Text("Last sync: ${java.time.Instant.ofEpochSecond(it)}", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.toggle(row.id) }) {
                                Text(if (row.status == SourceStatus.Connected) "Disconnect" else "Connect")
                            }
                            if (state.priority.isNotEmpty() && state.priority.firstOrNull() != row.id) {
                                OutlinedButton(onClick = { vm.setPriorityFirst(row.id) }) { Text("Prioritize") }
                            } else if (state.priority.firstOrNull() == row.id) {
                                Text("Primary source", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
