package org.htwk.pacing

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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.htwk.pacing.backend.database.ModeEntry
import org.junit.Rule
import org.junit.Test

class DemoBannerTest {
    @get:Rule
    val composeTestRule =
        createAndroidComposeRule<MainActivity>()

    @Test
    fun demoBanner_isVisible_whenDemoModeIsTrue() {
        // GIVEN
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = true)
        )

        // WHEN
        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        // THEN
        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertIsDisplayed()
    }

    @Test
    fun demoBanner_isNotVisible_whenDemoModeIsFalse() {
        // GIVEN
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = false)
        )

        // WHEN
        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        // THEN
        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertDoesNotExist()
    }

    @Test
    fun demoBanner_isNotVisible_whenModeIsNull() {
        // GIVEN
        val viewModel = TestModeViewModel(
            initialMode = null
        )

        // WHEN
        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        // THEN
        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertDoesNotExist()
    }

    @Test
    fun demoBanner_showsCorrectText() {
        // GIVEN
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = true)
        )

        // WHEN
        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        // THEN
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.demo_banner)
            )
            .assertIsDisplayed()
    }
}

class TestModeViewModel(
    initialMode: ModeEntry?
) : ViewModel() {


    private val _mode = MutableStateFlow<ModeEntry?>(initialMode)
    val mode: StateFlow<ModeEntry?> = _mode


    // Optional: falls du später dynamische Tests willst
    fun setDemoMode(enabled: Boolean) {
        _mode.value = ModeEntry(id = 0, demo = enabled)
    }
}


@Composable
fun TestDemoBanner(
    viewModel: TestModeViewModel,
    minHeight: Dp = 32.dp,
) {
    val mode by viewModel.mode.collectAsState()
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