package wc3viewer.analytics.hotkeys

import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricCollector
import wc3viewer.domain.metric.MetricId
import wc3viewer.domain.metric.MetricResult
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.model.hotkeyKey

data class HotkeyGroupUsage(
    /** Keyboard key 0..9, `(group + 1) % 10` like w3gjs. */
    val key: Int,
    val assigned: Int,
    val selected: Int,
    /** Units in the last assignment. */
    val lastAssignedSize: Int,
    val firstAssigned: GameTime?,
)

data class HotkeyResult(val players: Map<PlayerId, List<HotkeyGroupUsage>>) : MetricResult

/** Hotkey group usage: assignments and selections per key (groups never used are omitted). */
object HotkeyMetric : Metric<HotkeyResult> {
    override val id = MetricId("hotkeys")

    private class Acc {
        var assigned = 0
        var selected = 0
        var lastSize = 0
        var first: GameTime? = null
    }

    override fun newCollector(ctx: AnalysisContext): MetricCollector<HotkeyResult> =
        object : MetricCollector<HotkeyResult> {
            private val acc = ctx.replay.players.associate { it.id to Array(10) { Acc() } }

            override fun onEvent(event: GameEvent) {
                when (event) {
                    is GameEvent.HotkeyAssign -> acc[event.player]?.getOrNull(event.group)?.let {
                        it.assigned++
                        it.lastSize = event.units.size
                        if (it.first == null) it.first = event.time
                    }
                    is GameEvent.HotkeySelect -> acc[event.player]?.getOrNull(event.group)?.let { it.selected++ }
                    else -> Unit
                }
            }

            override fun finish() = HotkeyResult(
                acc.mapValues { (_, groups) ->
                    groups.mapIndexedNotNull { g, a ->
                        if (a.assigned == 0 && a.selected == 0) null
                        else HotkeyGroupUsage(hotkeyKey(g), a.assigned, a.selected, a.lastSize, a.first)
                    }.sortedBy { if (it.key == 0) 10 else it.key }
                },
            )
        }
}
