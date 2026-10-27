package wc3viewer.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.kindLabel
import wc3viewer.presentation.ObjectKind
import kotlin.math.abs

class TimelineEvent(val sec: Float, val kind: ObjectKind, val code: String, val name: String?, val extra: String? = null)

class TimelineLane(val playerName: String, val color: Color, val events: List<TimelineEvent>)

/** Row order inside a player lane. */
val TimelineRows = listOf(ObjectKind.HERO, ObjectKind.SKILL, ObjectKind.BUILDING, ObjectKind.UNIT, ObjectKind.RESEARCH, ObjectKind.ITEM)

private val ROW_H = 22.dp
private val LANE_HEADER_H = 26.dp
private val LANE_GAP = 10.dp

fun timelineHeight(lanes: Int) = ChartLayout.top + (LANE_HEADER_H + ROW_H * TimelineRows.size) * lanes + LANE_GAP * (lanes - 1) + ChartLayout.bottom + 4.dp

/**
 * Two-lane event timeline on a shared time axis: one lane per player, one row per object kind.
 * Marks carry the player color; the row tells the kind. Hover lists events near the cursor.
 */
@Composable
fun TimelineChart(lanes: List<TimelineLane>, maxSec: Float, cursor: TimeCursor, visible: Set<ObjectKind>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val self = androidx.compose.runtime.remember { Any() }
    val density = LocalDensity.current
    val leftPx = with(density) { ChartLayout.timelineLeft.toPx() }
    val rightPx = with(density) { ChartLayout.right.toPx() }
    val rowLabel = TextStyle(fontSize = 11.sp, color = Palette.textMuted)
    val laneLabel = TextStyle(fontSize = 12.sp, color = Palette.textPrimary, fontWeight = FontWeight.SemiBold)
    Canvas(modifier.fillMaxWidth().height(timelineHeight(lanes.size)).timeHover(cursor, self, maxSec, { leftPx }, { rightPx })) {
        val left = leftPx
        val right = size.width - rightPx
        val top = ChartLayout.top.toPx()
        val bottom = size.height - ChartLayout.bottom.toPx()
        val rowH = ROW_H.toPx()
        fun px(x: Float) = left + x / maxSec * (right - left)

        drawTimeAxis(measurer, maxSec, left, right, top, bottom)

        var y = top
        val rowCenters = HashMap<Pair<Int, ObjectKind>, Float>()
        lanes.forEachIndexed { li, lane ->
            // Lane header: swatch + player name.
            drawRect(lane.color, Offset(0f, y + 7.dp.toPx()), Size(3.dp.toPx(), 12.dp.toPx()))
            drawText(measurer.measure(lane.playerName, laneLabel), topLeft = Offset(10.dp.toPx(), y + 5.dp.toPx()))
            y += LANE_HEADER_H.toPx()
            TimelineRows.forEachIndexed { ri, kind ->
                if (ri % 2 == 0) drawRect(Palette.raised.copy(alpha = 0.55f), Offset(left, y), Size(right - left, rowH))
                val l = measurer.measure(rowLabel(kind), rowLabel)
                drawText(l, topLeft = Offset(10.dp.toPx(), y + (rowH - l.size.height) / 2))
                rowCenters[li to kind] = y + rowH / 2
                y += rowH
            }
            y += LANE_GAP.toPx()
        }

        lanes.forEachIndexed { li, lane ->
            for (e in lane.events) {
                if (e.kind !in visible) continue
                val cy = rowCenters[li to e.kind] ?: continue
                val x = px(e.sec)
                when (e.kind) {
                    ObjectKind.UNIT -> drawLine(lane.color.copy(alpha = 0.8f), Offset(x, cy - 6.dp.toPx()), Offset(x, cy + 6.dp.toPx()), 2.dp.toPx())
                    ObjectKind.HERO -> {
                        val r = 6.dp.toPx()
                        val ring = r + 2.dp.toPx()
                        drawPath(diamond(x, cy, ring), Palette.surface)
                        drawPath(diamond(x, cy, r), lane.color)
                    }
                    ObjectKind.RESEARCH -> {
                        val r = 4.dp.toPx()
                        drawRect(Palette.surface, Offset(x - r - 2, cy - r - 2), Size(2 * r + 4, 2 * r + 4))
                        drawRect(lane.color, Offset(x - r, cy - r), Size(2 * r, 2 * r))
                    }
                    else -> {
                        drawCircle(Palette.surface, 6.dp.toPx(), Offset(x, cy))
                        drawCircle(lane.color, 4.dp.toPx(), Offset(x, cy))
                    }
                }
            }
        }

        val sec = cursor.sec ?: return@Canvas
        val x = px(sec)
        drawCursor(x, top, bottom)
        if (cursor.owner !== self) return@Canvas
        // Events within ±8 px of the cursor, per player.
        val window = 8.dp.toPx() / (right - left) * maxSec
        val rows = ArrayList<TipRow>()
        for (lane in lanes) {
            val near = lane.events.filter { it.kind in visible && abs(it.sec - sec) <= window }.sortedBy { it.sec }
            if (near.isEmpty()) continue
            for (e in near.take(6)) {
                val what = e.name ?: e.code
                rows += TipRow(lane.color, formatTime(e.sec), what + "  " + kindLabel(e.kind).lowercase() + (e.extra?.let { " · $it" } ?: ""))
            }
            if (near.size > 6) rows += TipRow(null, "+${near.size - 6}", "more")
        }
        drawTooltip(measurer, x, top, formatTime(sec) + if (rows.isEmpty()) "  ·  no events here" else "", rows)
    }
}

fun rowLabel(kind: ObjectKind) = when (kind) {
    ObjectKind.HERO -> "Heroes"
    ObjectKind.SKILL -> "Skills"
    ObjectKind.BUILDING -> "Buildings"
    ObjectKind.UNIT -> "Units"
    ObjectKind.RESEARCH -> "Research"
    ObjectKind.ITEM -> "Items"
    ObjectKind.CANCEL -> "Cancels"
}

private fun diamond(x: Float, y: Float, r: Float) = Path().apply {
    moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close()
}
