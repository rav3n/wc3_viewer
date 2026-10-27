package wc3viewer.app.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.app.ui.components.LineKey
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Type
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Shared hover position in game seconds: every chart on a tab follows the same cursor. */
@Stable
class TimeCursor {
    var sec: Float? by mutableStateOf(null)

    /** The chart under the pointer: only it draws a tooltip, the others draw just the cursor line. */
    var owner: Any? by mutableStateOf(null)
}

/** Fixed chart gutters, shared so stacked charts line up on the time axis. */
object ChartLayout {
    val left = 48.dp
    /** Wider gutter for charts with row labels (timeline); charts stacked under it use it too. */
    val timelineLeft = 96.dp
    val right = 16.dp
    val top = 12.dp
    val bottom = 26.dp
}

object ChartText {
    val axis = TextStyle(fontSize = 11.sp, color = Palette.textMuted, fontFeatureSettings = "tnum")
    val tipTitle = TextStyle(fontSize = 12.sp, color = Palette.textSecondary, fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)
    val tipValue = TextStyle(fontSize = 13.sp, color = Palette.textPrimary, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)
    val tipLabel = TextStyle(fontSize = 12.sp, color = Palette.textSecondary)
    val tipMono: TextStyle get() = TextStyle(fontSize = 12.sp, color = Palette.textPrimary, fontFamily = wc3viewer.app.ui.theme.Fonts.mono)
}

/** "m:ss" for game seconds. */
fun formatTime(sec: Float): String {
    val t = sec.toInt().coerceAtLeast(0)
    val s = t % 60
    return "${t / 60}:${if (s < 10) "0" else ""}$s"
}

/** Time tick step (seconds) so that labels are at least [minGapPx] apart. */
fun timeStep(maxSec: Float, widthPx: Float, minGapPx: Float): Int {
    val steps = intArrayOf(30, 60, 120, 180, 300, 600, 900, 1200, 1800)
    for (s in steps) if (widthPx * s / maxSec >= minGapPx) return s
    return steps.last()
}

/** Rounds up to a clean axis maximum with [n] equal steps. Returns the step. */
fun niceStep(maxV: Float, n: Int = 4): Float {
    if (maxV <= 0f) return 1f
    val raw = maxV / n
    val mag = 10f.pow(floor(log10(raw)))
    val f = raw / mag
    val nice = when {
        f <= 1f -> 1f
        f <= 2f -> 2f
        f <= 2.5f -> 2.5f
        f <= 5f -> 5f
        else -> 10f
    }
    return nice * mag
}

/** Axis maximum: the first multiple of the nice step that covers [maxV]. */
fun niceMax(maxV: Float, n: Int = 4): Float {
    val step = niceStep(maxV, n)
    return step * ceil(maxV / step).coerceAtLeast(1f)
}

/** Pointer → [TimeCursor] for a plot whose x range starts at [leftPx] and spans [maxSec]. */
fun Modifier.timeHover(
    cursor: TimeCursor, owner: Any, maxSec: Float, leftPx: () -> Float, rightPx: () -> Float, snap: (Float) -> Float = { it },
): Modifier =
    pointerInput(cursor, maxSec) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                val x = e.changes.firstOrNull()?.position?.x ?: continue
                when (e.type) {
                    PointerEventType.Exit -> if (cursor.owner === owner) {
                        cursor.sec = null
                        cursor.owner = null
                    }
                    else -> {
                        val l = leftPx()
                        val r = size.width - rightPx()
                        cursor.sec = if (x < l - 4 || x > r + 4) null else snap(((x - l) / (r - l)).coerceIn(0f, 1f) * maxSec)
                        cursor.owner = owner
                    }
                }
            }
        }
    }

/** One tooltip line: a colored key, a strong value and a secondary label. */
class TipRow(val color: Color?, val value: String, val label: String, val dashed: Boolean = false, val mono: Boolean = false)

/** Draws the time grid (vertical) and x labels in m:ss along the bottom. */
fun DrawScope.drawTimeAxis(measurer: TextMeasurer, maxSec: Float, left: Float, right: Float, top: Float, bottom: Float, labels: Boolean = true) {
    val w = right - left
    val step = timeStep(maxSec, w, 64.dp.toPx())
    var t = 0
    while (t <= maxSec) {
        val x = left + t / maxSec * w
        drawLine(Palette.grid, Offset(x, top), Offset(x, bottom), 1f)
        if (labels) {
            val l = measurer.measure(formatTime(t.toFloat()), ChartText.axis)
            val lx = (x - l.size.width / 2f).coerceIn(left - 6f, right - l.size.width.toFloat())
            drawText(l, topLeft = Offset(lx, bottom + 6.dp.toPx()))
        }
        t += step
    }
    drawLine(Palette.axis, Offset(left, bottom), Offset(right, bottom), 1f)
}

/** Vertical cursor hairline. */
fun DrawScope.drawCursor(x: Float, top: Float, bottom: Float) {
    drawLine(Palette.cursor, Offset(x, top), Offset(x, bottom), 1f)
}

/** Tooltip box next to [anchorX]; flips to the left near the right edge. */
fun DrawScope.drawTooltip(measurer: TextMeasurer, anchorX: Float, top: Float, title: String, rows: List<TipRow>) {
    val pad = 10.dp.toPx()
    val gap = 6.dp.toPx()
    val keyW = 14.dp.toPx()
    val titleL = measurer.measure(title, ChartText.tipTitle)
    val lines = rows.map { r ->
        Triple(r, measurer.measure(r.value, if (r.mono) ChartText.tipMono else ChartText.tipValue), measurer.measure(r.label, ChartText.tipLabel))
    }
    val lineH = 20.dp.toPx()
    val valueW = lines.maxOfOrNull { it.second.size.width } ?: 0
    val contentW = maxOf(
        titleL.size.width.toFloat(),
        (lines.maxOfOrNull { (r, _, l) -> (if (r.color != null) keyW + gap else 0f) + valueW + gap + l.size.width } ?: 0f),
    )
    val w = contentW + pad * 2
    val h = pad * 2 + titleL.size.height + lines.size * lineH
    val x = if (anchorX + 12.dp.toPx() + w < size.width) anchorX + 12.dp.toPx() else anchorX - 12.dp.toPx() - w
    val y = top
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x + 1, y + 3), Size(w, h), CornerRadius(8.dp.toPx()))
    drawRoundRect(Palette.overlay, Offset(x, y), Size(w, h), CornerRadius(8.dp.toPx()))
    drawRoundRect(Palette.borderStrong, Offset(x, y), Size(w, h), CornerRadius(8.dp.toPx()), style = Stroke(1f))
    drawText(titleL, topLeft = Offset(x + pad, y + pad))
    var ly = y + pad + titleL.size.height + 2.dp.toPx()
    for ((r, v, l) in lines) {
        var lx = x + pad
        val cy = ly + lineH / 2
        if (r.color != null) {
            val effect = if (r.dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null
            drawLine(r.color, Offset(lx, cy), Offset(lx + keyW, cy), 2.dp.toPx(), pathEffect = effect)
            lx += keyW + gap
        }
        drawText(v, topLeft = Offset(lx + valueW - v.size.width, cy - v.size.height / 2f))
        drawText(l, topLeft = Offset(lx + valueW + gap, cy - l.size.height / 2f))
        ly += lineH
    }
}

/** Legend row: line key + label per series. */
@Composable
fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier, dashed: Set<String> = emptySet()) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((c, label) in items) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LineKey(c, label in dashed)
                Txt(label, Type.small, maxLines = 1)
            }
        }
    }
}
