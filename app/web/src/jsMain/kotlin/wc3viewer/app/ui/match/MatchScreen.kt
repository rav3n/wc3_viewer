package wc3viewer.app.ui.match

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import wc3viewer.app.ui.charts.TimeCursor
import wc3viewer.app.ui.components.Badge
import wc3viewer.app.ui.components.BadgeStyle
import wc3viewer.app.ui.components.RaceBadge
import wc3viewer.app.ui.components.TabBar
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.components.WinnerBadge
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.PlayerColors
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.presentation.MatchOverview
import wc3viewer.presentation.ObjectKind
import wc3viewer.presentation.PlayerUi
import wc3viewer.presentation.ReplaySections

enum class MatchTab(val title: String, val key: String) {
    Overview("Overview", "overview"),
    Timeline("Timeline", "timeline"),
    Economy("Economy", "economy"),
    BuildOrder("Build order", "build"),
    Apm("APM & hotkeys", "apm"),
    Heroes("Heroes", "heroes"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Overview
    }
}

/** Everything a tab needs, with player lookups. */
class MatchData(val fileName: String, val overview: MatchOverview, val sections: ReplaySections) {
    val players: List<PlayerUi> get() = overview.players
    fun color(p: PlayerUi): Color = PlayerColors[p.colorIndex]
    fun rate(p: PlayerUi) = sections.rates.firstOrNull { it.playerId == p.id }
    val maxSec: Float get() = sections.chartMaxX.coerceAtLeast(1f)
}

/** Content column: centered, capped width, page gutters. */
fun Modifier.content(): Modifier = this.widthIn(max = Space.contentMax).fillMaxWidth().padding(horizontal = Space.xl)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchScreen(fileName: String, overview: MatchOverview, sections: ReplaySections, initialTab: MatchTab) {
    val data = remember(fileName, overview, sections) { MatchData(fileName, overview, sections) }
    var tab by remember(data) { mutableStateOf(initialTab) }
    val cursor = remember(data) { TimeCursor() }
    val boFilter = remember(data) { mutableStateOf(BO_KINDS.toSet()) }
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        LazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
            item { MatchHeader(data, wide, Modifier.content().padding(top = Space.xl, bottom = Space.m)) }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(Palette.page), contentAlignment = Alignment.Center) {
                    TabBar(MatchTab.entries.map { it.title }, tab.ordinal, { tab = MatchTab.entries[it] }, Modifier.content())
                }
            }
            item { Box(Modifier.height(Space.l)) }
            tabContent(tab, data, cursor, wide, boFilter)
            item { Box(Modifier.height(Space.xxxl)) }
        }
    }
}

private fun LazyListScope.tabContent(
    tab: MatchTab, data: MatchData, cursor: TimeCursor, wide: Boolean, boFilter: MutableState<Set<ObjectKind>>,
) {
    when (tab) {
        MatchTab.Overview -> item { OverviewTab(data, cursor, wide, Modifier.content()) }
        MatchTab.Timeline -> item { TimelineTab(data, cursor, Modifier.content()) }
        MatchTab.Economy -> item { EconomyTab(data, cursor, wide, Modifier.content()) }
        MatchTab.BuildOrder -> buildOrderItems(data, boFilter)
        MatchTab.Apm -> item { ApmTab(data, cursor, wide, Modifier.content()) }
        MatchTab.Heroes -> item { HeroesTab(data, wide, Modifier.content()) }
    }
}

// ---------- Header ----------

@Composable
private fun MatchHeader(data: MatchData, wide: Boolean, modifier: Modifier) {
    val o = data.overview
    val (a, b) = o.players.getOrNull(0) to o.players.getOrNull(1)
    Column(
        modifier.clip(Shapes.l).background(Palette.surface, Shapes.l).border(1.dp, Palette.border, Shapes.l),
    ) {
        if (wide) {
            Row(Modifier.fillMaxWidth().height(104.dp), verticalAlignment = Alignment.CenterVertically) {
                if (a != null) PlayerBlock(data, a, mirrored = false, Modifier.weight(1f).fillMaxHeight())
                CenterBlock(o, Modifier.width(200.dp))
                if (b != null) PlayerBlock(data, b, mirrored = true, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column {
                if (a != null) PlayerBlock(data, a, mirrored = false, Modifier.fillMaxWidth().height(84.dp))
                CenterBlock(o, Modifier.fillMaxWidth().padding(vertical = Space.s))
                if (b != null) PlayerBlock(data, b, mirrored = false, Modifier.fillMaxWidth().height(84.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Meta("Map", o.map)
            Meta("Patch", o.patch)
            Meta("Result", if (o.players.any { it.isWinner }) "${o.winner} won" else "Not determined")
            Box(Modifier.weight(1f))
            if (o.warnings.isNotEmpty()) {
                Badge("${o.warnings.size} parser notes", Palette.warning, BadgeStyle.Outline, Palette.textSecondary)
            }
            Txt(data.fileName, Type.mono.copy(color = Palette.textMuted, fontSize = Type.caption.fontSize), maxLines = 1)
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, Type.caption)
        Txt(value, Type.small.copy(color = Palette.textPrimary), maxLines = 1)
    }
}

@Composable
private fun CenterBlock(o: MatchOverview, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt("VS", Type.overline.copy(letterSpacing = 2.4.sp))
        Txt(o.duration, Type.numLarge.copy(fontFeatureSettings = "tnum"))
        Txt("game length", Type.caption)
    }
}

@Composable
private fun PlayerBlock(data: MatchData, p: PlayerUi, mirrored: Boolean, modifier: Modifier) {
    val rate = data.rate(p)
    val color = data.color(p)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!mirrored) Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(
            Modifier.weight(1f).padding(horizontal = Space.xl),
            horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (mirrored && p.isWinner) WinnerBadge()
                Txt(p.name, Type.title, maxLines = 1, align = if (mirrored) TextAlign.End else TextAlign.Start)
                if (!mirrored && p.isWinner) WinnerBadge()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                RaceBadge(p.raceKey, p.race)
                if (rate != null) {
                    Txt("APM", Type.caption)
                    Txt("${rate.apm}", Type.numStrong)
                    Txt("EPM", Type.caption)
                    Txt("${rate.epm}", Type.numStrong)
                }
            }
        }
        if (mirrored) Box(Modifier.width(4.dp).fillMaxHeight().background(color))
    }
}
