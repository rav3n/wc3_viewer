package wc3viewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.app.ui.components.DropZone
import wc3viewer.app.ui.components.ErrorState
import wc3viewer.app.ui.components.HDivider
import wc3viewer.app.ui.components.LoadingState
import wc3viewer.app.ui.components.SecondaryButton
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.match.MatchScreen
import wc3viewer.app.ui.match.MatchTab
import wc3viewer.app.ui.screens.LandingScreen
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.PlayerColors
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.presentation.ReplayUiState
import wc3viewer.presentation.ReplayViewModel

@Composable
fun App(viewModel: ReplayViewModel, dragging: State<Boolean>, onPickFile: () -> Unit, initialTab: MatchTab = MatchTab.Overview) {
    val state by viewModel.state.collectAsState()
    val isDragging by dragging
    Column(Modifier.fillMaxSize().background(Palette.page)) {
        TopBar(showOpen = state !is ReplayUiState.Empty, onPickFile = onPickFile)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                ReplayUiState.Empty -> LandingScreen(isDragging, onPickFile)
                is ReplayUiState.Loading -> Centered { LoadingState(s.fileName) }
                is ReplayUiState.Failed -> Centered {
                    ErrorState(s.title, s.message, s.hints, s.fileName, onPickFile)
                }
                is ReplayUiState.Loaded -> MatchScreen(s.fileName, s.overview, s.sections, initialTab)
            }
            // Drop overlay over any non-landing screen.
            if (isDragging && state !is ReplayUiState.Empty) {
                Box(Modifier.fillMaxSize().background(Palette.page.copy(alpha = 0.82f)).padding(Space.xxl), contentAlignment = Alignment.Center) {
                    DropZone(active = true, onChoose = onPickFile, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun TopBar(showOpen: Boolean, onPickFile: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.page)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Space.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Logo()
            Txt("W3 REPLAY ANALYZER", Type.overline.copy(color = Palette.textPrimary, fontSize = 12.sp, letterSpacing = 1.6.sp))
            Box(Modifier.weight(1f))
            if (showOpen) {
                Txt("Drop a replay anywhere", Type.caption)
                SecondaryButton("Open replay", onPickFile)
            }
        }
        HDivider()
    }
}

/** Two overlapping squares in the player colors: "player vs player". */
@Composable
private fun Logo() {
    Box(Modifier.size(22.dp)) {
        Box(Modifier.size(14.dp).background(PlayerColors[0], Shapes.xs))
        Box(Modifier.align(Alignment.BottomEnd).size(14.dp).background(Palette.page, Shapes.xs).padding(2.dp)) {
            Box(Modifier.fillMaxSize().background(PlayerColors[1], Shapes.xs))
        }
    }
}

