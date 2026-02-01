package org.htwk.pacing.ui.components.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


@Composable
fun GraphLayout(
    xLabels: @Composable () -> Unit,
    yLabels: @Composable () -> Unit,
    graph: @Composable () -> Unit,
) = ConstraintLayout(Modifier.fillMaxSize()) {
    val (yAxis, graph, xAxis) = createRefs()

    Axis(
        horizontal = false,
        modifier = Modifier
            .constrainAs(yAxis) {
                start.linkTo(parent.start)
                top.linkTo(parent.top)
                end.linkTo(graph.start)
                bottom.linkTo(graph.bottom)
                height = Dimension.fillToConstraints
            }
    ) {
        yLabels()
    }

    Box(
        modifier = Modifier.constrainAs(graph) {
            top.linkTo(parent.top)
            bottom.linkTo(xAxis.top)
            start.linkTo(yAxis.end)
            end.linkTo(parent.end)
            height = Dimension.fillToConstraints
            width = Dimension.fillToConstraints
        }
    ) {
        graph()
    }

    Axis(
        horizontal = true,
        modifier = Modifier
            .constrainAs(xAxis) {
                start.linkTo(graph.start)
                top.linkTo(graph.bottom)
                end.linkTo(graph.end)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
            }
    ) {
        xLabels()
    }
}

@Composable
fun AxisLabelHourMinutes(time: Instant) {
    val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
    val text = "%02d:%02d".format(localTime.hour, localTime.minute)
    AxisLabel(text)
}

@Composable
fun AxisLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.testTag("AxisLabel")
)

@Composable
fun Axis(
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    labels: @Composable () -> Unit,
) =
    if (horizontal) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = modifier.fillMaxWidth(),
        ) { labels() }
    } else {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
            modifier = modifier.fillMaxHeight(),
        ) { labels() }
    }
