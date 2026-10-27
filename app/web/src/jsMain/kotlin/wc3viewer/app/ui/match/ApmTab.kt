package wc3viewer.app.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.charts.ChartLegend
import wc3viewer.app.ui.charts.HotkeyHeatmap
import wc3viewer.app.ui.charts.LineSeries
import wc3viewer.app.ui.charts.TimeCursor
import wc3viewer.app.ui.charts.TimeSeriesChart
import wc3viewer.app.ui.components.DataTable
import wc3viewer.app.ui.components.EmptyState
import wc3viewer.app.ui.components.Panel
import wc3viewer.app.ui.components.PlayerSwatch
import wc3viewer.app.ui.components.SegmentedControl
import wc3viewer.app.ui.components.StatTile
import wc3viewer.app.ui.components.TableColumn
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.presentation.HotkeyRow
import wc3viewer.presentation.PlayerUi

@Composable
fun ApmTab(data: MatchData, cursor: TimeCursor, wide: Boolean, modifier: Modifier) {
    var metric by remember { mutableStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.l)) {
        // Stat tiles: one row of four on wide screens, two rows otherwise.
        val tiles = data.players.flatMap { p ->
            val r = data.rate(p)
            listOf(
                Triple(p, "APM · ${p.name}", "${r?.apm ?: 0}") to "${r?.actions ?: 0} actions",
                Triple(p, "EPM · ${p.name}", "${r?.epm ?: 0}") to "${r?.spamPercent ?: 0}% filtered as spam",
            )
        }
        val chunks = if (wide) listOf(tiles) else tiles.chunked(2)
        for (chunk in chunks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                for ((t, sub) in chunk) StatTile(t.second, t.third, Modifier.weight(1f), sub = sub, identity = data.color(t.first))
            }
        }

        val apm = apmSeries(data)
        val epm = apmSeries(data, epm = true)
        val readout = data.players.flatMap { p ->
            listOfNotNull(
                apm.firstOrNull { it.label == p.name }?.let { LineSeries("APM · ${p.name}", it.color, it.xs, it.ys) },
                epm.firstOrNull { it.label == p.name }?.let { LineSeries("EPM · ${p.name}", it.color, it.xs, it.ys, dashed = true) },
            )
        }
        Panel(
            title = if (metric == 0) "APM over time" else "EPM over time",
            subtitle = if (metric == 0) "Actions per minute, 60 s sliding window" else
                "Effective actions: without repeated orders (< 500 ms), re-selections and repeated hotkey presses",
            trailing = {
                ChartLegend(data.players.map { data.color(it) to it.name })
                SegmentedControl(listOf("APM", "EPM"), metric, { metric = it })
            },
        ) {
            TimeSeriesChart(if (metric == 0) apm else epm, data.maxSec, cursor, Modifier.fillMaxWidth().height(300.dp), readout = readout)
        }

        Panel(title = "Hotkey groups", subtitle = "Selections per minute; brighter = more presses. Same scale for both players") {
            val scale = data.players.maxOfOrNull { data.sections.hotkeyHeat[it.id]?.max ?: 0 }?.coerceAtLeast(1) ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                for (p in data.players) HotkeyBlock(data, p, scale, cursor, wide)
            }
        }
    }
}

@Composable
private fun HotkeyBlock(data: MatchData, p: PlayerUi, scale: Int, cursor: TimeCursor, wide: Boolean) {
    val heat = data.sections.hotkeyHeat[p.id]
    val rows = data.sections.hotkeys[p.id].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PlayerSwatch(data.color(p))
            Txt(p.name, Type.bodyStrong)
            Txt("· ${rows.size} groups · ${rows.sumOf { it.selected }} selections", Type.caption)
        }
        if (heat == null || heat.keys.isEmpty()) {
            EmptyState("No hotkey groups", "This player never selected a control group.")
            return
        }
        val chart: @Composable (Modifier) -> Unit = { m -> HotkeyHeatmap(heat, data.color(p), scale, data.maxSec, cursor, m) }
        val table: @Composable (Modifier) -> Unit = { m -> HotkeyTable(rows, m) }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                chart(Modifier.weight(1f))
                table(Modifier.width(360.dp))
            }
        } else {
            chart(Modifier.fillMaxWidth())
            table(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HotkeyTable(rows: List<HotkeyRow>, modifier: Modifier) {
    val maxSel = rows.maxOfOrNull { it.selected }?.coerceAtLeast(1) ?: 1
    DataTable(
        listOf(
            TableColumn("Key", width = 44.dp) { Txt("${it.key}", Type.numStrong) },
            TableColumn("Presses", weight = 1.5f, align = TextAlign.End) { r ->
                Txt("${r.selected}", Type.num)
                Box(Modifier.width(6.dp))
                MiniBar(r.selected.toFloat() / maxSel)
            },
            TableColumn("Set", align = TextAlign.End) { Txt("${it.assigned}", Type.num) },
            TableColumn("Units", align = TextAlign.End) { Txt("${it.lastSize}", Type.num) },
            TableColumn("First", align = TextAlign.End) { Txt(it.firstAssigned ?: "—", Type.num.copy(color = Palette.textSecondary)) },
        ),
        rows,
        modifier,
        rowHeight = 26.dp,
    )
}

@Composable
private fun MiniBar(f: Float) {
    Box(Modifier.width(40.dp).height(4.dp).background(Palette.raised, Shapes.pill)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f.coerceIn(0.04f, 1f)).height(4.dp).background(Palette.textMuted, Shapes.pill))
    }
}
