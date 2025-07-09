package org.htwk.pacing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.ui.Main
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RunWith(AndroidJUnit4::class)
class ManualSymptomTest : KoinComponent {
    @get:Rule
    val composeTestRule = createComposeRule()

    val manualSymptomDao: ManualSymptomDao by inject()

    @Test
    fun select_feeling_and_symptoms() {
        composeTestRule.setContent { Main() }

        composeTestRule.onRoot().printToLog("UI_TREE")

        composeTestRule.onNodeWithTag("FeelingSelectionCard").assertIsDisplayed()

        val symptomsScreen = composeTestRule.onNodeWithTag("SymptomsScreen")
        symptomsScreen.assertIsNotDisplayed()

        val feelingButtons = listOf(
            composeTestRule.onNodeWithTag("VeryBadButton"),
            composeTestRule.onNodeWithTag("BadButton"),
            composeTestRule.onNodeWithTag("GoodButton"),
            composeTestRule.onNodeWithTag("VeryGoodButton")
        )
        val backButton = composeTestRule.onNodeWithTag("SymptomsScreenBackButton")
        val applyButton = composeTestRule.onNodeWithTag("SymptomsScreenApplyButton")
        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        val symptomConfirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")

        for (feelingButton in feelingButtons) {
            feelingButton.assertIsDisplayed()
            feelingButton.performClick()
            symptomsScreen.assertIsDisplayed()
            backButton.performClick()
            symptomsScreen.assertIsNotDisplayed()
        }

        for (indexed in feelingButtons.withIndex()) {
            val feelingButton = indexed.value
            feelingButton.assertIsDisplayed()
            feelingButton.performClick()
            addButton.performClick()
            symptomTextField.performTextInput("Symptom ${indexed.index}")
            symptomConfirmButton.performClick()
            applyButton.performClick()
            symptomsScreen.assertIsNotDisplayed()
        }

        val checkboxes = composeTestRule.onNodeWithTag("SymptomsScreenCheckboxes")
        feelingButtons[3].performClick()
        for (i in 0..3) {
            checkboxes.onChildAt(i).performClick()
        }

        runBlocking {
            assert(manualSymptomDao.getAllSymptoms().count() == 4)

            val expectedFeelings = listOf(
                Feeling.VeryBad, Feeling.Bad, Feeling.Good, Feeling.VeryGood, Feeling.VeryGood
            )
            val expectedSymptoms = listOf(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                listOf("Symptom 0", "Symptom 1", "Symptom 2", "Symptom 3")
            )
            manualSymptomDao.getAll().forEachIndexed { i, entry ->
                assert(entry.feeling.feeling == expectedFeelings[i])
                assert(entry.symptoms == expectedSymptoms[i])
            }
        }
    }
}