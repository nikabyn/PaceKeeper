package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.ui.Route

@Composable
fun FeelingSelectionCard(navController: NavController) {
    val red = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFEF5350)
    val orange = if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Color(0xFFEC9C29)
    val yellow = if (isSystemInDarkTheme()) Color(0xFFE6EE9C) else Color(0xFFA8B90C)
    val green = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF66BB6A)

    fun background(color: Color) = Modifier.background(color.copy(alpha = 0.3f))

    CardWithTitle("Track Symptoms") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.navigate(Route.symptoms(Feeling.VeryBad)) }) {
                Icon(
                    painter = painterResource(R.drawable.very_sad),
                    contentDescription = "very sad",
                    tint = red,
                    modifier = background(red),
                )
            }
            IconButton(onClick = { navController.navigate(Route.symptoms(Feeling.Bad)) }) {
                Icon(
                    painter = painterResource(R.drawable.sad),
                    "sad",
                    tint = orange,
                    modifier = background(orange),
                )
            }
            IconButton(onClick = { navController.navigate(Route.symptoms(Feeling.Good)) }) {
                Icon(
                    painter = painterResource(R.drawable.happy),
                    "happy",
                    tint = yellow,
                    modifier = background(yellow),
                )
            }
            IconButton(onClick = { navController.navigate(Route.symptoms(Feeling.VeryGood)) }) {
                Icon(
                    painter = painterResource(R.drawable.very_happy),
                    "very happy",
                    tint = green,
                    modifier = background(green),
                )
            }
        }
    }
}

