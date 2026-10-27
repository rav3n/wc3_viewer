package wc3viewer.domain.metric

import kotlin.jvm.JvmInline
import wc3viewer.domain.gamedata.GameData
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.Replay

@JvmInline
value class MetricId(val value: String)

/** Marker; concrete results are metric data classes. */
interface MetricResult

interface Metric<R : MetricResult> {
    val id: MetricId
    fun newCollector(ctx: AnalysisContext): MetricCollector<R>
}

interface MetricCollector<R : MetricResult> {
    fun onEvent(event: GameEvent)
    fun finish(): R
}

data class AnalysisOptions(
    /** Sliding window of the APM/EPM series. */
    val apmWindowMs: Long = 60_000,
    /** Step between APM/EPM series points. */
    val apmStepMs: Long = 10_000,
    /** EPM: a repeat of the same order within this threshold is not counted. */
    val epmRepeatThresholdMs: Long = 500,
)

class AnalysisContext(
    val replay: Replay,
    val gameData: GameData,
    val options: AnalysisOptions,
)

class AnalysisReport(val replay: Replay, val results: Map<MetricId, MetricResult>) {
    inline fun <reified R : MetricResult> get(id: MetricId): R? = results[id] as? R
}
