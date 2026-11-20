package com.pulse.app.ui.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pulse.app.ui.theme.PulseTheme

class PrivacyRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = PRIVACY,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    private companion object {
        val PRIVACY = """
            Pulse privacy policy

            Pulse reads health data (heart rate, HRV, sleep, spO2, respiratory rate, skin temperature, steps, workouts, VO2 max) exclusively through Health Connect to compute wellness scores: Recovery, Strain, Sleep, Stress.

            Data storage: everything is stored locally in an AES-256 (SQLCipher) encrypted database on this device. No account is created. No data is transmitted anywhere by default.

            Optional data flows: if YOU configure a personal backend for Garmin/Zepp access, or enable the AI coach with your own API key, only the summarized metrics you approve are sent to endpoints you control.

            Deletion: use Settings > Delete all data, or revoke Health Connect permissions at any time.

            Pulse is not a medical device and does not provide medical advice.
        """.trimIndent()
    }
}
