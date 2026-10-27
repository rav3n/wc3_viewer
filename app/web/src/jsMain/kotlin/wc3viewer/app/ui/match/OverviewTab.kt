package wc3viewer.app.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.charts.ChartLegend
import wc3viewer.app.ui.charts.LineSeries
import wc3viewer.app.ui.charts.TimeCursor
import wc3viewer.app.ui.charts.TimeSeriesChart
import wc3viewer.app.ui.components.EstimateBadge
import wc3viewer.app.ui.components.HDivider
import wc3viewer.app.ui.components.ObjectIcon
import wc3viewer.app.ui.components.Panel
import wc3viewer.app.ui.components.PlayerSwatch
import wc3viewer.app.ui.components.SectionLabel
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.presentation.ObjectKind
import wc3viewer.presentation.PlayerUi
import wc3viewer.presentation.TimingUi

@Composable
fun OverviewTab(data: MatchData, cursor: TimeCursor, wide: Boolean, modifier: Modifier) {
    val left: @Composable (Modifier) -> Unit = { m -> HeadToHead(data, m) }
    val right: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Insights(data)
            Panel(title = "APM over time", subtitle = "60 s window", trailing = { ChartLegend(data.players.map { data.color(it) to it.name }) }) {
                TimeSeriesChart(apmSeries(data), data.maxSec, cursor, Modifier.fillMaxWidth().height(200.dp))
            }
            if (data.sections.economy.isNotEmpty()) {
                Panel(
                    title = "Spent resources",
                    subtitle = "Gold + lumber, $ESTIMATE_NOTE",
                    trailing = { ChartLegend(data.players.map { data.color(it) to it.name }, dashed = data.players.map { it.name }.toSet()); EstimateBadge() },
                ) {
                    TimeSeriesChart(economySeries(data) { it.total }, data.maxSec, cursor, Modifier.fillMaxWidth().height(160.dp))
                }
            }
            HeroSummary(data)
            if (data.overview.warnings.isNotEmpty()) ParserNotes(data.overview.warnings)
        }
    }
    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            left(Modifier.weight(0.9f))
            right(Modifier.weight(1.1f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.l)) {
            left(Modifier.fillMaxWidth())
            right(Modifier.fillMaxWidth())
        }
    }
}

fun apmSeries(data: MatchData, epm: Boolean = false): List<LineSeries> =
    (if (epm) data.sections.epmSeries else data.sections.apmSeries).mapNotNull { s ->
        val p = data.players.firstOrNull { it.id == s.playerId } ?: return@mapNotNull null
        LineSeries(p.name, data.color(p), s.xs, s.ys)
    }

// ---------- Head to head ----------

private class CompareRow(
    val label: String,
    val a: String,
    val b: String,
    /** Optional numeric values for the proportional bars. */
    val av: Float? = null,
    val bv: Float? = null,
    val estimate: Boolean = false,
    /** Object label next to the value: game-data name, or the fourcc when unknown. */
    val aCode: String? = null,
    val bCode: String? = null,
)

private fun label(t: TimingUi) = t.name ?: t.code

@Composable
private fun HeadToHead(data: MatchData, modifier: Modifier) {
    val (pa, pb) = data.players.getOrNull(0) to data.players.getOrNull(1)
    if (pa == null || pb == null) return
    val ra = data.rate(pa)
    val rb = data.rate(pb)
    val ta = data.sections.timings[pa.id]
    val tb = data.sections.timings[pb.id]
    val ka = data.sections.hotkeys[pa.id].orEmpty()
    val kb = data.sections.hotkeys[pb.id].orEmpty()
    fun t(x: TimingUi?) = x?.time ?: "—"

    val mechanics = listOf(
        CompareRow("APM", "${ra?.apm ?: 0}", "${rb?.apm ?: 0}", ra?.apm?.toFloat(), rb?.apm?.toFloat()),
        CompareRow("EPM", "${ra?.epm ?: 0}", "${rb?.epm ?: 0}", ra?.epm?.toFloat(), rb?.epm?.toFloat()),
        CompareRow("Spam share", "${ra?.spamPercent ?: 0}%", "${rb?.spamPercent ?: 0}%"),
        CompareRow("Actions", "${ra?.actions ?: 0}", "${rb?.actions ?: 0}", ra?.actions?.toFloat(), rb?.actions?.toFloat()),
        CompareRow("Hotkey groups", "${ka.size}", "${kb.size}"),
        CompareRow("Group selections", "${ka.sumOf { it.selected }}", "${kb.sumOf { it.selected }}", ka.sumOf { it.selected }.toFloat(), kb.sumOf { it.selected }.toFloat()),
    )
    val timing = buildList {
        for (i in 0 until 3) {
            val ha = ta?.heroes?.getOrNull(i)
            val hb = tb?.heroes?.getOrNull(i)
            if (ha != null || hb != null) add(CompareRow("Hero ${i + 1}", t(ha), t(hb), aCode = ha?.let(::label), bCode = hb?.let(::label)))
        }
        add(CompareRow("Tier 2", t(ta?.tier2), t(tb?.tier2)))
        add(CompareRow("Tier 3", t(ta?.tier3), t(tb?.tier3)))
        add(CompareRow("Expansion", t(ta?.expansion), t(tb?.expansion)))
    }
    val heroesA = data.sections.heroes[pa.id].orEmpty()
    val heroesB = data.sections.heroes[pb.id].orEmpty()
    val levels = CompareRow(
        "Hero levels",
        heroesA.joinToString(" · ") { "${it.levelEstimate}" }.ifEmpty { "—" },
        heroesB.joinToString(" · ") { "${it.levelEstimate}" }.ifEmpty { "—" },
        estimate = true,
    )

    Panel(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.s)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            NameCell(data, pa, Modifier.weight(1f), end = false)
            Txt("Head to head", Type.overline, Modifier.width(132.dp), align = TextAlign.Center)
            NameCell(data, pb, Modifier.weight(1f), end = true)
        }
        Group("Mechanics", mechanics)
        Group("Timings · order time", timing)
        Group("Heroes", listOf(levels))
    }
}

