package wc3viewer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type

@Composable
fun Txt(
    text: String,
    style: TextStyle = Type.body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    var s = if (color != Color.Unspecified) style.copy(color = color) else style
    if (align != null) s = s.copy(textAlign = align)
    BasicText(text, modifier, s, overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}

/** Hover-aware clickable with a hand cursor. */
@Composable
fun Modifier.clickableHover(onClick: () -> Unit, content: @Composable (hovered: Boolean) -> Modifier = { Modifier }): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return this.then(content(hovered))
        .hoverable(source)
        .clickable(interactionSource = source, indication = null, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(
        modifier.clickableHover(onClick) { h ->
            Modifier.background(if (h) Color.White else Palette.accent, Shapes.m)
        }.padding(horizontal = if (large) 22.dp else 14.dp, vertical = if (large) 11.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Type.bodyStrong, color = Palette.onAccent) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clickableHover(onClick) { h ->
            Modifier.background(if (h) Palette.overlay else Palette.raised, Shapes.m)
                .border(1.dp, if (h) Palette.borderStrong else Palette.border, Shapes.m)
        }.padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Type.bodyStrong) }
}

/** Card surface with an optional header row. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.background(Palette.surface, Shapes.l).border(1.dp, Palette.border, Shapes.l)) {
        if (title != null || trailing != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.m + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Column(Modifier.weight(1f)) {
                    if (title != null) Txt(title, Type.heading)
                    if (subtitle != null) Txt(subtitle, Type.caption)
                }
                trailing?.invoke(this)
            }
        }
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text.uppercase(), Type.overline, modifier)
}

@Composable
fun HDivider(modifier: Modifier = Modifier, color: Color = Palette.border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Small colored square that carries player identity next to text. */
@Composable
fun PlayerSwatch(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, Shapes.xs))
}

/** Short colored line key, as used in chart legends. */
@Composable
fun LineKey(color: Color, dashed: Boolean = false) {
    if (!dashed) {
        Box(Modifier.width(14.dp).height(2.dp).background(color, Shapes.pill))
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { Box(Modifier.width(4.dp).height(2.dp).background(color)) }
        }
    }
}

/** Segmented control: a compact single-choice switch. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Palette.page, Shapes.m).border(1.dp, Palette.border, Shapes.m).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clickableHover({ onSelect(i) }) { h ->
                    Modifier.background(if (on) Palette.overlay else if (h) Palette.raised else Color.Transparent, Shapes.s)
                }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Txt(label, Type.small, color = if (on) Palette.textPrimary else Palette.textSecondary) }
        }
    }
}

/** Toggle chip for filters. */
@Composable
fun FilterChip(text: String, on: Boolean, onClick: () -> Unit, swatch: Color? = null, count: Int? = null) {
    Row(
        Modifier.clickableHover(onClick) { h ->
            Modifier.background(if (on) Palette.overlay else if (h) Palette.raised else Color.Transparent, Shapes.pill)
                .border(1.dp, if (on) Palette.borderStrong else Palette.border, Shapes.pill)
        }.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (swatch != null) Box(Modifier.size(8.dp).background(if (on) swatch else swatch.copy(alpha = 0.35f), Shapes.xs))
        Txt(text, Type.small, color = if (on) Palette.textPrimary else Palette.textMuted)
        if (count != null) Txt("$count", Type.numSmall, color = Palette.textMuted)
    }
}
