package org.htwk.pacing

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.htwk.pacing.ui.components.LabelCard
import org.htwk.pacing.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabelCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext<Context>()
    private val expectedLabelText = context.getString(R.string.label_energy_level_low)

    @Test
    fun labelCard_displaysCorrectText_andIcon_forLowEnergy() {
        composeTestRule.setContent {
            LabelCard(energy = 0.2)
        }
        
        composeTestRule
            .onNodeWithContentDescription("Energy level icon")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(expectedLabelText)
            .assertExists()
            .assertIsDisplayed()
    }
}
