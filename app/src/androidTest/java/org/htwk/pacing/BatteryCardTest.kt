package org.htwk.pacing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.animateMoveTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.components.BatteryCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BatteryCardTest : KoinComponent {
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
            BatteryCard(currentEnergy, koinViewModel())
        }

        composeTestRule.onNodeWithTag("ValidationCorrectButton").performClick()

        composeTestRule.waitUntil { runBlocking { validatedEnergyLevelDao.getLatest() } != null }

        val entry = runBlocking { validatedEnergyLevelDao.getLatest() }
        assertEquals(Validation.Correct, entry?.validation)
        assertEquals(currentEnergy, entry?.percentage?.toDouble())
    }

    @Test
    fun testAdjustEnergyLevel() {
        val currentEnergy = 0.5
        val adjustedEnergy = 1.0

        composeTestRule.setContent {
            BatteryCard(currentEnergy, koinViewModel())
        }

        val adjustButton = composeTestRule.onNodeWithTag("ValidationAdjustButton")
        val batteryBar = composeTestRule.onNodeWithTag("BatteryBar")
        val saveButton = composeTestRule.onNodeWithTag("ValidationAdjustSaveButton")

        adjustButton.performClick()

        batteryBar.performMouseInput {
            moveTo(Offset(width * currentEnergy.toFloat(), centerY))
            press()
            animateMoveTo(Offset(width * adjustedEnergy.toFloat(), centerY))
            release()
        }
        saveButton.performClick()

        composeTestRule.waitUntil { runBlocking { validatedEnergyLevelDao.getLatest() } != null }

        val entry = runBlocking { validatedEnergyLevelDao.getLatest() }
        assertEquals(Validation.Adjusted, entry?.validation)
        assertEquals(adjustedEnergy, entry?.percentage?.toDouble())
    }
}