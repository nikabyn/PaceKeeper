package org.htwk.pacing

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.htwk.pacing.ui.components.LabelCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabelCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun labelCard_displaysCorrectText_andIcon_forLowEnergy() {
        val expectedLabel = context.getString(R.string.label_energy_level_low)

        composeTestRule.setContent {
            LabelCard(energy = 0.2) // Low Energy → energyLevel = 1
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.label_energy_icon_name))
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(expectedLabel)
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun labelCard_displaysCorrectText_andIcon_forModerateEnergy() {
        val expectedLabel = context.getString(R.string.label_energy_level_moderate)

        composeTestRule.setContent {
            LabelCard(energy = 0.5) // Moderate Energy → energyLevel = 3
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.label_energy_icon_name))
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(expectedLabel)
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun labelCard_displaysCorrectText_andIcon_forHighEnergy() {
        val expectedLabel = context.getString(R.string.label_energy_level_high)

        composeTestRule.setContent {
            LabelCard(energy = 0.8) // High Energy → energyLevel = 4
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.label_energy_icon_name))
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(expectedLabel)
            .assertExists()
            .assertIsDisplayed()
    }
}
