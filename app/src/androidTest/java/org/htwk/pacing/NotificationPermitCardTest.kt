package org.htwk.pacing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.htwk.pacing.ui.components.NotificationPermitCard
import org.htwk.pacing.ui.theme.PacingTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermitCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun warningSwitch_titleIsDisplayed() {
        composeTestRule.setContent {
            PacingTheme {
                NotificationPermitCard(
                    warningPermit = true,
                    onWarningChange = {},
                )
            }
        }

        // Prüfen, dass der Titel sichtbar ist
        composeTestRule
            .onNodeWithText("Warnings")
            .assertIsDisplayed()
    }

    @Test
    fun warningSwitch_callbackTriggeredOnClick() {
        var callbackValue = false

        composeTestRule.setContent {
            PacingTheme {
                NotificationPermitCard(
                    warningPermit = false,
                    onWarningChange = { callbackValue = it },
                )
            }
        }

        // Klicke auf den Text (oder nahe Umgebung), löst Switch aus
        composeTestRule
            .onNodeWithText("Warnings")
            .performClick()

        // Callback muss true sein
        assertTrue(callbackValue)
    }
}
