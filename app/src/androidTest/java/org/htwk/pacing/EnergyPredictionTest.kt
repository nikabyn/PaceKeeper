package org.htwk.pacing

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.Clock
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.Series
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours

@RunWith(AndroidJUnit4::class)
class EnergyPredictionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun energyPredictionCard_displays() {
        val now = Clock.System.now()
        val series = Series(
            listOf(
                now - 12.hours,
                now - 6.hours,
                now - 2.hours,
                now
            ).map { it.toEpochMilliseconds().toDouble() },
            listOf(0.8, 0.65, 0.4),
        )

        composeTestRule.setContent {
            EnergyPredictionCard(
                series,
                minPrediction = 0.1f,
                avgPrediction = 0.3f,
                maxPrediction = 0.6f
            )
        }

        composeTestRule.onNodeWithTag("EnergyPredictionErrorText")
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag("Card")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(300.dp)
    }


    @Test
    fun energyPredictionCard_displaysErrorWhenEmpty() {
        val series = Series(emptyList(), emptyList())

        composeTestRule.setContent {
            EnergyPredictionCard(
                series,
                minPrediction = 0f,
                avgPrediction = 0.5f,
                maxPrediction = 1f
            )
        }

        composeTestRule.onNodeWithTag("EnergyPredictionErrorText")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("Card")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(300.dp)
    }
}