package com.pulse.app.ui.screens.coach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CoachScreen(vm: CoachViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Coach", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Requires your own LLM API key (Settings). Metrics are sent only when you chat.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages.size) { i ->
                val m = state.messages[i]
                val isUser = m.role == "user"
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(m.text, Modifier.padding(12.dp))
                }
            }
            if (state.busy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Ask your coach…") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { vm.send(input); input = "" },
                enabled = !state.busy
            ) { Text("Send") }
        }
    }
}
