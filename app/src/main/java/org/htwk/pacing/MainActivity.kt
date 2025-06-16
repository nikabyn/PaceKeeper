package org.htwk.pacing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.htwk.pacing.ui.screens.HomeScreen
import org.htwk.pacing.ui.screens.MeasurementsScreen
import org.htwk.pacing.ui.screens.SettingsScreen
import org.htwk.pacing.ui.theme.PacingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Main() }
    }
}

@Composable
fun Main() {
    PacingTheme {
        val navController = rememberNavController()
        val startDestination = Destination.HOME
        val selectedDestination =
            rememberSaveable { mutableIntStateOf(startDestination.ordinal) }

        Scaffold(
            bottomBar = { NavBar(navController, selectedDestination) },
        ) { contentPadding ->
            AppNavHost(
                navController,
                startDestination,
                modifier = Modifier.padding(contentPadding)
            )
        }
    }
}

@Composable
fun NavBar(
    navController: NavHostController,
    selectedDestination: MutableState<Int>,
) {
    NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
        Destination.entries.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = selectedDestination.value == index,
                onClick = {
                    navController.navigate(route = destination.route)
                    selectedDestination.value = index
                },
                icon = destination.icon,
                label = { Text(destination.label) }
            )
        }
    }
}

enum class Destination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    HOME("home", "Home", { Icon(Icons.Rounded.Home, "Home") }),
    MEASUREMENTS(
        "measurements",
        "Measurements",
        { Icon(painter = painterResource(R.drawable.rounded_show_chart_24), "Measurements") }),
    SETTINGS("settings", "Settings", { Icon(Icons.Rounded.Settings, "Settings") })
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Destination,
    modifier: Modifier,
) {
    NavHost(
        navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        Destination.entries.forEach { destination ->
            composable(destination.route) {
                when (destination) {
                    Destination.HOME -> HomeScreen()
                    Destination.MEASUREMENTS -> MeasurementsScreen()
                    Destination.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}