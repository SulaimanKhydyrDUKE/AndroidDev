package com.example.faceid.coverage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun FaceCoverageProgressUI(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {

        val clampedProgress = progress.coerceIn(0f, 1f)

        val totalSegments = 60
        val sweepPerSegment = 360f / totalSegments
        val activeSegments = (clampedProgress * totalSegments).toInt()

        val stroke = Stroke(
            width = 12.dp.toPx(),
            cap = StrokeCap.Round
        )

        repeat(totalSegments) { i ->
            drawArc(
                color = if (i < activeSegments) Color(0xFF00FF88) else Color.DarkGray,
                startAngle = -90f + i * sweepPerSegment,
                sweepAngle = sweepPerSegment * 0.6f, // creates spacing
                useCenter = false,
                style = stroke
            )
        }
    }
}
