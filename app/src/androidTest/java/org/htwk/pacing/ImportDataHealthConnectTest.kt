package org.htwk.pacing.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportDataHealthConnectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun importDataComponent_displaysButtonsAndText() {
        composeTestRule.setContent {
            ImportDataHealthConnect()
        }

        composeTestRule.onAllNodesWithText("Datei auswählen")
            .assertCountEquals(1)
            .onFirst()
            .assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Import starten")
            .assertCountEquals(1)
            .onFirst()
            .assertIsDisplayed()
    }
}
