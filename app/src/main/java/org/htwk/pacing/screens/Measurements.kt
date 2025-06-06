package org.htwk.pacing.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.htwk.pacing.componets.GraphCard
import org.htwk.pacing.componets.Series


@Composable
fun MeasurementsScreen(modifier: Modifier = Modifier) {
    Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            val series = Series(
                arrayOf(0.0f, 0.6f, 0.2f, 1.0f),
                arrayOf(0.0f, 0.2f, 0.6f, 1.0f)
            )
            GraphCard(title = "Title 1", series)
            GraphCard(title = "Title 2", series)
            GraphCard(title = "Title 3", series)
        }
    }
}
