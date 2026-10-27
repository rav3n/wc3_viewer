package wc3viewer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.components.DropZone
import wc3viewer.app.ui.components.SectionLabel
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type

@Composable
fun LandingScreen(dragging: Boolean, onPickFile: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl, vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Txt("Why did that game go the way it did?", Type.display, align = TextAlign.Center)
                Txt(
                    "Build orders, timings, APM and heroes of both players on one time axis. 1v1 replays, analyzed in your browser.",
                    Type.body, Modifier.widthIn(max = 560.dp), color = Palette.textSecondary, align = TextAlign.Center,
                )
            }
            DropZone(dragging, onPickFile, Modifier.widthIn(max = 760.dp).fillMaxWidth())
            val tips: @Composable (Modifier) -> Unit = { m ->
                Hint(m, "Where are my replays?") {
                    PathLine("Windows", "Documents\\Warcraft III\\BattleNet\\<id>\\Replays")
                    PathLine("macOS", "~/Library/Application Support/Blizzard/Warcraft III/BattleNet/<id>/Replays")
                    Txt("The last game is saved as LastReplay.w3g. W3Champions also lets you download replays from the match page.", Type.caption)
                }
            }
            val privacy: @Composable (Modifier) -> Unit = { m ->
                Hint(m, "What you get") {
                    Txt("• Exact: APM, EPM, hotkeys, orders, skill picks.", Type.small)
                    Txt("• Estimates are marked ESTIMATE: hero levels, completion times.", Type.small)
                    Txt("• The file never leaves your browser.", Type.small)
                }
            }
            if (wide) {
                Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    tips(Modifier.weight(1.4f))
                    privacy(Modifier.weight(1f))
                }
            } else {
                tips(Modifier.fillMaxWidth())
                privacy(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Hint(modifier: Modifier, title: String, content: @Composable () -> Unit) {
    Column(
        modifier.background(Palette.surface, Shapes.l).border(1.dp, Palette.border, Shapes.l).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun PathLine(os: String, path: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(os, Type.caption)
        Box(Modifier.fillMaxWidth().background(Palette.page, Shapes.s).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Txt(path, Type.mono.copy(fontSize = Type.caption.fontSize), color = Palette.textPrimary)
        }
    }
}

