package com.pulse.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.pulse.app.ui.nav.Routes

@Composable
fun BottomNavBar(nav: NavHostController, current: String?) {
    NavigationBar {
        val items = listOf(
            Triple(Routes.DASHBOARD, Icons.Default.Favorite, "Home"),
            Triple(Routes.ACTIVITIES, Icons.Default.BarChart, "Activities"),
            Triple(Routes.TRENDS, Icons.Default.BarChart, "Trends"),
            Triple(Routes.JOURNAL, Icons.Default.EditNote, "Journal"),
            Triple(Routes.COACH, Icons.Default.SmartToy, "Coach")
        )
        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = current == route,
                onClick = {
                    if (current != route) {
                        nav.navigate(route) {
                            popUpTo(Routes.DASHBOARD) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
