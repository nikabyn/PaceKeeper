package org.htwk.pacing

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.ui.components.EnergyPredictionCard
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
        val data = listOf(
            PredictedEnergyLevelEntry(
                time = now - 12.hours,
                percentageNow = Percentage.fromDouble(0.8),
                timeFuture = now,
                percentageFuture = Percentage.fromDouble(0.0)
            ),
            PredictedEnergyLevelEntry(
                time = now - 6.hours,
                percentageNow = Percentage.fromDouble(0.65),
                timeFuture = now,
                percentageFuture = Percentage.fromDouble(0.0)
            ),
            PredictedEnergyLevelEntry(
                time = now - 2.hours,
                percentageNow = Percentage.fromDouble(0.4),
                timeFuture = now,
                percentageFuture = Percentage.fromDouble(0.0)
            ),
            PredictedEnergyLevelEntry(
                time = now,
                percentageNow = Percentage.fromDouble(0.2),
                timeFuture = now,
                percentageFuture = Percentage.fromDouble(0.0)
            ),
        )

        composeTestRule.setContent {
            EnergyPredictionCard(
                data,
                currentEnergy = 0.5f,
                minPrediction = 0.1f,
                avgPrediction = 0.3f,
                maxPrediction = 0.6f,
                modifier = Modifier.height(300.dp),
            )
        }

        composeTestRule.onNodeWithTag("EnergyPredictionErrorText")
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag("Card")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(300.dp)
    }
}