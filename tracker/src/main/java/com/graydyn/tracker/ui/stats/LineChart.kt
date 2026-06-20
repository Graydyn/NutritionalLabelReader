package com.graydyn.tracker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartSeries(
    val points: Map<Int, Float>,   // x (day-of-month) -> y
    val color: Color,
    val breakOnGaps: Boolean       // true = calories (skip missing), false = weight
)

private const val LEFT_GUTTER = 96f   // px for Y labels
private const val BOTTOM_GUTTER = 40f // px for X labels
private const val TOP_PAD = 16f
private const val RIGHT_PAD = 16f
private const val Y_TICKS = 4

@Composable
fun LineChart(
    series: List<ChartSeries>,
    xDomainMax: Int,
    yRange: YRange,
    referenceLine: Float? = null,
    referenceColor: Color = Color(0xFF9E9E9E),
    yLabel: (Float) -> String,
    gridColor: Color = Color(0x33000000),
    axisTextColor: Color = Color(0xFF666666),
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val plotLeft = LEFT_GUTTER
        val plotTop = TOP_PAD
        val plotRight = size.width - RIGHT_PAD
        val plotBottom = size.height - BOTTOM_GUTTER
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        val span = (yRange.max - yRange.min).takeIf { it != 0f } ?: 1f

        fun xFor(day: Int): Float =
            if (xDomainMax <= 1) plotLeft
            else plotLeft + (day - 1).toFloat() / (xDomainMax - 1).toFloat() * plotWidth

        fun yFor(value: Float): Float =
            plotBottom - ((value - yRange.min) / span) * plotHeight

        // Gridlines + Y tick labels
        for (i in 0..Y_TICKS) {
            val v = yRange.min + span * i / Y_TICKS
            val y = yFor(v)
            drawLine(gridColor, start = Offset(plotLeft, y), end = Offset(plotRight, y), strokeWidth = 1f)
            val label = measurer.measure(yLabel(v), TextStyle(color = axisTextColor, fontSize = 10.sp))
            drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
        }

        // X tick labels: 1, 5, 10, ... and last day
        val xTicks = buildList {
            add(1)
            var d = 5
            while (d < xDomainMax) { add(d); d += 5 }
            add(xDomainMax)
        }.distinct()
        for (day in xTicks) {
            val label = measurer.measure(day.toString(), TextStyle(color = axisTextColor, fontSize = 10.sp))
            drawText(label, topLeft = Offset(xFor(day) - label.size.width / 2f, plotBottom + 6f))
        }

        // Reference (goal) line, dashed
        referenceLine?.let { ref ->
            if (ref in yRange.min..yRange.max) {
                val y = yFor(ref)
                drawLine(
                    referenceColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            }
        }

        // Series
        series.forEach { s -> drawSeries(s, xDomainMax, ::xFor, ::yFor) }
    }
}

private fun DrawScope.drawSeries(
    s: ChartSeries,
    xDomainMax: Int,
    xFor: (Int) -> Float,
    yFor: (Float) -> Float
) {
    val path = Path()
    var penDown = false
    for (day in 1..xDomainMax) {
        val v = s.points[day]
        if (v == null) {
            if (s.breakOnGaps) penDown = false
            continue
        }
        val px = xFor(day)
        val py = yFor(v)
        if (!penDown) { path.moveTo(px, py); penDown = true } else { path.lineTo(px, py) }
        drawCircle(s.color, radius = 3f, center = Offset(px, py))
    }
    drawPath(path, color = s.color, style = Stroke(width = 3f))
}
