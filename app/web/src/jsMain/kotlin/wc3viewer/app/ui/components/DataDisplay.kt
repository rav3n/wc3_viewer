package wc3viewer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.app.ui.theme.kindColor
import wc3viewer.app.ui.theme.kindLabel
import wc3viewer.app.ui.theme.raceColor
import wc3viewer.app.ui.theme.raceShort
import wc3viewer.presentation.ObjectKind
import wc3viewer.presentation.RaceUi

// ---------- Badges ----------

enum class BadgeStyle { Filled, Outline, Dashed }

@Composable
fun Badge(text: String, color: Color, style: BadgeStyle = BadgeStyle.Outline, textColor: Color = Palette.textPrimary) {
    val base = Modifier.height(20.dp)
    val m = when (style) {
        BadgeStyle.Filled -> base.background(color, Shapes.s)
        BadgeStyle.Outline -> base.background(color.copy(alpha = 0.14f), Shapes.s).border(1.dp, color.copy(alpha = 0.55f), Shapes.s)
        BadgeStyle.Dashed -> base
    }
    Box(m, contentAlignment = Alignment.Center) {
        if (style == BadgeStyle.Dashed) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color, cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
                )
            }
        }
        Txt(text, Type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), Modifier.padding(horizontal = 6.dp), textColor, maxLines = 1)
    }
}

@Composable
fun RaceBadge(race: RaceUi, label: String) {
    Row(
        Modifier.height(20.dp).background(Palette.raised, Shapes.s).border(1.dp, Palette.border, Shapes.s).padding(start = 3.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(14.dp).background(raceColor(race), Shapes.xs), contentAlignment = Alignment.Center) {
            Txt(raceShort(race), Type.caption.copy(fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold), color = Palette.page)
        }
        Txt(label, Type.caption, color = Palette.textSecondary, maxLines = 1)
    }
}

@Composable
fun WinnerBadge() = Badge("WINNER", Palette.winner, BadgeStyle.Filled, Palette.onAccent)

/** Mandatory marker for values estimated from orders (PRD: exact vs estimate). */
@Composable
fun EstimateBadge(text: String = "≈ ESTIMATE") = Badge(text, Palette.textMuted, BadgeStyle.Dashed, Palette.textSecondary)

@Composable
fun KindBadge(kind: ObjectKind) = Badge(kindLabel(kind).uppercase(), kindColor(kind))

// ---------- Object label ----------

/** Icon placeholder until licensed-free icons exist: a tinted tile with a letter. */
@Composable
fun ObjectIcon(code: String, kind: ObjectKind, name: String? = null, size: Dp = 22.dp) {
    val c = kindColor(kind)
        // Skills are 'A' + race letter + name ('AHbz'): the third char is the meaningful one.
    val letter = (name?.firstOrNull { it.isLetter() } ?: code.getOrNull(if (kind == ObjectKind.SKILL) 2 else 1) ?: '?').uppercaseChar()
    Box(
        Modifier.size(size).background(c.copy(alpha = 0.22f), Shapes.s).border(1.dp, c.copy(alpha = 0.7f), Shapes.s),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            letter.toString(),
            Type.caption.copy(fontSize = (size.value * 0.5f).sp, lineHeight = (size.value * 0.6f).sp, fontWeight = FontWeight.Bold),
            color = c.copy(red = (c.red + 0.25f).coerceAtMost(1f), green = (c.green + 0.25f).coerceAtMost(1f), blue = (c.blue + 0.25f).coerceAtMost(1f)),
        )
    }
}

/** Game-data name; the raw fourcc in monospace only when the name is unknown. */
@Composable
fun ObjectLabel(code: String, name: String?, modifier: Modifier = Modifier, strike: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val deco = if (strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
        if (name != null) {
            Txt(name, Type.body.copy(textDecoration = deco), Modifier.weight(1f, fill = false), maxLines = 1)
        } else {
            Txt(code, Type.mono.copy(color = Palette.textPrimary, textDecoration = deco), maxLines = 1)
        }
    }
}

// ---------- Stat tile ----------

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    identity: Color? = null,
    estimate: Boolean = false,
) {
    Column(
        modifier.background(Palette.raised, Shapes.m).border(1.dp, Palette.border, Shapes.m).padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (identity != null) PlayerSwatch(identity, 8.dp)
            Txt(label, Type.caption, Modifier.weight(1f), maxLines = 1)
            if (estimate) EstimateBadge()
        }
        Txt(value, Type.numLarge, maxLines = 1)
        if (sub != null) Txt(sub, Type.caption, maxLines = 1)
    }
}

// ---------- Tab bar ----------

@Composable
fun TabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        HDivider(Modifier.align(Alignment.BottomStart))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                Box(
                    Modifier.clickableHover({ onSelect(i) }) { h ->
                        if (h && !on) Modifier.background(Palette.raised, Shapes.s) else Modifier
                    }.height(44.dp).padding(horizontal = Space.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(
                        t, Type.bodyStrong,
                        color = if (on) Palette.textPrimary else Palette.textMuted,
                    )
                    if (on) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Palette.accent))
                }
            }
        }
    }
}

// ---------- Table ----------

class TableColumn<T>(
    val header: String,
    val width: Dp? = null,
    val weight: Float = 1f,
    val align: TextAlign = TextAlign.Start,
    val cell: @Composable RowScope.(T) -> Unit,
)

/** Dense table: hairline row separators, header in overline style, zebra-free. */
@Composable
fun <T> DataTable(columns: List<TableColumn<T>>, rows: List<T>, modifier: Modifier = Modifier, rowHeight: Dp = 32.dp) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in columns) {
                Box(c.mod(this), contentAlignment = c.alignment()) { SectionLabel(c.header) }
            }
        }
        HDivider()
        for (r in rows) {
            Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                for (c in columns) {
                    Row(c.mod(this), horizontalArrangement = c.arrangement(), verticalAlignment = Alignment.CenterVertically) { c.cell(this, r) }
                }
            }
            HDivider(color = Palette.border.copy(alpha = 0.6f))
        }
    }
}

private fun <T> TableColumn<T>.mod(scope: RowScope): Modifier = with(scope) {
    (if (width != null) Modifier.width(width) else Modifier.weight(weight)).padding(horizontal = 6.dp)
}

private fun <T> TableColumn<T>.alignment() = when (align) {
    TextAlign.End -> Alignment.CenterEnd
    TextAlign.Center -> Alignment.Center
    else -> Alignment.CenterStart
}

private fun <T> TableColumn<T>.arrangement() = when (align) {
    TextAlign.End -> Arrangement.End
    TextAlign.Center -> Arrangement.Center
    else -> Arrangement.Start
}

