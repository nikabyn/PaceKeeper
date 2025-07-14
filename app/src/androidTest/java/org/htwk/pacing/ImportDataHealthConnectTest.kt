package org.htwk.pacing.ui.components

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.htwk.pacing.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportDataHealthConnectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    val context: Context = ApplicationProvider.getApplicationContext<Context>()
    val selectFileText = context.getString(R.string.select_file)
    val startImportText = context.getString(R.string.import_start)

    @Test
    fun importDataComponent_displaysButtonsAndText() {
        composeTestRule.setContent {
            ImportDataHealthConnect()
        }

        composeTestRule.onAllNodesWithText(selectFileText)
            .assertCountEquals(1)
            .onFirst()
            .assertIsDisplayed()

        composeTestRule.onAllNodesWithText(startImportText)
            .assertCountEquals(1)
            .onFirst()
            .assertIsDisplayed()
    }
}
