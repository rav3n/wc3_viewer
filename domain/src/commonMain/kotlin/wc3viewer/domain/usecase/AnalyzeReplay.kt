package wc3viewer.domain.usecase

import wc3viewer.domain.gamedata.GameDataRepository
import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.AnalysisOptions
import wc3viewer.domain.metric.AnalysisReport
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricId
import wc3viewer.domain.metric.MetricResult
import wc3viewer.domain.port.ReplayParser

class AnalyzeReplay(
    private val parser: ReplayParser,
    private val gameData: GameDataRepository,
    private val metrics: List<Metric<*>>,
) {
    suspend operator fun invoke(bytes: ByteArray, options: AnalysisOptions = AnalysisOptions()): AnalysisReport {
        val replay = parser.parse(bytes)
        val ctx = AnalysisContext(replay, gameData.forPatch(replay.patch), options)
        val collectors = metrics.map { it.id to it.newCollector(ctx) }
        // One pass over the events for all metrics.
        for (event in replay.events) {
            for ((_, c) in collectors) c.onEvent(event)
        }
        val results = LinkedHashMap<MetricId, MetricResult>()
        for ((id, c) in collectors) results[id] = c.finish()
        return AnalysisReport(replay, results)
    }
}
