package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing

@Composable
fun CardWithTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    inner: @Composable ColumnScope.() -> Unit
) {
    val colors = CardStyle.colors
    val shape = CardStyle.shape
    val modifier = modifier
        .fillMaxWidth()
        .testTag("Card")

    @Composable
    fun content() {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = Spacing.large, vertical = Spacing.largeIncreased)
                .fillMaxSize(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .testTag("CardTitle"),
            )
            inner()
        }
    }

    if (onClick == null) {
        Card(modifier = modifier, shape = shape, colors = colors) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) { content() }
    }
}