package wc3viewer.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import wc3viewer.app.ui.components.EmptyState
import wc3viewer.app.ui.theme.Palette
import kotlin.math.roundToInt

/** A line series in game seconds. */
class LineSeries(
    val label: String,
    val color: Color,
    val xs: FloatArray,
    val ys: FloatArray,
    /** Dashed = estimate (PRD: estimated series look different). */
    val dashed: Boolean = false,
) {
    /** Value at the data point nearest to [sec]. */
    fun valueAt(sec: Float): Float? {
        if (xs.isEmpty()) return null
        var lo = 0
        var hi = xs.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (xs[mid] < sec) lo = mid + 1 else hi = mid
        }
        val i = if (lo > 0 && sec - xs[lo - 1] < xs[lo] - sec) lo - 1 else lo
        return ys[i]
    }

    fun nearestX(sec: Float): Float {
        var best = sec
        var d = Float.MAX_VALUE
        for (x in xs) {
            val dd = kotlin.math.abs(x - sec)
            if (dd < d) { d = dd; best = x }
        }
        return best
    }
}

/** Horizontal reference line (e.g. an upkeep threshold) with a label at the right edge. */
class RefLine(val y: Float, val label: String)

/**
 * Time-series line chart: m:ss x-axis, muted grid, synced vertical cursor and a tooltip that lists
 * [readout] (defaults to the drawn series) at the hovered time.
 */
@Composable
fun TimeSeriesChart(
    series: List<LineSeries>,
    maxSec: Float,
    cursor: TimeCursor,
    modifier: Modifier,
    readout: List<LineSeries> = series,
    tooltip: Boolean = true,
    yTicks: Int = 4,
    showXLabels: Boolean = true,
    leftGutter: androidx.compose.ui.unit.Dp = ChartLayout.left,
    refLines: List<RefLine> = emptyList(),
) {
    if (series.all { it.xs.isEmpty() } || maxSec <= 0f) {
        Box(modifier) { EmptyState("No data", "This replay has no actions for the chart.") }
        return
    }
    val measurer = rememberTextMeasurer()
    val self = androidx.compose.runtime.remember { Any() }
    val density = LocalDensity.current
    val leftPx = with(density) { leftGutter.toPx() }
    val rightPx = with(density) { ChartLayout.right.toPx() }
    val maxY = remember(series, refLines) {
        val top = maxOf(series.maxOf { s -> s.ys.maxOrNull() ?: 0f }, refLines.maxOfOrNull { it.y } ?: 0f)
        niceMax(top.coerceAtLeast(1f), yTicks)
    }
    val snapSource = series.first { it.xs.isNotEmpty() }
    Canvas(modifier.timeHover(cursor, self, maxSec, { leftPx }, { rightPx }) { snapSource.nearestX(it) }) {
        val left = leftPx
        val right = size.width - rightPx
        val top = ChartLayout.top.toPx()
        val bottom = size.height - (if (showXLabels) ChartLayout.bottom.toPx() else 4f)
        fun px(x: Float) = left + x / maxSec * (right - left)
        fun py(y: Float) = bottom - y / maxY * (bottom - top)

        // Y grid with labels.
        for (i in 0..yTicks) {
            val v = maxY * i / yTicks
            val y = py(v)
            if (i > 0) drawLine(Palette.grid, Offset(left, y), Offset(right, y), 1f)
            val l = measurer.measure(v.roundToInt().toString(), ChartText.axis)
            drawText(l, topLeft = Offset(left - 8f - l.size.width, y - l.size.height / 2f))
        }
        drawTimeAxis(measurer, maxSec, left, right, top, bottom, showXLabels)
        for (r in refLines) {
            val y = py(r.y)
            drawLine(Palette.warning.copy(alpha = 0.7f), Offset(left, y), Offset(right, y), 1f * density.density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            val l = measurer.measure(r.label, ChartText.axis.copy(color = Palette.warning))
            drawText(l, topLeft = Offset(right - l.size.width - 4f, y - l.size.height - 2f))
        }

        for (s in series) {
            if (s.xs.isEmpty()) continue
            val path = Path()
            path.moveTo(px(s.xs[0]), py(s.ys[0]))
            for (k in 1 until s.xs.size) path.lineTo(px(s.xs[k]), py(s.ys[k]))
            drawPath(
                path, s.color,
                style = Stroke(
                    2f * density.density, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null,
                ),
            )
        }

        val sec = cursor.sec ?: return@Canvas
        val x = px(sec)
        drawCursor(x, top, bottom)
        for (s in series) {
            val v = s.valueAt(sec) ?: continue
            drawCircle(Palette.surface, 6f * density.density, Offset(x, py(v)))
            drawCircle(s.color, 4f * density.density, Offset(x, py(v)))
        }
        if (tooltip && cursor.owner === self) {
            val rows = readout.mapNotNull { s -> s.valueAt(sec)?.let { TipRow(s.color, it.roundToInt().toString(), s.label, s.dashed) } }
            drawTooltip(measurer, x, top, formatTime(sec), rows)
        }
    }
}
