package org.htwk.pacing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import org.htwk.pacing.backend.database.ModeDao
import org.htwk.pacing.backend.database.ModeEntry
import org.htwk.pacing.ui.components.DemoBanner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DemoBannerTest : KoinComponent {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val modeDao: ModeDao by inject()

    @Before
    fun setup() {
        runBlocking {
            modeDao.setMode(ModeEntry(id = 0, demo = false))
        }
    }

    @Test
    fun testBannerNotVisibleWhenDemoDisabled() {
        composeTestRule.setContent {
            DemoBanner(koinViewModel())
        }

        composeTestRule.onNodeWithTag("DemoBanner").assertDoesNotExist()
    }

    @Test
    fun testBannerVisibleWhenDemoEnabled() {
        runBlocking {
            modeDao.setMode(ModeEntry(id = 0, demo = true))
        }

        composeTestRule.setContent {
            DemoBanner(koinViewModel())
        }

        // Warten, bis der Banner erscheint (wegen asynchronem Flow-Update)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("DemoBanner").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("DemoBanner").assertIsDisplayed()
    }
}
