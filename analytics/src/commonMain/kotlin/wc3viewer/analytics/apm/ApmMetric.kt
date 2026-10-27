package wc3viewer.analytics.apm

import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricCollector
import wc3viewer.domain.metric.MetricId
import wc3viewer.domain.model.GameEvent

data class ApmResult(override val players: List<PlayerRate>) : ActionRateResult

/** APM by w3gjs rules: the mapper marks counted events with [GameEvent.countsForApm]. */
object ApmMetric : Metric<ApmResult> {
    override val id = MetricId("apm")

    override fun newCollector(ctx: AnalysisContext): MetricCollector<ApmResult> = object : MetricCollector<ApmResult> {
        private val acc = RateAccumulator(ctx.replay, ctx.options.apmWindowMs, ctx.options.apmStepMs)

        override fun onEvent(event: GameEvent) {
            if (event.countsForApm) acc.add(event.player, event.time)
        }

        override fun finish() = ApmResult(acc.build())
    }
}
