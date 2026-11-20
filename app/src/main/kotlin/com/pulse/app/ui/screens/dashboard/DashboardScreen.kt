package com.pulse.app.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.ui.components.Dial
import com.pulse.app.ui.components.RowBar
import com.pulse.app.ui.components.LineChart
import com.pulse.app.ui.theme.PulseColors
import com.pulse.app.ui.theme.recoveryColor
import com.pulse.app.ui.theme.strainColor

@Composable
fun DashboardScreen(
    onOpenRecovery: () -> Unit,
    onOpenStrain: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenStress: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pulse", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.hcNotInstalled || state.hcOutdated) {
                Card(colors = CardDefaults.cardColors(containerColor = PulseColors.Yellow), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.hcNotInstalled) "Install Health Connect to sync your wearable data."
                        else "Update Health Connect from the Play Store.",
                        modifier = Modifier.padding(12.dp),
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.calibrating && state.recovery != null) {
                Text("Calibrating…", style = MaterialTheme.typography.labelLarge, color = PulseColors.Yellow)
                Spacer(Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Dial(
                    value = (state.recovery ?: 0).toDouble(),
                    label = "Recovery",
                    sublabel = "morning",
                    color = if (state.recovery == null) PulseColors.OnSurfaceDim else recoveryColor(state.recovery!!),
                    onTap = onOpenRecovery
                )
                Dial(
                    value = state.strain ?: 0.0,
                    maxValue = 21.0,
                    label = "Strain",
                    sublabel = "today",
                    color = if (state.strain == null) PulseColors.OnSurfaceDim else strainColor(state.strain!!),
                    onTap = onOpenStrain
                )
            }
            Spacer(Modifier.height(8.dp))
            Dial(
                value = (state.sleepPerformance ?: 0).toDouble(),
                label = "Sleep performance",
                sublabel = if (state.sleepAsleepMin != null && state.sleepNeedMin != null)
                    "%.1f h of %.1f h needed".format(state.sleepAsleepMin!! / 60, state.sleepNeedMin!! / 60)
                else "tap for details",
                color = if (state.sleepPerformance == null) PulseColors.OnSurfaceDim else PulseColors.Purple,
                onTap = onOpenSleep
            )
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenStress)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Stress", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val s = state.stressAvg
                    if (s == null) Text("No stress data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else RowBar("Average level (%.2f / 3)".format(s), s, 3.0, color = PulseColors.Yellow)
                    if (state.steps != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Steps today: %.0f".format(state.steps!!), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Insight", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(state.insight, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.trend7.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Recovery — last 7 days", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = state.trend7.map { it.first to it.second.toDouble() },
                            color = PulseColors.Green
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Not a medical device. Scores are on-device wellness estimates.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
