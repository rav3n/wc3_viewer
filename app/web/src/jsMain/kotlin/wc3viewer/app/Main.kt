package wc3viewer.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams
import kotlinx.coroutines.MainScope
import wc3viewer.analytics.MetricRegistry
import wc3viewer.app.platform.BrowserFileSource
import androidx.compose.runtime.mutableStateOf
import wc3viewer.app.ui.App
import wc3viewer.app.ui.theme.Fonts
import wc3viewer.app.ui.match.MatchTab
import wc3viewer.domain.usecase.AnalyzeReplay
import wc3viewer.gamedata.ProductionGameData
import wc3viewer.parser.W3gParser
import wc3viewer.presentation.ReplayViewModel

/** Composition root: manual DI (ADR 13). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val gameData = ProductionGameData.repository
    val analyze = AnalyzeReplay(W3gParser(), gameData, MetricRegistry.default)
    val viewModel = ReplayViewModel({ bytes -> analyze(bytes) }, MainScope(), gameData)
    Fonts.load()
    val dragging = mutableStateOf(false)
    val files = BrowserFileSource(onFile = viewModel::onFileSelected, onDragActive = { dragging.value = it })
    files.installDropTarget()
    val params = URLSearchParams(window.location.search)
    // Dev helpers: ?replay=<url> loads a replay from the same origin on start; ?tab=<key> opens a match tab.
    params.get("replay")?.let(files::fetch)
    val tab = MatchTab.of(params.get("tab"))
    ComposeViewport(document.body!!) {
        App(viewModel, dragging, onPickFile = files::pick, initialTab = tab)
    }
}
