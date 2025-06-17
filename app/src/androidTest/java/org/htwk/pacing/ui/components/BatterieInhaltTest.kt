package org.htwk.pacing.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class BatterieInhaltTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSegmentHasWhiteColor() {
        val testColors = listOf(Color.Companion.White) + List(5) { Color.Companion.Green }

        composeTestRule.setContent {
            BatterieInhalt(segmentColors = testColors)
        }

        composeTestRule.onNodeWithTag("segment_0").assertExists()
            .assert(SemanticsMatcher.Companion.expectValue(SegmentColorKey, Color.Companion.White))
    }

    @Test
    fun testAllSixSegmentsExist() {
        val colors = List(6) { Color.Companion.Green }

        composeTestRule.setContent {
            BatterieInhalt(segmentColors = colors)
        }

        for (i in 0 until 6) {
            composeTestRule.onNodeWithTag("segment_$i").assertExists()
        }
    }
}