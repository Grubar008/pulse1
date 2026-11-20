package com.pulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.pulse.app.ui.nav.PulseApp
import com.pulse.app.ui.theme.PulseTheme
import com.pulse.app.work.DailySyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                DailySyncWorker.schedule(applicationContext)
            }
        }
        setContent {
            PulseTheme {
                PulseApp()
            }
        }
    }
}
