package org.htwk.pacing.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import org.htwk.pacing.ui.screens.FeedbackScreen
import org.htwk.pacing.ui.screens.SymptomScreen
import org.htwk.pacing.ui.screens.UserProfileScreen
import org.htwk.pacing.ui.screens.NotificationsScreen
import org.htwk.pacing.ui.screens.InformationScreen
import org.htwk.pacing.ui.screens.DataScreen
import org.htwk.pacing.ui.screens.AppearanceScreen
import org.htwk.pacing.ui.screens.ServicesScreen
import org.htwk.pacing.ui.screens.UserProfileViewModel
import org.htwk.pacing.ui.screens.settings.ConnectionsAndServicesScreen
import org.htwk.pacing.ui.theme.PacingTheme

@Composable
fun Main() {
    val userProfileViewModel: UserProfileViewModel = koinViewModel()
    val themeMode by userProfileViewModel.themeMode.collectAsState()

    // Determine dark theme based on user preference
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme() // AUTO or default
    }

    PacingTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val parentRoute = navBackStackEntry.value?.destination?.parent?.route
        val selectedDestination = rememberSaveable { mutableIntStateOf(0) }
        val snackbarHostState = remember { SnackbarHostState() }


        if (parentRoute == "main_nav") {
            Scaffold(
                bottomBar = { NavBar(navController, selectedDestination) },
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        snackbar = { data ->
                            Snackbar(data, shape = RoundedCornerShape(10.dp))
                        })
                }
            ) { contentPadding ->
                AppNavHost(
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize()
                )
            }
        } else {
            AppNavHost(
                navController = navController,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.fillMaxSize()
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
    const val CONNECTIONS_AND_SERVICES = "settings/connections_and_services"
    const val USERPROFILE = "userprofile"
    const val SERVICES = "services"
    const val FEEDBACK = "feedback"
    const val DATA = "data"
    const val NOTIFICATIONS = "notifications"
    const val APPEAREANCE = "appeareance"
    const val INFORMATION = "information"
    fun symptoms(feeling: Feeling) = "symptoms/${feeling.level}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    NavHost(
        navController,
        startDestination = "main_nav",
        modifier = modifier
    ) {
        navigation(route = "main_nav", startDestination = Route.HOME) {
            composable(route = Route.HOME) {
                HomeScreen(
                    navController = navController,
                    snackbarHostState = snackbarHostState
                )
            }
            composable(route = Route.MEASUREMENTS) { MeasurementsScreen() }
            composable(route = Route.SETTINGS) { SettingsScreen(navController) }
            composable(Route.USERPROFILE) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                UserProfileScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
            composable(Route.SERVICES) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                ServicesScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
            composable(Route.FEEDBACK) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                FeedbackScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
            composable(Route.DATA) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                val settingsViewModel: org.htwk.pacing.ui.screens.SettingsViewModel = koinViewModel()
                DataScreen(
                    navController = navController,
                    viewModel = userProfileViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
            composable(Route.NOTIFICATIONS) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                NotificationsScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
            composable(Route.APPEAREANCE) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                AppearanceScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
            composable(Route.INFORMATION) {
                val userProfileViewModel: UserProfileViewModel = koinViewModel()
                InformationScreen(
                    navController = navController,
                    viewModel = userProfileViewModel
                )
            }
        }

        composable(route = Route.CONNECTIONS_AND_SERVICES) {
            ConnectionsAndServicesScreen(navController)
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
}