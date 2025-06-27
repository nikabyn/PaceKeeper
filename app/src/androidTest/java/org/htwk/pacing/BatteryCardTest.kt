package org.htwk.pacing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.htwk.pacing.ui.components.BatteryCard
import org.junit.Rule
import org.junit.Test

class BatteryCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBatteryRendersBars() {
        composeTestRule.setContent {
            BatteryCard(0.5)
        }

        val bars = composeTestRule.onAllNodesWithTag("BatteryCardBar")
        bars.assertCountEquals(6)
        bars.fetchSemanticsNodes().forEachIndexed { index, _ ->
            bars[index]
                .assertExists()
                .assertIsDisplayed()
        }
    }
}