package org.htwk.pacing.ui.components
//package org.htwk.pacing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleRectangle()
        }
    }
}


@Composable
fun SimpleRectangle(){
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 100.dp)
            .background(Color.Blue)
    )
}
@Preview
@Composable
fun PreviewSimpleRectangle(){
    SimpleRectangle()
}