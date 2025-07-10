package org.htwk.pacing

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.htwk.pacing.ui.components.AnnotatedGraph
import org.htwk.pacing.ui.components.AxisConfig
import org.htwk.pacing.ui.components.Graph
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.Series
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraphTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun graphCard_displaysTitleAndGraph() {
        val title = "Test Title"
        val series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0))

        composeTestRule.setContent {
            GraphCard(title, series, modifier = Modifier.height(300.dp))
        }

        composeTestRule.onNodeWithTag("CardTitle")
            .assertIsDisplayed()
            .assertTextEquals(title)

        composeTestRule.onNodeWithTag("AnnotatedGraph")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(100.dp)
    }

    @Test
    fun annotatedGraph_displaysAxisAndGraph() {
        val series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0))

        composeTestRule.setContent {
            AnnotatedGraph(
                series,
                modifier = Modifier.height(300.dp),
                xConfig = AxisConfig(steps = 3u),
                yConfig = AxisConfig(steps = 4u)
            )
        }

        composeTestRule.onNodeWithTag("AnnotatedGraph")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(100.dp)

        val xAxis = composeTestRule.onNodeWithTag("xAxis")
        val xAxisLabels = xAxis.onChildren()
        xAxisLabels.assertCountEquals(3)
        xAxisLabels.fetchSemanticsNodes().forEachIndexed { index, node ->
            val labelText = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text } ?: ""
            assert(labelText.contains('.') || labelText.contains(','))
        }

        val yAxis = composeTestRule.onNodeWithTag("yAxis")
        val yAxisLabels = yAxis.onChildren()
        yAxisLabels.assertCountEquals(4)
        yAxisLabels.fetchSemanticsNodes().forEachIndexed { index, node ->
            val labelText = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text } ?: ""
            assert(labelText.contains('.') || labelText.contains(','))
        }
    }

    @Test
    fun graph_displays() {
        val series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0))
        val height = 300.dp

        composeTestRule.setContent {
            Graph(
                series,
                modifier = Modifier.height(height),
            )
        }

        composeTestRule.onNodeWithTag("Graph")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(height)
    }
}