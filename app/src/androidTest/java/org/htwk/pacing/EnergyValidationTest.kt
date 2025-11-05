package org.htwk.pacing

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.screens.EnergyValidationCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RunWith(AndroidJUnit4::class)
class EnergyValidationTest : KoinComponent {
    @get:Rule
    val composeTestRule = createComposeRule()

    val validatedEnergyLevelDao: ValidatedEnergyLevelDao by inject()

    @Before
    fun clear() {
        runBlocking { validatedEnergyLevelDao.deleteAll() }
    }

    @Test
    fun testCorrectEnergyLevel() {
        val currentEnergy = 0.5

        composeTestRule.setContent {
            EnergyValidationCard(koinViewModel(), currentEnergy)
        }

        composeTestRule.onNodeWithTag("ValidationCorrectButton").performClick()

        composeTestRule.waitUntil { runBlocking { validatedEnergyLevelDao.getLatest() } != null }

        val entry = runBlocking { validatedEnergyLevelDao.getLatest() }
        assertEquals(Validation.Correct, entry?.validation)
        assertEquals(currentEnergy, entry?.percentage?.toDouble())
    }

    @Test
    fun testAdjustEnergyLevel() {
        val adjustedEnergy = 1.0

        composeTestRule.setContent {
            EnergyValidationCard(koinViewModel(), 0.5)
        }

        val adjustButton = composeTestRule.onNodeWithTag("ValidationAdjustButton")
        val adjustTextField = composeTestRule.onNodeWithTag("ValidationAdjustTextField")
        val saveButton = composeTestRule.onNodeWithTag("ValidationAdjustSaveButton")

        adjustButton.performClick()

        composeTestRule.waitUntil {
            adjustTextField.fetchSemanticsNode().config.any { (key, value) ->
                key.name == "IsEditable" && value == true
            }
        }

        adjustTextField.performTextReplacement(adjustedEnergy.toString())
        adjustTextField.assert(hasText(adjustedEnergy.toString()))
        saveButton.performClick()

        composeTestRule.waitUntil { runBlocking { validatedEnergyLevelDao.getLatest() } != null }

        val entry = runBlocking { validatedEnergyLevelDao.getLatest() }
        assertEquals(Validation.Adjusted, entry?.validation)
        assertEquals(adjustedEnergy, entry?.percentage?.toDouble())
    }
}