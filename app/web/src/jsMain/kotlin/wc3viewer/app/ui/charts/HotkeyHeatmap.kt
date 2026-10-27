package wc3viewer.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.app.ui.theme.Palette
import wc3viewer.presentation.HotkeyHeat
import kotlin.math.pow

private val CELL_H = 18.dp

fun heatmapHeight(rows: Int) = ChartLayout.top + CELL_H * rows.coerceAtLeast(1) + ChartLayout.bottom

/**
 * Hotkey heatmap "key × minute" in one hue (the player's), aligned with the time charts above it.
 * [scaleMax] is shared between players so intensities compare.
 */
@Composable
fun HotkeyHeatmap(heat: HotkeyHeat, color: Color, scaleMax: Int, maxSec: Float, cursor: TimeCursor, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val self = androidx.compose.runtime.remember { Any() }
    val density = LocalDensity.current
    val leftPx = with(density) { ChartLayout.left.toPx() }
    val rightPx = with(density) { ChartLayout.right.toPx() }
    val keyStyle = TextStyle(fontSize = 12.sp, color = Palette.textSecondary, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val bucket = heat.bucketSec.toFloat()
    Canvas(
        modifier.fillMaxWidth().height(heatmapHeight(heat.keys.size))
            .timeHover(cursor, self, maxSec, { leftPx }, { rightPx }) { (it / bucket).toInt() * bucket + bucket / 2 },
    ) {
        val left = leftPx
        val right = size.width - rightPx
        val top = ChartLayout.top.toPx()
        val cellH = CELL_H.toPx()
        val bottom = top + cellH * heat.keys.size
        fun px(x: Float) = left + x / maxSec * (right - left)
        val gap = 2f

        drawTimeAxis(measurer, maxSec, left, right, bottom, bottom)
        heat.keys.forEachIndexed { r, key ->
            val y = top + r * cellH
            val l = measurer.measure("$key", keyStyle)
            drawText(l, topLeft = Offset(left - 12.dp.toPx() - l.size.width, y + (cellH - l.size.height) / 2))
            val row = heat.counts[r]
            for (b in row.indices) {
                val x0 = px(b * bucket)
                val x1 = px(minOf((b + 1) * bucket, maxSec))
                val v = row[b]
                val c = if (v == 0) Palette.raised else color.copy(alpha = 0.18f + 0.82f * (v.toFloat() / scaleMax).coerceIn(0f, 1f).pow(0.6f))
                drawRoundRect(c, Offset(x0 + gap / 2, y + gap / 2), Size((x1 - x0 - gap).coerceAtLeast(1f), cellH - gap), CornerRadius(2f))
            }
        }

        val sec = cursor.sec ?: return@Canvas
        val b = (sec / bucket).toInt()
        val x0 = px(b * bucket)
        val x1 = px(minOf((b + 1) * bucket, maxSec))
        drawRect(Color.White.copy(alpha = 0.9f), Offset(x0, top), Size(x1 - x0, bottom - top), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        if (cursor.owner !== self) return@Canvas
        val rows = heat.keys.mapIndexed { r, key -> TipRow(null, "${heat.counts[r].getOrElse(b) { 0 }}", "key $key") }
        drawTooltip(measurer, x1, top, "${formatTime(b * bucket)}–${formatTime(minOf((b + 1) * bucket, maxSec))} selections", rows)
    }
}