@Composable
private fun NameCell(data: MatchData, p: PlayerUi, modifier: Modifier, end: Boolean) {
    Row(modifier, horizontalArrangement = if (end) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        if (!end) { PlayerSwatch(data.color(p)); Box(Modifier.width(8.dp)) }
        Txt(p.name, Type.bodyStrong, maxLines = 1)
        if (end) { Box(Modifier.width(8.dp)); PlayerSwatch(data.color(p)) }
    }
}

@Composable
private fun Group(title: String, rows: List<CompareRow>) {
    HDivider()
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) { SectionLabel(title) }
    for (r in rows) CompareLine(r)
}

@Composable
private fun CompareLine(r: CompareRow) {
    val max = maxOf(r.av ?: 0f, r.bv ?: 0f)
    Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
        // Left value, bar grows towards the center.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Txt(r.a, Type.numStrong, maxLines = 1)
            if (r.aCode != null) Txt(r.aCode, Type.small, maxLines = 1)
            if (r.av != null && max > 0f) Bar(r.av / max, Modifier.weight(1f), alignEnd = true)
        }
        Row(Modifier.width(if (r.estimate) 200.dp else 132.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Txt(r.label, Type.small, maxLines = 1)
            if (r.estimate) { Box(Modifier.width(6.dp)); EstimateBadge() }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End)) {
            if (r.bv != null && max > 0f) Bar(r.bv / max, Modifier.weight(1f), alignEnd = false)
            if (r.bCode != null) Txt(r.bCode, Type.small, maxLines = 1)
            Txt(r.b, Type.numStrong, maxLines = 1)
        }
    }
}

/** Neutral magnitude bar: identity is already given by the column. */
@Composable
private fun Bar(fraction: Float, modifier: Modifier, alignEnd: Boolean) {
    Box(modifier.height(4.dp).background(Palette.raised, Shapes.pill), contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(4.dp).background(Palette.textMuted, Shapes.pill))
    }
}

// ---------- Insights ----------

@Composable
private fun Insights(data: MatchData) {
    val lines = insights(data)
    if (lines.isEmpty()) return
    Panel(title = "Key differences", subtitle = "From order times and action counts") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            for ((p, text) in lines) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PlayerSwatch(data.color(p), 8.dp)
                    Txt(text, Type.body)
                }
            }
        }
    }
}

private fun insights(data: MatchData): List<Pair<PlayerUi, String>> {
    val (a, b) = data.players.getOrNull(0) to data.players.getOrNull(1)
    if (a == null || b == null) return emptyList()
    val out = ArrayList<Pair<PlayerUi, String>>()
    fun diff(label: String, x: TimingUi?, y: TimingUi?) {
        when {
            x != null && y != null -> {
                val d = (y.timeSec - x.timeSec).toInt()
                if (kotlin.math.abs(d) >= 10) {
                    val first = if (d > 0) a else b
                    out += first to "${first.name} ordered $label ${fmtDelta(kotlin.math.abs(d))} earlier"
                }
            }
            x != null -> out += a to "Only ${a.name} ordered $label (${x.time})"
            y != null -> out += b to "Only ${b.name} ordered $label (${y.time})"
        }
    }
    val ta = data.sections.timings[a.id]
    val tb = data.sections.timings[b.id]
    diff("the second hero", ta?.heroes?.getOrNull(1), tb?.heroes?.getOrNull(1))
    diff("tier 2", ta?.tier2, tb?.tier2)
    diff("an expansion", ta?.expansion, tb?.expansion)
    val ra = data.rate(a)
    val rb = data.rate(b)
    if (ra != null && rb != null && ra.epm != rb.epm) {
        val hi = if (ra.epm > rb.epm) a to rb.epm else b to ra.epm
        val v = maxOf(ra.epm, rb.epm)
        out += hi.first to "${hi.first.name} had ${v - hi.second} more effective actions per minute ($v vs ${hi.second})"
    }
    return out.take(4)
}

private fun fmtDelta(sec: Int) = if (sec < 60) "$sec s" else "${sec / 60} min ${sec % 60} s"

// ---------- Heroes summary ----------

@Composable
private fun HeroSummary(data: MatchData) {
    Panel(title = "Heroes", trailing = { EstimateBadge("≈ LEVEL ESTIMATE") }) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            for (p in data.players) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Row(Modifier.width(160.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        PlayerSwatch(data.color(p), 8.dp)
                        Txt(p.name, Type.small, maxLines = 1)
                    }
                    val heroes = data.sections.heroes[p.id].orEmpty()
                    if (heroes.isEmpty()) Txt("No hero skills learned", Type.caption)
                    for (h in heroes) {
                        Row(
                            Modifier.background(Palette.raised, Shapes.m).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            ObjectIcon(h.heroCode ?: "????", ObjectKind.HERO, if (h.hasName) h.hero else null, size = 24.dp)
                            HeroName(h, Type.bodyStrong)
                            Txt("Lv ${h.levelEstimate}", Type.numSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParserNotes(warnings: List<String>) {
    Panel(title = "Parser notes", subtitle = "Unusual data in the file; metrics may be less precise") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            for (w in warnings.take(6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Box(Modifier.padding(top = 7.dp).size(5.dp).background(Palette.warning, Shapes.pill))
                    Txt(w, Type.small)
                }
            }
            if (warnings.size > 6) Txt("+${warnings.size - 6} more", Type.caption)
        }
    }
}
