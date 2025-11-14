package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.theme.PacingTheme

@Composable
fun FeelingSelectionCard(navController: NavController) {
    val red = PacingTheme.colors.red
    val orange = PacingTheme.colors.orange
    val yellow = PacingTheme.colors.yellow
    val green = PacingTheme.colors.green

    fun background(color: Color) = Modifier.background(color.copy(alpha = 0.3f))

    CardWithTitle(
        stringResource(R.string.track_symptoms),
        modifier = Modifier.testTag("FeelingSelectionCard")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                modifier = Modifier.testTag("VeryBadButton"),
                onClick = { navController.navigate(Route.symptoms(Feeling.VeryBad)) }) {
                Icon(
                    painter = painterResource(R.drawable.very_sad),
                    contentDescription = stringResource(R.string.very_sad),
                    tint = red,
                    modifier = background(red),
                )
            }
            IconButton(
                modifier = Modifier.testTag("BadButton"),
                onClick = { navController.navigate(Route.symptoms(Feeling.Bad)) }) {
                Icon(
                    painter = painterResource(R.drawable.sad),
                    stringResource(R.string.sad),
                    tint = orange,
                    modifier = background(orange),
                )
            }
            IconButton(
                modifier = Modifier.testTag("GoodButton"),
                onClick = { navController.navigate(Route.symptoms(Feeling.Good)) }) {
                Icon(
                    painter = painterResource(R.drawable.happy),
                    stringResource(R.string.happy),
                    tint = yellow,
                    modifier = background(yellow),
                )
            }
            IconButton(
                modifier = Modifier.testTag("VeryGoodButton"),
                onClick = { navController.navigate(Route.symptoms(Feeling.VeryGood)) }) {
                Icon(
                    painter = painterResource(R.drawable.very_happy),
                    stringResource(R.string.very_happy),
                    tint = green,
                    modifier = background(green),
                )
            }
        }
    }
}

