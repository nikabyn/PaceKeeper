package org.htwk.pacing.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent(content)
    }

    @Test
    fun firstPage_showsForwardEnabled_backHidden() {
        val vm = FakeWelcomeViewModel()
        setContent {
            WelcomeScreen(onFinished = {}, viewModel = vm)
        }

        composeTestRule.onNodeWithTag("nav_back", useUnmergedTree = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_forward").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("nav_dots").assertIsDisplayed()
    }

    @Test
    fun navigateForward_and_back_viaButtons() {
        val vm = FakeWelcomeViewModel()
        setContent {
            WelcomeScreen(onFinished = {}, viewModel = vm)
        }

        composeTestRule.onNodeWithTag("nav_forward").performClick()
        composeTestRule.onNodeWithTag("nav_back").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("nav_back").performClick()
        composeTestRule.onNodeWithTag("nav_back", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun lastPage_forwardDisabled_untilAgreementChecked() {
        val vm = FakeWelcomeViewModel()
        setContent {
            WelcomeScreen(onFinished = {}, viewModel = vm)
        }

        repeat(3) { composeTestRule.onNodeWithTag("nav_forward").performClick() }
        composeTestRule.onNodeWithTag("nav_forward").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("agreement_checkbox").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("nav_forward").assertIsEnabled()
    }

    @Test
    fun privacyPolicy_opensAndClosesDialog() {
        val vm = FakeWelcomeViewModel()
        setContent {
            WelcomeScreen(onFinished = {}, viewModel = vm)
        }

        repeat(3) { composeTestRule.onNodeWithTag("nav_forward").performClick() }
        composeTestRule.onNodeWithTag("privacy_button").performClick()
        composeTestRule.onNodeWithTag("privacy_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("privacy_close").performClick()
        composeTestRule.onNodeWithTag("privacy_dialog").assertDoesNotExist()
    }

    @Test
    fun finishing_callsOnFinished_whenAgreed() {
        var finishedCalled = false
        val vm = FakeWelcomeViewModel()
        setContent {
            WelcomeScreen(onFinished = { finishedCalled = true }, viewModel = vm)
        }

        repeat(3) { composeTestRule.onNodeWithTag("nav_forward").performClick() }
        composeTestRule.onNodeWithTag("nav_forward").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("agreement_checkbox").performClick()
        composeTestRule.onNodeWithTag("nav_forward").performClick()
        composeTestRule.runOnIdle {
            assert(finishedCalled) { "onFinished should have been called" }
        }
    }
}

class FakeWelcomeViewModel : WelcomeViewModel(dao = FakeUserProfileDao()) {}