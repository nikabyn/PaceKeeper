package org.htwk.pacing.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.SymptomScreen
import org.htwk.pacing.ui.screens.HomeScreen
import org.htwk.pacing.ui.screens.MeasurementsScreen
import org.htwk.pacing.ui.screens.SettingsScreen
import org.htwk.pacing.ui.theme.PacingTheme

@Composable
fun Main() {
    PacingTheme {
        val navController = rememberNavController()
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val parentRoute = navBackStackEntry?.value?.destination?.parent?.route
        val selectedDestination = rememberSaveable { mutableIntStateOf(0) }

        if (parentRoute == "main_nav") {
            Scaffold(
                bottomBar = { NavBar(navController, selectedDestination) },
            ) { contentPadding ->
                AppNavHost(
                    navController,
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize()
                )
            }
        } else {
            AppNavHost(navController, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun NavBar(
    navController: NavHostController,
    selectedDestination: MutableState<Int>,
) {
    NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
        NavBarEntries.entries.forEachIndexed { index, destination ->
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

enum class NavBarEntries(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    HOME(
        Route.Home,
        "Home",
        { Icon(Icons.Rounded.Home, "Home") },
    ),
    MEASUREMENTS(
        Route.Measurements,
        "Measurements",
        { Icon(painter = painterResource(R.drawable.rounded_show_chart_24), "Measurements") },
    ),
    SETTINGS(
        Route.Settings,
        "Settings",
        { Icon(Icons.Rounded.Settings, "Settings") },
    )
}

object Route {
    val Home = "home"
    val Symptoms = "symptoms"
    val Measurements = "measurements"
    val Settings = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier,
) {
    NavHost(
        navController,
        startDestination = "main_nav",
        modifier = modifier
    ) {
        navigation(route = "main_nav", startDestination = Route.Home) {
            composable(route = Route.Home) { HomeScreen(navController) }
            composable(route = Route.Measurements) { MeasurementsScreen() }
            composable(route = Route.Settings) { SettingsScreen() }
        }

        composable(route = Route.Symptoms) {
            SymptomScreen(navController)
        }
    }
}