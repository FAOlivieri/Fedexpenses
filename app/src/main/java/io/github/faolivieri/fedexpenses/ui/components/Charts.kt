package io.github.faolivieri.fedexpenses.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.faolivieri.fedexpenses.util.Money
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class DonutSlice(val value: Long, val color: Color)

/** Part-to-whole ring with a 2px surface gap between slices. */
@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * 0.16f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val total = slices.sumOf { it.value }.toFloat()
            if (total <= 0f) {
                drawArc(empty, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            val gapDeg = if (slices.size > 1) (2.dp.toPx() / (Math.PI.toFloat() * d)) * 360f else 0f
            for (s in slices) {
                val sweep = s.value / total * 360f
                drawArc(s.color, start, max(sweep - gapDeg, 0.5f), false, topLeft, Size(d, d), style = Stroke(stroke))
                start += sweep
            }
        }
        center()
    }
}

data class LineSeries(val name: String, val color: Color, val values: List<Long>)

/**
 * Multi-series line chart sharing one y-axis. Tap or drag to place a crosshair; the tooltip
 * lists every series' value for that bucket.
 */
@Composable
fun LineChart(labels: List<String>, series: List<LineSeries>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisText = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    var selected by remember(labels, series.size) { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current

    val maxValue = series.flatMap { it.values }.maxOrNull()?.coerceAtLeast(0) ?: 0
    val minValue = series.flatMap { it.values }.minOrNull()?.coerceAtMost(0) ?: 0
    val ticks = niceTicks(minValue, maxValue)
    val yMin = ticks.first()
    val yMax = ticks.last()

    BoxWithConstraints(modifier) {
        val leftPad = with(density) { 40.dp.toPx() }
        val rightPad = with(density) { 12.dp.toPx() }
        val topPad = with(density) { 8.dp.toPx() }
        val bottomPad = with(density) { 24.dp.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val plotW = widthPx - leftPad - rightPad
        val n = labels.size
        fun xOf(i: Int) = leftPad + if (n <= 1) plotW / 2 else plotW * i / (n - 1)
        fun indexAt(x: Float) = if (n <= 1) 0 else ((x - leftPad) / plotW * (n - 1)).roundToInt().coerceIn(0, n - 1)

        Canvas(
            Modifier.matchParentSize()
                .pointerInput(n, widthPx) { detectTapGestures { selected = if (n == 0) null else indexAt(it.x) } }
                .pointerInput(n, widthPx) {
                    // Horizontal only, so vertical swipes still scroll the screen.
                    detectHorizontalDragGestures { change, _ -> if (n > 0) selected = indexAt(change.position.x) }
                },
        ) {
            val plotH = size.height - topPad - bottomPad
            fun yOf(v: Long) = topPad + plotH * (1f - (v - yMin).toFloat() / (yMax - yMin).coerceAtLeast(1))

            // Recessive hairline grid with compact labels.
            for (t in ticks) {
                val y = yOf(t)
                drawLine(grid, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
                drawLabel(measurer, Money.compact(t), axisText, Offset(0f, y), alignRight = leftPad - 6.dp.toPx())
            }
            // Thin out x labels so they never collide.
            if (n > 0) {
                val labelW = measurer.measure(labels.maxBy { it.length }, axisText).size.width + 8.dp.toPx()
                val step = max(1, ceil(labelW / (plotW / max(1, n - 1).toFloat())).toInt())
                for (i in 0 until n step step) {
                    val r = measurer.measure(labels[i], axisText)
                    val x = (xOf(i) - r.size.width / 2).coerceIn(0f, size.width - r.size.width)
                    drawText(r, topLeft = Offset(x, size.height - bottomPad + 6.dp.toPx()))
                }
            }

            selected?.let { i ->
                drawLine(ink.copy(alpha = 0.35f), Offset(xOf(i), topPad), Offset(xOf(i), topPad + plotH), strokeWidth = 1.dp.toPx())
            }

            val showDots = n <= 18
            for (s in series) {
                if (s.values.isEmpty()) continue
                val path = Path()
                s.values.forEachIndexed { i, v ->
                    if (i == 0) path.moveTo(xOf(i), yOf(v)) else path.lineTo(xOf(i), yOf(v))
                }
                drawPath(path, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                s.values.forEachIndexed { i, v ->
                    if (showDots || i == selected || n == 1) {
                        val r = if (i == selected) 5.dp.toPx() else 4.dp.toPx()
                        drawCircle(surface, r + 2.dp.toPx(), Offset(xOf(i), yOf(v)))
                        drawCircle(s.color, r, Offset(xOf(i), yOf(v)))
                    }
                }
            }
        }

        selected?.let { i ->
            val x = xOf(i)
            val tooltipW = 170.dp
            val tooltipWPx = with(density) { tooltipW.toPx() }
            val left = if (x + tooltipWPx + 12 > widthPx) x - tooltipWPx - 12 else x + 12
            Surface(
                modifier = Modifier.offset { IntOffset(left.roundToInt().coerceAtLeast(0), 0) }.width(tooltipW),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(labels[i], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    for (s in series) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(s.color, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                modifier = Modifier.weight(1f))
                            Text(Money.plain(s.values.getOrElse(i) { 0 }).let { if (s.values.getOrElse(i) { 0 } < 0) "−$it" else it },
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    measurer: TextMeasurer, text: String, style: androidx.compose.ui.text.TextStyle, at: Offset, alignRight: Float,
) {
    val r = measurer.measure(text, style)
    drawText(r, topLeft = Offset(alignRight - r.size.width, at.y - r.size.height / 2))
}

/** 4–6 round tick values covering [min, max], in cents. */
internal fun niceTicks(min: Long, max: Long): List<Long> {
    if (max == min) return listOf(min, min + 10_000)
    val range = (max - min).toDouble()
    val rough = range / 4
    val mag = 10.0.pow(log10(rough).toInt())
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= rough }.toLong().coerceAtLeast(1)
    val start = Math.floorDiv(min, step) * step
    val end = -Math.floorDiv(-max, step) * step
    return generateSequence(start) { it + step }.takeWhile { it <= end }.toList()
}

@Composable
fun LegendItem(name: String, color: Color, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
