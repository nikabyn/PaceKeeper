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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.htwk.pacing.backend.heuristics.HeartRateZones
import org.htwk.pacing.ui.components.AnnotatedGraph
import org.htwk.pacing.ui.components.AxisConfig
import org.htwk.pacing.ui.components.Graph
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.HeartRateGraphCard
import org.htwk.pacing.ui.components.Series
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeartRateGraphTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createTestZonesResult(): HeartRateZones.HeartRateZonesResult {
        return HeartRateZones.HeartRateZonesResult(
            maxHeartRate = 180.0,
            anaerobicThreshold = 99.0,
            healthZone = 60..80,
            visualHealthZone = 0..80,
            recoveryZone = 81..100,
            exertionZone = 101..120
        )
    }

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
    fun heartRateGraphCard_displaysTitleAndZones() {
        val title = "Heart Rate Zones"
        val series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0))

        composeTestRule.setContent {
            HeartRateGraphCard(
                title = title,
                series = series,
                zonesResult = createTestZonesResult(),
                modifier = Modifier.height(300.dp)
            )
        }

        composeTestRule.onNodeWithTag("CardTitle")
            .assertIsDisplayed()
            .assertTextEquals(title)

        composeTestRule.onNodeWithTag("AnnotatedGraph")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(100.dp)

        // Überprüfe, dass der Graph mit Zonen gerendert wird
        composeTestRule.onNodeWithTag("GraphCanvas")
            .assertIsDisplayed()
    }

    @Test
    fun heartRateGraphCard_withDifferentZoneRanges() {
        val narrowZones = HeartRateZones.HeartRateZonesResult(
            maxHeartRate = 100.0,
            anaerobicThreshold = 55.0,
            healthZone = 50..55,
            visualHealthZone = 0..55,
            recoveryZone = 56..70,
            exertionZone = 71..85
        )

        composeTestRule.setContent {
            HeartRateGraphCard(
                title = "Narrow Zones",
                series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0)),
                zonesResult = narrowZones,
                modifier = Modifier.height(250.dp)
            )
        }

        composeTestRule.onNodeWithTag("CardTitle")
            .assertIsDisplayed()
            .assertTextEquals("Narrow Zones")

        composeTestRule.onNodeWithTag("GraphCanvas")
            .assertIsDisplayed()
    }

    @Test
    fun heartRateGraphCard_withEmptyData() {
        composeTestRule.setContent {
            HeartRateGraphCard(
                title = "Empty Data Zones",
                series = Series(emptyList(), y = emptyList()),
                zonesResult = createTestZonesResult(),
                modifier = Modifier.height(200.dp)
            )
        }

        composeTestRule.onNodeWithTag("CardTitle")
            .assertIsDisplayed()
            .assertTextEquals("Empty Data Zones")

        // Sollte trotzdem den Graphen mit Zonen anzeigen
        composeTestRule.onNodeWithTag("AnnotatedGraph")
            .assertIsDisplayed()
    }

    @Test
    fun heartRateGraphCard_zonesWithExtremeValues() {
        val extremeZones = HeartRateZones.HeartRateZonesResult(
            maxHeartRate = 200.0,
            anaerobicThreshold = 110.0,
            healthZone = 0..50,
            visualHealthZone = 0..50,
            recoveryZone = 51..100,
            exertionZone = 101..150
        )

        val extremeData = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0))

        composeTestRule.setContent {
            HeartRateGraphCard(
                title = "Extreme Values",
                series = extremeData,
                zonesResult = extremeZones,
                modifier = Modifier.height(350.dp)
            )
        }

        composeTestRule.onNodeWithTag("CardTitle")
            .assertIsDisplayed()
            .assertTextEquals("Extreme Values")

        composeTestRule.onNodeWithTag("GraphCanvas")
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

        composeTestRule.onNodeWithTag("GraphCanvas")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(height)
    }

    @Test
    fun heartRateGraphCard_multipleInstances() {
        val zones1 = createTestZonesResult()
        val zones2 = HeartRateZones.HeartRateZonesResult(
            maxHeartRate = 190.0,
            anaerobicThreshold = 104.5,
            healthZone = 65..85,
            visualHealthZone = 0..85,
            recoveryZone = 86..110,
            exertionZone = 111..135
        )

        composeTestRule.setContent {
            // Zwei verschiedene HeartRateGraphCards mit unterschiedlichen Zonen
            HeartRateGraphCard(
                title = "User 1 Zones",
                series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0)),
                zonesResult = zones1,
                modifier = Modifier.height(200.dp)
            )

            HeartRateGraphCard(
                title = "User 2 Zones",
                series = Series(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0, 3.0)),
                zonesResult = zones2,
                modifier = Modifier.height(200.dp)
            )
        }

        composeTestRule.onNodeWithTag("CardTitle", true)
            .onChildren()
            .assertCountEquals(2)

        composeTestRule.onNodeWithText("User 1 Zones")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("User 2 Zones")
            .assertIsDisplayed()
    }
}