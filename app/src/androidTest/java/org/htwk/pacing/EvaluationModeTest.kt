package org.htwk.pacing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.ModeDao
import org.htwk.pacing.backend.database.ModeEntry
import org.htwk.pacing.ui.components.StartEvaluationMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EvaluationModeTest : KoinComponent {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val modeDao: ModeDao by inject()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        runBlocking {
            // Sicherstellen, dass wir standardmäßig nicht im Demo-Modus starten
            modeDao.setMode(ModeEntry(id = 0, demo = false))
        }
    }

    @Test
    fun testInitialState_ShowsStartButton() {
        composeTestRule.setContent {
            StartEvaluationMode(koinViewModel())
        }

        val startText = context.getString(R.string.demo_start_button_text)
        composeTestRule.onNodeWithText(startText).assertIsDisplayed()
    }

    @Test
    fun testClickButton_OpensConfirmationDialog() {
        composeTestRule.setContent {
            StartEvaluationMode(koinViewModel())
        }

        val startText = context.getString(R.string.demo_start_button_text)
        composeTestRule.onNodeWithText(startText).performClick()

        val dialogTitle = context.getString(R.string.demo_data_dialog_title)
        composeTestRule.onNodeWithText(dialogTitle).assertIsDisplayed()
    }

    @Test
    fun testDialogCancel_ClosesDialog() {
        composeTestRule.setContent {
            StartEvaluationMode(koinViewModel())
        }

        // Dialog öffnen
        val startText = context.getString(R.string.demo_start_button_text)
        composeTestRule.onNodeWithText(startText).performClick()

        // Abbrechen klicken
        val cancelText = context.getString(R.string.cancel)
        composeTestRule.onNodeWithText(cancelText).performClick()

        // Prüfen, ob der Dialog geschlossen wurde
        val dialogTitle = context.getString(R.string.demo_data_dialog_title)
        composeTestRule.onNodeWithText(dialogTitle).assertDoesNotExist()
    }

    @Test
    fun testDemoEnabled_ShowsEndButton() {
        runBlocking {
            // Modus manuell auf Demo setzen
            modeDao.setMode(ModeEntry(id = 0, demo = true))
        }

        composeTestRule.setContent {
            StartEvaluationMode(koinViewModel())
        }

        val endText = context.getString(R.string.demo_end_button_text)
        composeTestRule.onNodeWithText(endText).assertIsDisplayed()
    }
}
