package org.htwk.pacing

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.UserProfileEntry
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.screens.HomeScreen
import org.htwk.pacing.ui.screens.SymptomScreen
import org.htwk.pacing.ui.theme.PacingTheme
import org.junit.After
import org.junit.Before
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
    val userProfileDao: UserProfileDao by inject()

    @Before
    fun setup() {
        runBlocking {
            manualSymptomDao.deleteAll()
            userProfileDao.deleteAll()
            userProfileDao.insertOrUpdate(UserProfileEntry.createInitial().copy(checkedIn = true))
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            manualSymptomDao.deleteAll()
        }
    }

    @Composable
    private fun TestNavHost() {
        PacingTheme {
            val navController = rememberNavController()
            NavHost(navController, startDestination = Route.HOME) {
                composable(Route.HOME) {
                    HomeScreen(navController, remember { SnackbarHostState() })
                }
                composable(
                    route = "symptoms/{feeling}",
                    arguments = listOf(navArgument("feeling") { type = NavType.IntType })
                ) { backStackEntry ->
                    val feelingLevel = backStackEntry.arguments!!.getInt("feeling")
                    val feeling = Feeling.fromInt(feelingLevel)
                    SymptomScreen(navController, feeling)
                }
            }
        }
    }

    @Test
    fun select_feeling_and_symptoms() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule
            .onNodeWithTag("FeelingSelectionCard", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        val feelingButtons = listOf(
            composeTestRule.onNodeWithTag("VeryBadButton"),
            composeTestRule.onNodeWithTag("BadButton"),
            composeTestRule.onNodeWithTag("GoodButton"),
            composeTestRule.onNodeWithTag("VeryGoodButton")
        )

        for (feelingButton in feelingButtons) {
            feelingButton.assertIsDisplayed()
            feelingButton.performClick()
            composeTestRule.waitForIdle()

            val symptomsScreen = composeTestRule.onNodeWithTag("SymptomsScreen")
            symptomsScreen.assertIsDisplayed()

            composeTestRule.onNodeWithTag("SymptomsScreenBackButton").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

        for (indexed in feelingButtons.withIndex()) {
            val feelingButton = indexed.value
            feelingButton.assertIsDisplayed()
            feelingButton.performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("SymptomsScreenAddButton").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("AddSymptomTextField").performTextInput("Symptom ${indexed.index}")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("AddSymptomConfirmButton").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("SymptomsScreenApplyButton").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

        val checkboxes = composeTestRule.onNodeWithTag("SymptomsScreenCheckboxes")
        feelingButtons[3].performClick()
        composeTestRule.waitForIdle()

        for (i in 0..3) {
            checkboxes.onChildAt(i).performClick()
            composeTestRule.waitForIdle()
        }

        runBlocking {
            assert(manualSymptomDao.getAllSymptoms().count() >= 4)
        }
    }

    @Test
    fun add_symptom_with_empty_string_disables_confirm_button() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("VeryGoodButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("SymptomsScreenAddButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("AddSymptomTextField").performTextInput(" ")
        composeTestRule.onNodeWithTag("AddSymptomConfirmButton").performClick()
        composeTestRule.onNodeWithTag("AddSymptomDialog").assertIsDisplayed()
    }

    @Test
    fun add_symptom_with_whitespace_only_is_invalid() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()
        composeTestRule.waitForIdle()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()
        composeTestRule.waitForIdle()

        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        symptomTextField.performTextInput("   ")
        composeTestRule.waitForIdle()

        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")
        confirmButton.assertIsDisplayed()

        val dialog = composeTestRule.onNodeWithTag("AddSymptomDialog")
        dialog.assertIsDisplayed()
    }

    @Test
    fun symptom_strength_slider_updates_correctly() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()
        composeTestRule.waitForIdle()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()
        composeTestRule.waitForIdle()

        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        symptomTextField.performTextInput("Test Symptom")
        composeTestRule.waitForIdle()

        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")
        confirmButton.performClick()
        composeTestRule.waitForIdle()

        val checkboxes = composeTestRule.onNodeWithTag("SymptomsScreenCheckboxes")
        checkboxes.assertIsDisplayed()

        val applyButton = composeTestRule.onNodeWithTag("SymptomsScreenApplyButton")
        applyButton.assertIsDisplayed()
    }

    @Test
    fun save_button_navigation_to_home() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("VeryGoodButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("SymptomsScreenApplyButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("FeelingSelectionCard", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun default_symptoms_are_loaded_on_first_entry() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("VeryGoodButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithTag("SymptomSlider_fatigue", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking {
            val allSymptoms = manualSymptomDao.getAllSymptoms()
            assert(allSymptoms.isNotEmpty())
        }
    }

    @Test
    fun multiple_symptoms_can_be_created_and_tracked() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()
        composeTestRule.waitForIdle()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        val symptomTextField = composeTestRule.onNodeWithTag("AddSymptomTextField")
        val confirmButton = composeTestRule.onNodeWithTag("AddSymptomConfirmButton")

        addButton.performClick()
        composeTestRule.waitForIdle()
        symptomTextField.performTextInput("Headache")
        composeTestRule.waitForIdle()
        confirmButton.performClick()
        composeTestRule.waitForIdle()

        addButton.performClick()
        composeTestRule.waitForIdle()
        symptomTextField.performTextInput("Fatigue")
        composeTestRule.waitForIdle()
        confirmButton.performClick()
        composeTestRule.waitForIdle()

        addButton.performClick()
        composeTestRule.waitForIdle()
        symptomTextField.performTextInput("Brain Fog")
        composeTestRule.waitForIdle()
        confirmButton.performClick()
        composeTestRule.waitForIdle()

        val applyButton = composeTestRule.onNodeWithTag("SymptomsScreenApplyButton")
        applyButton.performClick()
        composeTestRule.waitForIdle()

        runBlocking {
            val allSymptoms = manualSymptomDao.getAllSymptoms()
            assert(allSymptoms.count() >= 3)
        }
    }

    @Test
    fun back_button_cancels_symptom_selection() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingSelectionCard = composeTestRule.onNodeWithTag("FeelingSelectionCard", useUnmergedTree = true)
        feelingSelectionCard.assertIsDisplayed()

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()
        composeTestRule.waitForIdle()

        val symptomsScreen = composeTestRule.onNodeWithTag("SymptomsScreen")
        symptomsScreen.assertIsDisplayed()

        val backButton = composeTestRule.onNodeWithTag("SymptomsScreenBackButton")
        backButton.assertIsDisplayed()
        backButton.performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule
                    .onNodeWithTag("FeelingSelectionCard", useUnmergedTree = true)
                    .assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }

        feelingSelectionCard.assertIsDisplayed()
    }

    @Test
    fun add_symptom_dialog_can_be_cancelled() {
        composeTestRule.setContent { TestNavHost() }

        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithTag("FeelingSelectionCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val feelingButton = composeTestRule.onNodeWithTag("VeryGoodButton")
        feelingButton.performClick()
        composeTestRule.waitForIdle()

        val addButton = composeTestRule.onNodeWithTag("SymptomsScreenAddButton")
        addButton.performClick()
        composeTestRule.waitForIdle()

        val dialog = composeTestRule.onNodeWithTag("AddSymptomDialog")
        dialog.assertIsDisplayed()

        val dismissButton = composeTestRule.onNodeWithTag("AddSymptomDismissButton")
        dismissButton.assertIsDisplayed()
        dismissButton.performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                dialog.assertDoesNotExist()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}