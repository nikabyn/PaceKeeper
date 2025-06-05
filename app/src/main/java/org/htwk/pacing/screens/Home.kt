package org.htwk.pacing.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.htwk.pacing.ui.components.BatterieKomponente
import androidx.compose.foundation.layout.*

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BatterieKomponente()
        }

        Box(
            modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("Graph 1")
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("Graph 2")
        }


    }
}
