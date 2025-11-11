package org.htwk.pacing.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.ui.screens.HomeScreen
import org.htwk.pacing.ui.screens.MeasurementsScreen
import org.htwk.pacing.ui.screens.SettingsScreen
import org.htwk.pacing.ui.screens.SymptomScreen
import org.htwk.pacing.ui.screens.UserProfileScreen
import org.htwk.pacing.ui.screens.UserProfileViewModel
import org.htwk.pacing.ui.theme.PacingTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun Main() {
    PacingTheme {
        val navController = rememberNavController()
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val parentRoute = navBackStackEntry.value?.destination?.parent?.route
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
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
    }
}

enum class NavBarEntries(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: @Composable () -> Unit,
) {
    HOME(
        Route.HOME,
        R.string.home,
        { Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.home)) }
    ),
    MEASUREMENTS(
        Route.MEASUREMENTS,
        R.string.measurements,
        {
            Icon(
                painter = painterResource(R.drawable.rounded_show_chart_24),
                contentDescription = stringResource(R.string.measurements)
            )
        }
    ),
    SETTINGS(
        Route.SETTINGS,
        R.string.settings,
        { Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings)) }
    )
}

object Route {
    const val HOME = "home"
    const val MEASUREMENTS = "measurements"
    const val SETTINGS = "settings"
    const val USERPROFILE = "userprofile"
    fun symptoms(feeling: Feeling) = "symptoms/${feeling.level}"
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
        navigation(route = "main_nav", startDestination = Route.HOME) {
            composable(route = Route.HOME) { HomeScreen(navController) }
            composable(route = Route.MEASUREMENTS) { MeasurementsScreen() }
            composable(route = Route.SETTINGS) { SettingsScreen(navController) }
            composable(Route.USERPROFILE) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                UserProfileScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
        }

        composable(
            route = "symptoms/{feeling}",
            arguments = listOf(navArgument("feeling") { type = NavType.IntType })
        ) { backStackEntry ->
            val feelingLevel = backStackEntry.arguments!!.getInt("feeling")
            val feeling = Feeling.fromInt(feelingLevel)
            SymptomScreen(navController, feeling)
        }
    }
}