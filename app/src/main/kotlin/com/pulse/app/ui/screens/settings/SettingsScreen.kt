package com.pulse.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.prefs.PulseSettings
import com.pulse.app.data.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val demoMode: Boolean = false,
    val notifications: Boolean = true,
    val coachKey: Boolean = false,
    val coachKeyInput: String = "",
    val coachKeySaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PulseSettings,
    private val profileRepository: ProfileRepository,
    private val db: PulseDatabase
) : ViewModel() {

    val state = MutableStateFlow(SettingsState())

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = prefs.settings.first()
            state.value = state.value.copy(
                demoMode = app.demoMode,
                notifications = app.notificationsEnabled,
                coachKey = prefs.hasCoachKey()
            )
        }
    }

    fun setDemo(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { prefs.setDemoMode(on); load() }
    }

    fun setNotifications(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { prefs.setNotifications(on); load() }
    }

    fun setCoachKeyInput(v: String) { state.value = state.value.copy(coachKeyInput = v) }

    fun saveCoachKey() {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setCoachKey(state.value.coachKeyInput)
            state.value = state.value.copy(coachKey = true, coachKeySaved = true, coachKeyInput = "")
        }
    }

    fun wipe() {
        viewModelScope.launch(Dispatchers.IO) {
            db.wipeDao().wipeHr(); db.wipeDao().wipeHrv(); db.wipeDao().wipeSpo2()
            db.wipeDao().wipeResp(); db.wipeDao().wipeSkin(); db.wipeDao().wipeSteps()
            db.wipeDao().wipeSleepSessions(); db.wipeDao().wipeSleepStages()
            db.wipeDao().wipeWorkouts(); db.wipeDao().wipeCycles(); db.wipeDao().wipeScores()
            db.wipeDao().wipeBaselines(); db.wipeDao().wipeJournal()
        }
    }
}


@Composable
fun SettingsScreen(onOpenConnectivity: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedButton(onClick = onOpenConnectivity, modifier = Modifier.fillMaxWidth()) { Text("Connectivity & sources") }

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Demo mock data (30 days)")
            Switch(checked = state.demoMode, onCheckedChange = vm::setDemo)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Notifications")
            Switch(checked = state.notifications, onCheckedChange = vm::setNotifications)
        }

        Text("AI coach API key", style = MaterialTheme.typography.titleMedium)
        Text(
            if (state.coachKey) "Key stored (encrypted)." else "Not set. Chat won't work until set. Stored in EncryptedSharedPreferences.",
            style = MaterialTheme.typography.bodySmall
        )
        Row {
            OutlinedTextField(
                value = state.coachKeyInput,
                onValueChange = vm::setCoachKeyInput,
                label = { Text("API key") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = vm::saveCoachKey, modifier = Modifier.padding(4.dp)) { Text("Save") }
        }

        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text(
            "All health data stays on this device in an SQLCipher-encrypted database. Nothing is uploaded unless you configure a personal Garmin/Zepp backend or use the AI coach.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Pulse is not a medical device.", style = MaterialTheme.typography.labelSmall)

        Button(
            onClick = { vm.wipe() },
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE5484D)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Delete all data") }
    }
}
