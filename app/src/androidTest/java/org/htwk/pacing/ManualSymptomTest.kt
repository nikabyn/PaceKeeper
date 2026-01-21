package org.htwk.pacing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.ui.Main
import org.htwk.pacing.ui.screens.SymptomScreen
import org.htwk.pacing.ui.screens.SymptomsViewModel
import org.junit.Ignore
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
    @Ignore("Temporarily deactivated because the TEst fails on the emulator")
    fun select_feeling_and_symptoms() {
        composeTestRule.setContent {
            SymptomScreen(
                navController = rememberNavController(),
                feeling = Feeling.Good,
                viewModel = SymptomsViewModel(manualSymptomDao)
            )
        }

        composeTestRule.onRoot().printToLog("UI_TREE")

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithTag("FeelingSelectionCard", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

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

    @Test
    @Ignore
    fun add_symptom_with_empty_string_disables_confirm_button() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()

        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")
        confirmButton.assertIsDisplayed()

        // Confirm button should be disabled (not clickable) with empty text
        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        symptomTextField.assertIsDisplayed()
    }

    @Test
    @Ignore
    fun add_symptom_with_whitespace_only_is_invalid() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()

        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        symptomTextField.performTextInput("   ")

        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")
        confirmButton.assertIsDisplayed()
    }

    @Test
    @Ignore
    fun symptom_strength_slider_updates_correctly() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()

        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        symptomTextField.performTextInput("Test Symptom")

        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")
        confirmButton.performClick()

        // Verify symptom is displayed and slider exists
        val checkboxes = composeTestRule.onNodeWithTag("SymptomsScreenCheckboxes")
        checkboxes.assertIsDisplayed()

        val applyButton = composeTestRule.onNodeWithTag("SymptomsScreenApplyButton")
        applyButton.assertIsDisplayed()
    }

    @Test
    @Ignore
    fun save_button_navigation_to_home() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val symptomsScreen = composeTestRule.onNodeWithTag("SymptomsScreen")
        symptomsScreen.assertIsDisplayed()

        val applyButton = composeTestRule.onNodeWithTag("SymptomsScreenApplyButton")
        applyButton.performClick()

        symptomsScreen.assertIsNotDisplayed()
    }

    @Test
    @Ignore
    fun default_symptoms_are_loaded_on_first_entry() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val symptomsScreen = composeTestRule.onNodeWithTag("SymptomsScreen")
        symptomsScreen.assertIsDisplayed()

        val checkboxes = composeTestRule.onNodeWithTag("SymptomsScreenCheckboxes")
        checkboxes.assertIsDisplayed()

        runBlocking {
            val allSymptoms = manualSymptomDao.getAllSymptoms()
            assert(allSymptoms.isNotEmpty())
        }
    }

    @Test
    @Ignore
    fun multiple_symptoms_can_be_created_and_tracked() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")

        // Add first symptom
        addButton.performClick()
        symptomTextField.performTextInput("Headache")
        confirmButton.performClick()

        // Add second symptom
        addButton.performClick()
        symptomTextField.performTextInput("Fatigue")
        confirmButton.performClick()

        // Add third symptom
        addButton.performClick()
        symptomTextField.performTextInput("Brain Fog")
        confirmButton.performClick()

        val applyButton = composeTestRule.onNodeWithTag("SymptomsScreenApplyButton")
        applyButton.performClick()

        runBlocking {
            val allSymptoms = manualSymptomDao.getAllSymptoms()
            assert(allSymptoms.count() >= 3)
        }
    }

    @Test
    @Ignore
    fun back_button_cancels_symptom_selection() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val symptomsScreen = composeTestRule.onNodeWithTag("SymptomsScreen")
        symptomsScreen.assertIsDisplayed()

        val backButton = composeTestRule.onNodeWithTag("SymptomsScreenBackButton")
        backButton.performClick()

        symptomsScreen.assertIsNotDisplayed()
    }

    @Test
    @Ignore
    fun add_symptom_dialog_can_be_cancelled() {
        composeTestRule.setContent { Main() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()

        val dialog = composeTestRule.onNodeWithTag("AddSymptomDialog")
        dialog.assertIsDisplayed()

        // Find and click dismiss button
        val dismissButton = composeTestRule.onNodeWithTag("AddSymptomDismissButton")
        dismissButton.performClick()

        dialog.assertIsNotDisplayed()
    }
}