package wc3viewer.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type

// ---------- Glyphs (drawn, no icon assets) ----------

@Composable
fun UploadGlyph(color: Color, modifier: Modifier = Modifier.size(40.dp)) {
    Canvas(modifier) {
        val w = size.width
        val s = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * 0.5f, w * 0.62f); lineTo(w * 0.5f, w * 0.12f)
            moveTo(w * 0.3f, w * 0.3f); lineTo(w * 0.5f, w * 0.1f); lineTo(w * 0.7f, w * 0.3f)
            moveTo(w * 0.15f, w * 0.6f); lineTo(w * 0.15f, w * 0.85f); lineTo(w * 0.85f, w * 0.85f); lineTo(w * 0.85f, w * 0.6f)
        }
        drawPath(p, color, style = s)
    }
}

@Composable
fun AlertGlyph(color: Color, modifier: Modifier = Modifier.size(40.dp)) {
    Canvas(modifier) {
        val w = size.width
        val s = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val tri = Path().apply {
            moveTo(w * 0.5f, w * 0.1f); lineTo(w * 0.93f, w * 0.88f); lineTo(w * 0.07f, w * 0.88f); close()
        }
        drawPath(tri, color, style = s)
        drawLine(color, Offset(w * 0.5f, w * 0.38f), Offset(w * 0.5f, w * 0.6f), w * 0.07f, StrokeCap.Round)
        drawCircle(color, w * 0.045f, Offset(w * 0.5f, w * 0.74f))
    }
}

@Composable
fun FileGlyph(color: Color, modifier: Modifier = Modifier.size(40.dp)) {
    Canvas(modifier) {
        val w = size.width
        val s = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * 0.22f, w * 0.08f); lineTo(w * 0.6f, w * 0.08f); lineTo(w * 0.8f, w * 0.28f)
            lineTo(w * 0.8f, w * 0.92f); lineTo(w * 0.22f, w * 0.92f); close()
            moveTo(w * 0.6f, w * 0.08f); lineTo(w * 0.6f, w * 0.28f); lineTo(w * 0.8f, w * 0.28f)
        }
        drawPath(p, color, style = s)
    }
}

// ---------- States ----------

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Txt(title, Type.bodyStrong, color = Palette.textSecondary, align = TextAlign.Center)
        Txt(message, Type.caption, Modifier.widthIn(max = 420.dp), align = TextAlign.Center)
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    hints: List<String>,
    fileName: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 560.dp).background(Palette.surface, Shapes.l)
            .border(1.dp, Palette.border, Shapes.l).padding(Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Box(Modifier.size(52.dp).background(Palette.danger.copy(alpha = 0.12f), Shapes.l), contentAlignment = Alignment.Center) {
                AlertGlyph(Palette.danger, Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(title, Type.title)
                Txt(fileName, Type.mono.copy(color = Palette.textMuted), maxLines = 1)
            }
        }
        Txt(message, Type.body, color = Palette.textSecondary)
        if (hints.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SectionLabel("What to do")
                for (h in hints) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Box(Modifier.padding(top = 8.dp).size(4.dp).background(Palette.textMuted, Shapes.pill))
                        Txt(h, Type.body)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PrimaryButton("Choose another replay", onRetry)
        }
    }
}

@Composable
fun LoadingState(fileName: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition()
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart))
    Column(
        modifier.widthIn(min = 360.dp, max = 480.dp).background(Palette.surface, Shapes.l)
            .border(1.dp, Palette.border, Shapes.l).padding(Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            FileGlyph(Palette.textSecondary, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Txt("Analyzing replay", Type.heading)
                Txt(fileName, Type.mono.copy(color = Palette.textMuted), maxLines = 1)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRoundRect(Palette.raised, cornerRadius = CornerRadius(2.dp.toPx()))
            val segW = size.width * 0.3f
            val x = (size.width + segW) * phase - segW
            drawRoundRect(
                Palette.accent, topLeft = Offset(x.coerceAtLeast(0f), 0f),
                size = Size((segW + minOf(0f, x)).coerceIn(0f, size.width - x.coerceAtLeast(0f)), size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        Txt("Parsing actions and computing metrics. The file stays in your browser.", Type.caption)
    }
}

/** Large drag-and-drop target with a file button. */
@Composable
fun DropZone(active: Boolean, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (active) Palette.accent else Palette.borderStrong
    Box(modifier.background(if (active) Palette.raised else Palette.surface, Shapes.l)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                border, cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(if (active) 2.dp.toPx() else 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Box(Modifier.size(72.dp).background(Palette.raised, Shapes.pill), contentAlignment = Alignment.Center) {
                UploadGlyph(if (active) Palette.textPrimary else Palette.textSecondary, Modifier.size(34.dp))
            }
            Txt(if (active) "Release to analyze" else "Drop a .w3g replay here", Type.title, align = TextAlign.Center)
            Txt("or", Type.caption)
            PrimaryButton("Choose file", onChoose, large = true)
            Spacer(Modifier.height(Space.xs))
            Txt("Warcraft III Reforged · patch 3.0+ · 1v1", Type.caption, align = TextAlign.Center)
        }
    }
}
