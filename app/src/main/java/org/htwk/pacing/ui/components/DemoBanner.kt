package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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


class ModeViewModel(
    private val db: PacingDatabase,
    private val modeDao: ModeDao
) : ViewModel() {
    val mode = modeDao.getModeLive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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

@Composable
fun DemoBanner(
    modeViewModel: ModeViewModel,
    minHeight: Dp = 32.dp,
) {
    val mode by modeViewModel.mode.collectAsState()
    if (mode?.demo != true) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Color(0xFFFF9800))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("DemoBanner"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.demo_banner),
            color = Color.White
        )
    }
}
