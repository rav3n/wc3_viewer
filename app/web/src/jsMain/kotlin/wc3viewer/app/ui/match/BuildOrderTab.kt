package wc3viewer.app.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.components.EmptyState
import wc3viewer.app.ui.components.FilterChip
import wc3viewer.app.ui.components.HDivider
import wc3viewer.app.ui.components.ObjectIcon
import wc3viewer.app.ui.components.ObjectLabel
import wc3viewer.app.ui.components.PlayerSwatch
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.app.ui.theme.kindColor
import wc3viewer.app.ui.theme.kindLabel
import wc3viewer.presentation.BuildOrderRow
import wc3viewer.presentation.ObjectKind

/** One build-order line; repeated identical orders within [GROUP_SEC] fold into one with a count. */
private class BoItem(val side: Int, val row: BuildOrderRow, val count: Int)

private const val GROUP_SEC = 5f
val BO_KINDS = listOf(ObjectKind.BUILDING, ObjectKind.UNIT, ObjectKind.HERO, ObjectKind.RESEARCH, ObjectKind.ITEM, ObjectKind.CANCEL)

private fun items(data: MatchData, visible: Set<ObjectKind>): List<BoItem> {
    val out = ArrayList<BoItem>()
    data.players.take(2).forEachIndexed { side, p ->
        var last: BoItem? = null
        for (r in data.sections.buildOrders[p.id].orEmpty()) {
            if (r.kindKey !in visible) continue
            val l = last
            if (l != null && l.row.code == r.code && l.row.kindKey == r.kindKey && r.timeSec - l.row.timeSec <= GROUP_SEC) {
                last = BoItem(side, l.row, l.count + 1)
                out[out.lastIndex] = last
            } else {
                last = BoItem(side, r, 1)
                out += last
            }
        }
    }
    return out.sortedWith(compareBy({ it.row.timeSec }, { it.side }))
}

fun LazyListScope.buildOrderItems(data: MatchData, boFilter: MutableState<Set<ObjectKind>>) {
    item {
        val visible by boFilter
        Column(Modifier.content(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Txt("Show", Type.caption)
                for (k in BO_KINDS) {
                    val n = data.players.sumOf { p -> data.sections.buildOrders[p.id].orEmpty().count { it.kindKey == k } }
                    FilterChip(plural(k), k in visible, { boFilter.value = if (k in visible) visible - k else visible + k }, kindColor(k), n)
                }
            }
            Txt(
                "Times are order times, not completions. Repeated orders within $GROUP_SEC s are folded (×N). Cancels are struck through.",
                Type.caption,
            )
            BoHeader(data)
        }
    }
    val list = items(data, boFilter.value)
    if (list.isEmpty()) {
        item { EmptyState("Nothing to show", "No orders match the selected filters.", Modifier.content()) }
        return
    }
    items(list) { BoLine(it, data, Modifier.content()) }
}

private fun plural(k: ObjectKind) = when (k) {
    ObjectKind.RESEARCH -> "Research"
    ObjectKind.HERO -> "Heroes"
    else -> kindLabel(k) + "s"
}

@Composable
private fun BoHeader(data: MatchData) {
    Column {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            data.players.getOrNull(0)?.let { p ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Txt(p.name, Type.heading, maxLines = 1)
                    PlayerSwatch(data.color(p))
                }
            }
            Txt("TIME", Type.overline, Modifier.width(76.dp), align = TextAlign.Center)
            data.players.getOrNull(1)?.let { p ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    PlayerSwatch(data.color(p))
                    Txt(p.name, Type.heading, maxLines = 1)
                }
            }
        }
        HDivider(color = Palette.borderStrong)
    }
}

@Composable
private fun BoLine(item: BoItem, data: MatchData, modifier: Modifier) {
    val color = data.players.getOrNull(item.side)?.let { data.color(it) } ?: Palette.textMuted
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (item.side == 0) Entry(item, mirrored = true, Modifier.fillMaxHeight().fillMaxWidth())
            }
            Row(Modifier.width(76.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(if (item.side == 0) color else Palette.border))
                Txt(item.row.time, Type.num.copy(color = Palette.textSecondary), Modifier.weight(1f), align = TextAlign.Center)
                Box(Modifier.width(3.dp).fillMaxHeight().background(if (item.side == 1) color else Palette.border))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (item.side == 1) Entry(item, mirrored = false, Modifier.fillMaxHeight().fillMaxWidth())
            }
        }
        HDivider(color = Palette.border.copy(alpha = 0.5f))
    }
}

@Composable
private fun Entry(item: BoItem, mirrored: Boolean, modifier: Modifier) {
    val r = item.row
    val cancel = r.kindKey == ObjectKind.CANCEL
    Row(
        modifier.padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s, if (mirrored) Alignment.End else Alignment.Start),
    ) {
        val parts: List<@Composable () -> Unit> = listOf(
            { ObjectIcon(r.code, r.kindKey, if (r.hasName) r.name else null) },
            { ObjectLabel(r.code, if (r.hasName) r.name else null, strike = cancel) },
            { if (item.count > 1) Txt("×${item.count}", Type.numStrong.copy(color = Palette.textSecondary)) },
            { Txt(kindLabel(r.kindKey), Type.caption) },
        )
        for (p in if (mirrored) parts.reversed() else parts) p()
    }
}

