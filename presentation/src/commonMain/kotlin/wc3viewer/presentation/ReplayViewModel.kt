package wc3viewer.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wc3viewer.domain.gamedata.GameDataRepository
import wc3viewer.domain.metric.AnalysisReport

/**
 * Plain StateFlow holder: androidx.lifecycle gives nothing on the web.
 * [gameData] — names for the UI (hero skills, heroes, timings); the same repository the analysis uses.
 */
class ReplayViewModel(
    private val analyze: suspend (ByteArray) -> AnalysisReport,
    private val scope: CoroutineScope,
    private val gameData: GameDataRepository,
) {
    private val _state = MutableStateFlow<ReplayUiState>(ReplayUiState.Empty)
    val state: StateFlow<ReplayUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onFileSelected(name: String, bytes: ByteArray) {
        job?.cancel()
        _state.value = ReplayUiState.Loading(name)
        job = scope.launch {
            _state.value = try {
                val report = analyze(bytes)
                ReplayUiState.Loaded(name, ReportMapper.overview(report), ReportMapper.sections(report, gameData.forPatch(report.replay.patch)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ReplayUiState.Failed(
                    name, ReportMapper.errorMessage(e), emptyList(), ReportMapper.errorTitle(e), ReportMapper.errorHints(e),
                )
            }
        }
    }
}
