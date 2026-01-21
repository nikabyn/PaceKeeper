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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.ModeDao
import org.htwk.pacing.backend.database.ModeEntry
import org.koin.androidx.compose.koinViewModel


class ModeViewModel(
    private val modeDao: ModeDao
) : ViewModel() {

    private val _mode = MutableStateFlow<ModeEntry?>(null)
    val mode: StateFlow<ModeEntry?> = _mode.asStateFlow()

    init {
        loadMode()
    }

    private fun loadMode() {
        viewModelScope.launch {
            _mode.value = modeDao.getMode()
        }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            val newMode = ModeEntry(
                id = 0,
                demo = enabled
            )
            modeDao.setMode(newMode)
            _mode.value = newMode
        }
    }
}

@Composable
fun DemoBanner(
    modeViewModel: ModeViewModel = koinViewModel(),
    minHeight: Dp = 32.dp,
) {
    modeViewModel.setDemoMode(true)
    val mode by modeViewModel.mode.collectAsState()
    if (mode?.demo != true) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Color(0xFFFF9800))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.demo_banner),
            color = Color.White
        )
    }
}
