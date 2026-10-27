package wc3viewer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.presentation.ObjectKind
import wc3viewer.presentation.RaceUi

/**
 * Design tokens of the dark esports theme. Everything visual goes through these objects,
 * screens never hard-code colors or sizes.
 */
object Palette {
    // Background layers, darkest first.
    val page = Color(0xFF0B0E13)
    val surface = Color(0xFF12161D)
    val raised = Color(0xFF181D25)
    val overlay = Color(0xFF1F2530)
    val pressed = Color(0xFF272E3A)

    val border = Color(0xFF242A34)
    val borderStrong = Color(0xFF333B48)

    // Text: three levels. Text never wears a series color.
    val textPrimary = Color(0xFFE8EAED)
    val textSecondary = Color(0xFFAAB1BC)
    val textMuted = Color(0xFF6E7684)

    // Interactive accent is neutral (light), so it never competes with the player colors.
    val accent = Color(0xFFE8EAED)
    val onAccent = Color(0xFF0B0E13)

    val winner = Color(0xFFE3B341)
    val danger = Color(0xFFE5534B)
    val warning = Color(0xFFD29922)

    // Chart chrome.
    val grid = Color(0xFF1E242D)
    val axis = Color(0xFF353C48)
    val cursor = Color(0xFF8A929E)
}

/**
 * Player identity colors: blue / orange. Validated as a categorical pair on the dark surface
 * (lightness band, chroma, CVD ΔE ≥ 26 for protan/deutan/tritan, contrast ≥ 3:1).
 */
object PlayerColors {
    private val colors = listOf(Color(0xFF3987E5), Color(0xFFD95926))
    operator fun get(index: Int): Color = colors[index.mod(colors.size)]
}

/** Race colors are secondary: only small badges with a text label, never chart marks. */
fun raceColor(race: RaceUi): Color = when (race) {
    RaceUi.HUMAN -> Color(0xFFC2A15A)
    RaceUi.ORC -> Color(0xFFB4533C)
    RaceUi.NIGHT_ELF -> Color(0xFF8C6CCB)
    RaceUi.UNDEAD -> Color(0xFF4FA08A)
    RaceUi.UNKNOWN -> Color(0xFF6E7684)
}

fun raceShort(race: RaceUi): String = when (race) {
    RaceUi.HUMAN -> "HU"
    RaceUi.ORC -> "OR"
    RaceUi.NIGHT_ELF -> "NE"
    RaceUi.UNDEAD -> "UD"
    RaceUi.UNKNOWN -> "??"
}

/** Muted per-kind colors for icon placeholders and kind badges. */
fun kindColor(kind: ObjectKind): Color = when (kind) {
    ObjectKind.BUILDING -> Color(0xFF6A7C94)
    ObjectKind.UNIT -> Color(0xFF4E8C6C)
    ObjectKind.HERO -> Color(0xFFC29A3E)
    ObjectKind.RESEARCH -> Color(0xFF7F6BC4)
    ObjectKind.ITEM -> Color(0xFFA9733E)
    ObjectKind.CANCEL -> Color(0xFF8F4B4B)
    ObjectKind.SKILL -> Color(0xFF4A8AB0)
}

fun kindLabel(kind: ObjectKind): String = when (kind) {
    ObjectKind.BUILDING -> "Building"
    ObjectKind.UNIT -> "Unit"
    ObjectKind.HERO -> "Hero"
    ObjectKind.RESEARCH -> "Research"
    ObjectKind.ITEM -> "Item"
    ObjectKind.CANCEL -> "Cancel"
    ObjectKind.SKILL -> "Skill"
}

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Content max width on wide screens. */
    val contentMax = 1480.dp
}

object Shapes {
    val xs = RoundedCornerShape(3.dp)
    val s = RoundedCornerShape(4.dp)
    val m = RoundedCornerShape(6.dp)
    val l = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(50)
}

object Type {
    private val base = TextStyle(color = Palette.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)

    /** Tabular figures so columns of numbers align. */
    private const val TNUM = "tnum"

    val display = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    val title = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val heading = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val body = base
    val bodyStrong = base.copy(fontWeight = FontWeight.Medium)
    val small = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Palette.textSecondary)
    val caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp, color = Palette.textMuted)

    /** Upper-case section labels. */
    val overline = base.copy(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp, color = Palette.textMuted,
    )

    val numLarge = base.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
    val num = base.copy(fontFeatureSettings = TNUM)
    val numStrong = num.copy(fontWeight = FontWeight.SemiBold)
    val numSmall = small.copy(fontFeatureSettings = TNUM)

    /** Raw object IDs (fourcc). */
    val mono: TextStyle get() = base.copy(fontFamily = Fonts.mono, fontSize = 12.sp, color = Palette.textSecondary)
}
