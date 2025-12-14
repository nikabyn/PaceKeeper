package org.htwk.pacing.ui.screens.settings

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.OAuth2Provider
import org.htwk.pacing.backend.OAuth2Result
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel


@Composable
fun FitbitScreen(
    navController: NavController,
    fitbitOauthUri: Uri? = null,
    viewModel: FitbitViewModel = koinViewModel(),
) {
    if (fitbitOauthUri != null) {
        viewModel.onFitbitOAuth2Redirect(fitbitOauthUri)
    }

    val context = LocalContext.current
    val isFitbitConnected by viewModel.isFitbitConnected.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshTrigger.emit(Unit)
        }
    }

    SettingsSubScreen(
        title = stringResource(R.string.fitbit),
        navController = navController,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.large,
                vertical = Spacing.extraLarge
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            ConnectionCard(
                name = stringResource(R.string.fitbit),
                tip = "Login with Fitbit Account",
                connected = isFitbitConnected,
                iconId = R.drawable.fitbit,
                onClick = {
                    viewModel.fitbitOAuth.startLogin(
                        context,
                        FitbitViewModel.scopes.toList()
                    )
                },
            )
        }
    }
}

class FitbitViewModel(
    private val db: PacingDatabase,
    val fitbitOAuth: OAuth2Provider,
) : ViewModel() {
    companion object {
        private const val TAG = "FitbitViewModel"

        val scopes = arrayOf(
            "activity",
            "cardio_fitness",
            "electrocardiogram",
            "heartrate",
            "irregular_rhythm_notifications",
            "location",
            "nutrition",
            "oxygen_saturation",
            "respiratory_rate",
            "sleep",
            "temperature",
            "weight",
        )
    }

    val refreshTrigger = MutableStateFlow(Unit)

    fun onFitbitOAuth2Redirect(uri: Uri) {
        viewModelScope.launch {
            val loginResult = fitbitOAuth.completeLogin(uri)
            val tokenResponse = when (loginResult) {
                is OAuth2Result.TokenResponse -> loginResult

                is OAuth2Result.RedirectUriError -> {
                    Log.e(TAG, "Invalid redirect uri, ${loginResult.name}: $uri")
                    return@launch
                }

                is OAuth2Result.HttpError -> {
                    Log.e(TAG, "Error in http request, ${loginResult.status}: ${loginResult.body}")
                    return@launch
                }
            }

            // Store tokens in database
            val newProfile = db.userProfileDao()
                .getProfile()
                ?.copy(fitbitTokenResponse = tokenResponse)
                ?: error("Unreachable: Database must always have a user profile")
            db.userProfileDao().insertOrUpdate(newProfile)
        }
    }

    val isFitbitConnected = db.userProfileDao()
        .getProfileLive()
        .map { userProfile ->
            // TODO: ping fitbit to check whether token is still valid
            userProfile?.fitbitTokenResponse != null
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            false
        )
}