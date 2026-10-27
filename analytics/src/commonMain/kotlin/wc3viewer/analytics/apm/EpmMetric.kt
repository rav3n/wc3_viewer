package wc3viewer.analytics.apm

import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricCollector
import wc3viewer.domain.metric.MetricId
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.model.SelectMode
import wc3viewer.domain.model.UnitRef

data class EpmResult(override val players: List<PlayerRate>) : ActionRateResult

/**
 * EPM = APM without repeats (user's rule):
 * - the same unit order (same order ID and target) within [AnalysisOptions.epmRepeatThresholdMs]
 *   after the previous identical order; the window is measured from the previous repeat, so a burst of
 *   identical clicks every 100 ms counts once. Production orders (train, build, research, buy, learn)
 *   are not filtered: queueing several units with quick clicks is effective;
 * - selecting the same set of units again (deselect+select pair or a plain select);
 * - selecting the same hotkey group again right after itself.
 */
object EpmMetric : Metric<EpmResult> {
    override val id = MetricId("epm")

    override fun newCollector(ctx: AnalysisContext): MetricCollector<EpmResult> = EpmCollector(ctx)

    private class PlayerState {
        var lastOrderKey: Any? = null
        var lastOrderMs = Long.MIN_VALUE
        var lastSelection: Set<UnitRef>? = null
        var pendingDeselect: GameTime? = null
        var lastWasHotkey: Int? = null
    }

    private class EpmCollector(ctx: AnalysisContext) : MetricCollector<EpmResult> {
        private val threshold = ctx.options.epmRepeatThresholdMs
        private val acc = RateAccumulator(ctx.replay, ctx.options.apmWindowMs, ctx.options.apmStepMs)
        private val state = HashMap<PlayerId, PlayerState>()

        override fun onEvent(event: GameEvent) {
            val s = state.getOrPut(event.player) { PlayerState() }
            if (event is GameEvent.Select) {
                onSelect(s, event)
                return
            }
            flushDeselect(s, event.player)
            if (!event.countsForApm) return
            if (event is GameEvent.HotkeySelect) {
                if (s.lastWasHotkey != event.group) acc.add(event.player, event.time)
                s.lastWasHotkey = event.group
                return
            }
            s.lastWasHotkey = null
            val key = orderKey(event)
            if (key != null) {
                val repeat = key == s.lastOrderKey && event.time.ms - s.lastOrderMs < threshold
                s.lastOrderKey = key
                s.lastOrderMs = event.time.ms
                if (repeat) return
            }
            acc.add(event.player, event.time)
        }

        private fun onSelect(s: PlayerState, e: GameEvent.Select) {
            s.lastWasHotkey = null
            when {
                e.mode == SelectMode.REMOVE -> {
                    flushDeselect(s, e.player)
                    s.pendingDeselect = e.time
                }
                else -> {
                    val set = e.units.toSet()
                    val paired = s.pendingDeselect != null
                    s.pendingDeselect = null
                    // A pair or a plain select counts once, unless it re-selects the same units.
                    if ((paired || e.countsForApm) && set != s.lastSelection) acc.add(e.player, e.time)
                    s.lastSelection = set
                }
            }
        }

        /** A deselect not followed by a select is a real action of its own. */
        private fun flushDeselect(s: PlayerState, player: PlayerId) {
            s.pendingDeselect?.let { acc.add(player, it) }
            s.pendingDeselect = null
        }

        override fun finish(): EpmResult {
            for ((p, s) in state) flushDeselect(s, p)
            return EpmResult(acc.build())
        }
    }

    /** Identity of an order for repeat detection; null for non-order actions. */
    private fun orderKey(e: GameEvent): Any? = when (e) {
        is GameEvent.Order -> e.order to e.target
        else -> null
    }
}
