package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.ModeDao
import org.htwk.pacing.backend.database.ModeEntry
import org.htwk.pacing.backend.database.PacingDatabase
import org.koin.androidx.compose.koinViewModel

/**
 * ViewModel for managing the application's operational mode (e.g., demo mode).
 *
 * It provides a [mode] state flow that observes changes in the database and a
 * [setDemoMode] function to toggle the demo mode state.
 *
 * @param db The [PacingDatabase] instance.
 * @param modeDao The [ModeDao] for accessing mode-related data.
 */
open class ModeViewModel(
    private val db: PacingDatabase,
    private val modeDao: ModeDao
) : ViewModel() {
    /**
     * A state flow representing the current [ModeEntry] from the database.
     * Defaults to `null` initially and maintains state while there are subscribers.
     */
    open val mode = modeDao.getModeLive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Updates the demo mode status in the database.
     *
     * @param enabled `true` to enable demo mode, `false` to disable it.
     */
    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            modeDao.setMode(
                ModeEntry(
                    id = 0,
                    demo = enabled
                )
            )
        }
    }
}

/**
 * A banner component that is displayed at the top of the screen when the application
 * is in demo mode.
 *
 * The banner uses the theme's primary color as background and displays a text message
 * indicating that the demo mode is active. If the demo mode is disabled, this
 * component renders nothing.
 *
 * @param modeViewModel The [ModeViewModel] providing the demo mode state. Defaults to a Koin-injected instance.
 * @param minHeight The minimum height for the banner. Defaults to 32.dp.
 */
@Composable
fun DemoBanner(
    modeViewModel: ModeViewModel = koinViewModel(),
    minHeight: Dp = 32.dp,
) {
    val mode by modeViewModel.mode.collectAsState()
    
    // Only show the banner if demo mode is explicitly enabled
    if (mode?.demo != true) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(color = MaterialTheme.colorScheme.primary)
            .testTag("DemoBanner"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.demo_banner),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
