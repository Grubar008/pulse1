package com.pulse.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.app.core.model.Sex
import com.pulse.app.core.model.UserProfile
import com.pulse.app.data.repo.ProfileRepository
import com.pulse.app.data.source.health.HealthConnectSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val healthSource: HealthConnectSource
) : ViewModel() {

    val profile = MutableStateFlow(UserProfile())
    val hcStatus = MutableStateFlow<com.pulse.app.core.model.SourceStatus?>(null)

    fun permissions(): Set<String> = HealthConnectSource.HealthPerms.READ_PERMISSIONS

    fun saveName(v: String) { profile.value = profile.value.copy(name = v) }
    fun saveAge(v: Int) { profile.value = profile.value.copy(age = v) }
    fun saveSex(v: Sex) { profile.value = profile.value.copy(sex = v) }
    fun saveWeight(v: Double) { profile.value = profile.value.copy(weightKg = v) }
    fun saveSleepNeed(v: Int) { profile.value = profile.value.copy(sleepNeedBaseMin = v) }

    fun refreshStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            hcStatus.value = runCatching { healthSource.status() }.getOrNull()
        }
    }

    fun complete() {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepository.save(profile.value.copy(onboarded = true))
        }
    }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsState()
    val status by vm.hcStatus.collectAsState()

    LaunchedEffect(Unit) { vm.refreshStatus() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Pulse", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pulse computes Recovery, Strain and Sleep scores from data your wearable already writes to Health Connect. Everything stays on this device.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(value = profile.name, onValueChange = vm::saveName, label = { Text("Name") })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Age:")
            Slider(value = profile.age.toFloat(), onValueChange = { vm.saveAge(it.toInt()) }, valueRange = 14f..90f, modifier = Modifier.width(180.dp))
            Text("${profile.age}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s ->
                FilterChip(selected = profile.sex == s, onClick = { vm.saveSex(s) }, label = { Text(s.name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sleep need (h):")
            Slider(value = (profile.sleepNeedBaseMin / 60f), onValueChange = { vm.saveSleepNeed((it * 60).toInt()) }, valueRange = 6f..10f, modifier = Modifier.width(180.dp))
            Text("%.1f".format(profile.sleepNeedBaseMin / 60.0))
        }

        val launcher = rememberLauncherForActivityResult(
            androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
        ) { vm.refreshStatus() }

        when (status) {
            is com.pulse.app.core.model.SourceStatus.Connected ->
                Text("Health Connect connected ✓", color = androidx.compose.ui.graphics.Color(0xFF53C22B))
            is com.pulse.app.core.model.SourceStatus.PermissionsMissing ->
                Text("Grant Health Connect permissions to continue scoring.")
            is com.pulse.app.core.model.SourceStatus.Outdated ->
                Text("Health Connect needs an update (Play Store).")
            is com.pulse.app.core.model.SourceStatus.NotInstalled ->
                Text("Health Connect is not installed (Play Store).")
            null -> Text("Checking Health Connect…")
            else -> {}
        }

        Button(onClick = { launcher.launch(vm.permissions().toSet()) }) {
            Text("Grant Health Connect permissions")
        }

        Text(
            "First 4 nights are calibration: scores appear once your baseline is learned.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Not a medical device.", style = MaterialTheme.typography.labelSmall)

        Button(onClick = { vm.complete(); onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text("Start using Pulse")
        }
    }
}
