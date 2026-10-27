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
import wc3viewer.app.ui.charts.LineSeries
import wc3viewer.app.ui.charts.RefLine
import wc3viewer.app.ui.charts.TimeCursor
import wc3viewer.app.ui.charts.TimeSeriesChart
import wc3viewer.app.ui.components.EmptyState
import wc3viewer.app.ui.components.EstimateBadge
import wc3viewer.app.ui.components.Panel
import wc3viewer.app.ui.components.PlayerSwatch
import wc3viewer.app.ui.components.SectionLabel
import wc3viewer.app.ui.components.SegmentedControl
import wc3viewer.app.ui.components.StatTile
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.presentation.ChartSeries
import wc3viewer.presentation.EconomyUi

/** Honest caption for every economy number (PRD: exact vs estimate). */
const val ESTIMATE_NOTE = "estimated from orders: cancels, deaths and failed orders are not visible in the replay"

private const val UPKEEP_LOW = 50f
private const val UPKEEP_HIGH = 80f

/** One dashed (= estimate) line per player from the economy UI model. */
fun economySeries(data: MatchData, pick: (EconomyUi) -> ChartSeries): List<LineSeries> =
    data.players.mapNotNull { p ->
        val e = data.sections.economy[p.id] ?: return@mapNotNull null
        val s = pick(e)
        LineSeries(p.name, data.color(p), s.xs, s.ys, dashed = true)
    }

@Composable
fun EconomyTab(data: MatchData, cursor: TimeCursor, wide: Boolean, modifier: Modifier) {
    if (data.sections.economy.isEmpty()) {
        EmptyState("No economy estimate", "This replay has no priced orders.", modifier)
        return
    }
    var resource by remember { mutableStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            EstimateBadge()
            Txt(
                "Estimated from orders: cancels, deaths and failed orders are not visible in the replay. " +
                    "Queue cancels are refunded; prices come from the game data of the patch.",
                Type.caption,
            )
        }

        val tiles = data.players.flatMap { p ->
            val e = data.sections.economy[p.id] ?: return@flatMap emptyList()
            listOf(
                Triple(p, "Gold spent · ${p.name}", fmt(e.totalGold)) to "${fmt(e.totalLumber)} lumber",
                Triple(p, "Reached 50 food · ${p.name}", e.upkeep50 ?: "never") to "80 food: ${e.upkeep80 ?: "never"}",
            )
        }
        for (chunk in if (wide) listOf(tiles) else tiles.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                for ((t, sub) in chunk) StatTile(t.second, t.third, Modifier.weight(1f), sub = sub, identity = data.color(t.first), estimate = true)
            }
        }

        val legend: @Composable () -> Unit = {
            ChartLegend(data.players.map { data.color(it) to it.name }, dashed = data.players.map { it.name }.toSet())
        }
        Panel(
            title = "Spent resources",
            subtitle = "Cumulative, by order time · $ESTIMATE_NOTE",
            trailing = {
                legend()
                SegmentedControl(listOf("Gold", "Lumber", "Total"), resource, { resource = it })
            },
        ) {
            val series = economySeries(data) {
                when (resource) {
                    0 -> it.gold
                    1 -> it.lumber
                    else -> it.total
                }
            }
            TimeSeriesChart(series, data.maxSec, cursor, Modifier.fillMaxWidth().height(260.dp))
        }

        Panel(
            title = "Food used vs cap",
            subtitle = "Used = food of ordered units and heroes, an upper bound: deaths never lower it. " +
                "Cap = food of ordered buildings, max 100",
            trailing = { FoodLegend(data) },
        ) {
            val used = data.players.mapNotNull { p ->
                data.sections.economy[p.id]?.foodUsed?.let { LineSeries("Used · ${p.name}", data.color(p), it.xs, it.ys) }
            }
            val cap = data.players.mapNotNull { p ->
                data.sections.economy[p.id]?.foodCap?.let { LineSeries("Cap · ${p.name}", data.color(p), it.xs, it.ys, dashed = true) }
            }
            TimeSeriesChart(
                used + cap, data.maxSec, cursor, Modifier.fillMaxWidth().height(260.dp), yTicks = 3,
                refLines = listOf(RefLine(UPKEEP_LOW, "Low upkeep · 50"), RefLine(UPKEEP_HIGH, "High upkeep · 80")),
            )
        }

        Panel(title = "Spending by category", subtitle = "Gold + lumber per category · $ESTIMATE_NOTE") {
            CategoryBreakdown(data)
        }
    }
}

@Composable
private fun FoodLegend(data: MatchData) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.l), verticalAlignment = Alignment.CenterVertically) {
        ChartLegend(data.players.map { data.color(it) to it.name })
        ChartLegend(listOf(Palette.textSecondary to "used", Palette.textSecondary to "cap"), dashed = setOf("cap"))
    }
}

/** Per category: one bar per player, scaled to the largest value of both players. */
@Composable
private fun CategoryBreakdown(data: MatchData) {
    val players = data.players.filter { data.sections.economy[it.id] != null }
    val labels = players.firstNotNullOfOrNull { data.sections.economy[it.id] }?.categories?.map { it.label }.orEmpty()
    val max = players.maxOfOrNull { p -> data.sections.economy[p.id]!!.categories.maxOf { it.total } }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        for ((i, label) in labels.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                SectionLabel(label, Modifier.width(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (p in players) {
                        val c = data.sections.economy[p.id]!!.categories[i]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            PlayerSwatch(data.color(p), 8.dp)
                            Box(Modifier.weight(1f).height(10.dp).background(Palette.raised, Shapes.xs)) {
                                if (c.total > 0) {
                                    Box(
                                        Modifier.fillMaxWidth((c.total.toFloat() / max).coerceIn(0.01f, 1f)).height(10.dp)
                                            .background(data.color(p).copy(alpha = 0.75f), Shapes.xs),
                                    )
                                }
                            }
                            Txt("${fmt(c.gold)} g · ${fmt(c.lumber)} l", Type.numSmall, Modifier.width(150.dp), align = TextAlign.End, maxLines = 1)
                        }
                    }
                }
            }
        }
        val notes = players.mapNotNull { p ->
            val e = data.sections.economy[p.id]!!
            if (e.unpricedOrders == 0 && e.cancels == 0) null else "${p.name}: ${e.cancels} refunded cancels, ${e.unpricedOrders} orders without a price"
        }
        for (n in notes) Txt(n, Type.caption)
    }
}

/** 12345 → "12 345". */
private fun fmt(v: Int): String {
    val s = v.toString()
    val neg = s.startsWith("-")
    val d = s.removePrefix("-")
    val out = StringBuilder()
    d.forEachIndexed { i, ch ->
        if (i > 0 && (d.length - i) % 3 == 0) out.append(' ')
        out.append(ch)
    }
    return (if (neg) "-" else "") + out
}
