package org.htwk.pacing.ui.components

/*
class DemoBannerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    @ignore
    fun demoBanner_isVisible_whenDemoModeIsTrue() {
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = true)
        )

        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertIsDisplayed()
    }

    @Test
    fun demoBanner_isNotVisible_whenDemoModeIsFalse() {
        val viewModel = TestModeViewModel(
            initialMode = ModeEntry(id = 0, demo = false)
        )

        composeTestRule.setContent {
            TestDemoBanner(viewModel)
        }

        composeTestRule
            .onNodeWithTag("DemoBanner")
            .assertDoesNotExist()
    }
}

class TestModeViewModel(
    initialMode: ModeEntry?
) : ViewModel() {


    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<ModeEntry?> = _mode
}

@Composable
fun TestDemoBanner(viewModel: TestModeViewModel) {
    val mode by viewModel.mode.collectAsState()


    if (mode?.demo != true) return


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFF9800))
            .padding(8.dp)
            .testTag("DemoBanner"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.demo_banner),
            color = Color.White
        )
    }
}

*/