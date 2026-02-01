package org.htwk.pacing.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.htwk.pacing.R
import org.htwk.pacing.backend.data_collection.fitbit.Fitbit
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.ui.screens.AppearanceScreen
import org.htwk.pacing.ui.screens.DataScreen
import org.htwk.pacing.ui.screens.FeedbackScreen
import org.htwk.pacing.ui.screens.HomeScreen
import org.htwk.pacing.ui.screens.InformationScreen
import org.htwk.pacing.ui.screens.NotificationScreen
import org.htwk.pacing.ui.screens.SettingsScreen
import org.htwk.pacing.ui.screens.SymptomScreen
import org.htwk.pacing.ui.screens.UserProfileScreen
import org.htwk.pacing.ui.screens.UserProfileViewModel
import org.htwk.pacing.ui.screens.WelcomeScreen
import org.htwk.pacing.ui.screens.WelcomeViewModel
import org.htwk.pacing.ui.screens.measurements.Measurement
import org.htwk.pacing.ui.screens.measurements.MeasurementScreen
import org.htwk.pacing.ui.screens.measurements.MeasurementsScreen
import org.htwk.pacing.ui.screens.settings.ConnectionsAndServicesScreen
import org.htwk.pacing.ui.screens.settings.FitbitScreen
import org.htwk.pacing.ui.theme.PacingTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun Main() {
    val userProfileViewModel: UserProfileViewModel = koinViewModel()
    val themeMode by userProfileViewModel.themeMode.collectAsState()

    val welcomeViewModel: WelcomeViewModel = koinViewModel()
    val checkedIn by welcomeViewModel.checkedIn.collectAsState()

    val navController = rememberNavController()
    val selectedDestination = rememberSaveable { mutableIntStateOf(0) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val navBarVisible by remember {
        derivedStateOf {
            when (navBackStackEntry?.destination?.route) {
                Route.HOME,
                Route.MEASUREMENTS,
                Route.SETTINGS,
                null -> true

                else -> false
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val startDestination = rememberSaveable(checkedIn) {
        when (checkedIn) {
            false -> Route.WELCOME
            else -> Route.MAIN_NAV
        }
    }

    PacingTheme(darkTheme = darkTheme) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = navBarVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    NavBar(
                        navController = navController,
                        selectedDestination = selectedDestination
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { data -> Snackbar(data, shape = RoundedCornerShape(10.dp)) }
                )
            }
        ) { contentPadding ->
            AppNavHost(
                navController = navController,
                startDestination = startDestination,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .fillMaxSize()
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
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
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
    const val WELCOME = "welcome"
    const val MAIN_NAV = "main_nav"

    const val HOME = "home"
    fun symptoms(feeling: Feeling) = "symptoms/${feeling.level}"

    const val MEASUREMENTS = "measurements"
    fun measurement(measurement: Measurement) = "measurements/${measurement.ordinal}"

    const val SETTINGS = "settings"
    const val USERPROFILE = "settings/userprofile"
    const val CONNECTIONS_AND_SERVICES = "settings/connections_and_services"
    const val FITBIT = "settings/connections_and_services/fitbit"
    const val FEEDBACK = "settings/feedback"
    const val DATA = "settings/data"
    const val NOTIFICATIONS = "settings/notifications"
    const val APPEARANCE = "settings/appearance"
    const val INFORMATION = "settings/information"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val subScreenEntry = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(300)
    )
    val subScreenExit = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(300)
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        composable(Route.WELCOME) {
            val viewModel: WelcomeViewModel = koinViewModel()
            WelcomeScreen(
                onFinished = {
                    viewModel.completeOnboarding()
                    navController.navigate(Route.MAIN_NAV) {
                        popUpTo(Route.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        navigation(route = Route.MAIN_NAV, startDestination = Route.HOME) {

            composable(Route.HOME) { HomeScreen(navController, snackbarHostState) }
            composable(
                route = "symptoms/{feeling}",
                arguments = listOf(navArgument("feeling") { type = NavType.IntType })
            ) { backStackEntry ->
                val feelingLevel = backStackEntry.arguments!!.getInt("feeling")
                val feeling = Feeling.fromInt(feelingLevel)
                SymptomScreen(navController, feeling)
            }

            composable(Route.MEASUREMENTS) { MeasurementsScreen(navController) }
            composable(
                route = "measurements/{measurement}",
                arguments = listOf(navArgument("measurement") { type = NavType.IntType })
            ) { backStackEntry ->
                val measurementOrdinal = backStackEntry.arguments!!.getInt("measurement")
                val measurement = Measurement.entries.first { it.ordinal == measurementOrdinal }
                MeasurementScreen(navController, measurement)
            }

            composable(Route.SETTINGS) { SettingsScreen(navController) }

            composable(
                Route.USERPROFILE,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { UserProfileScreen(navController) }

            composable(
                Route.CONNECTIONS_AND_SERVICES,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { ConnectionsAndServicesScreen(navController) }

            composable(
                Route.FEEDBACK,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { FeedbackScreen(navController) }

            composable(
                Route.DATA,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { DataScreen(navController) }

            composable(
                Route.NOTIFICATIONS,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { NotificationScreen(navController) }

            composable(
                Route.APPEARANCE,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { AppearanceScreen(navController) }

            composable(
                Route.INFORMATION,
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { InformationScreen(navController) }

            composable(
                route = Route.FITBIT,
                deepLinks = listOf(navDeepLink { uriPattern = Fitbit.redirectUri.toString() }),
                enterTransition = { subScreenEntry },
                exitTransition = { subScreenExit }
            ) { backStackEntry ->
                val deepLinkIntent = backStackEntry.arguments?.getParcelableCompat<Intent>(
                    NavController.KEY_DEEP_LINK_INTENT
                )

                val fitbitOauthUri = deepLinkIntent?.data
                    ?.takeIf { uri -> uri.authority == "fitbit_oauth2_redirect" }
                    ?.also { uri -> Log.d("AppNavHost", "Received Fitbit OAuth redirect = $uri") }

                FitbitScreen(navController, fitbitOauthUri)
            }
        }
    }
}

private inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
