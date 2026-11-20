package com.pulse.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulse.app.ui.screens.activities.ActivitiesScreen
import com.pulse.app.ui.screens.coach.CoachScreen
import com.pulse.app.ui.screens.connectivity.ConnectivityScreen
import com.pulse.app.ui.screens.dashboard.DashboardScreen
import com.pulse.app.ui.screens.journal.JournalScreen
import com.pulse.app.ui.screens.onboarding.OnboardingScreen
import com.pulse.app.ui.screens.recovery.RecoveryDetailScreen
import com.pulse.app.ui.screens.settings.SettingsScreen
import com.pulse.app.ui.screens.sleep.SleepDetailScreen
import com.pulse.app.ui.screens.strain.StrainDetailScreen
import com.pulse.app.ui.screens.stress.StressDetailScreen
import com.pulse.app.ui.screens.trends.TrendsScreen
import com.pulse.app.ui.screens.coverage.DataCoverageScreen
import com.pulse.app.data.prefs.PulseSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

@HiltViewModel
class NavViewModel @Inject constructor(settings: PulseSettings) : ViewModel() {
    val onboarded = settings.settings
        .map { it.onboarded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null as Boolean?)
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val RECOVERY = "recovery"
    const val STRAIN = "strain"
    const val SLEEP = "sleep"
    const val STRESS = "stress"
    const val ACTIVITIES = "activities"
    const val TRENDS = "trends"
    const val JOURNAL = "journal"
    const val COACH = "coach"
    const val CONNECTIVITY = "connectivity"
    const val SETTINGS = "settings"
    const val COVERAGE = "coverage"
}

@Composable
fun PulseApp(nav: NavHostController = rememberNavController(), vm: NavViewModel = hiltViewModel()) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onboarded by vm.onboarded.collectAsState(initial = null)
    val start = when (onboarded) {
        null -> return
        true -> Routes.DASHBOARD
        else -> Routes.ONBOARDING
    }

    Scaffold(
        bottomBar = {
            if (current in setOf(Routes.DASHBOARD, Routes.ACTIVITIES, Routes.TRENDS, Routes.JOURNAL, Routes.COACH)) {
                BottomNavBar(nav, current)
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.ONBOARDING) { OnboardingScreen(onDone = { nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }) }
            composable(Routes.DASHBOARD) { DashboardScreen(
                onOpenRecovery = { nav.navigate(Routes.RECOVERY) },
                onOpenStrain = { nav.navigate(Routes.STRAIN) },
                onOpenSleep = { nav.navigate(Routes.SLEEP) },
                onOpenStress = { nav.navigate(Routes.STRESS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            ) }
            composable(Routes.RECOVERY) { RecoveryDetailScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.STRAIN) { StrainDetailScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SLEEP) { SleepDetailScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.STRESS) { StressDetailScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ACTIVITIES) { ActivitiesScreen() }
            composable(Routes.TRENDS) { TrendsScreen() }
            composable(Routes.JOURNAL) { JournalScreen() }
            composable(Routes.COACH) { CoachScreen() }
            composable(Routes.CONNECTIVITY) { ConnectivityScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenConnectivity = { nav.navigate(Routes.CONNECTIVITY) }) }
            composable(Routes.COVERAGE) { DataCoverageScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
