package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.htwk.pacing.MainActivity
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.ModeEntry
import org.junit.Rule
import org.junit.Test

class DemoBannerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoBanner_isVisible_whenDemoModeIsTrue() {
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = true)
        )

        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertIsDisplayed()
    }

    @Test
    fun demoBanner_isNotVisible_whenDemoModeIsFalse() {
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = false)
        )

        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertDoesNotExist()
    }
}

class TestModeViewModel(
    initialMode: ModeEntry?
) : ViewModel() {


    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<ModeEntry?> = _mode
}

@Composable
fun TestDemoBanner(viewModel: TestModeViewModel) {
    val mode by viewModel.mode.collectAsState()


    if (mode?.demo != true) return


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFF9800))
            .padding(8.dp)
            .testTag("DemoBanner"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.demo_banner),
            color = Color.White
        )
    }
}

