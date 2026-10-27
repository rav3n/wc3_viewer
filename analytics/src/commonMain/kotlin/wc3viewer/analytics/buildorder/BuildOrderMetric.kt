package wc3viewer.analytics.buildorder

import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricCollector
import wc3viewer.domain.metric.MetricId
import wc3viewer.domain.metric.MetricResult
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.PlayerId

enum class BuildOrderKind { TRAIN, HERO, BUILD, RESEARCH, ITEM, CANCEL }

/** An order (not a completion) — the replay only stores player input. */
data class BuildOrderEntry(val time: GameTime, val obj: ObjectId, val kind: BuildOrderKind, val name: String?)

data class BuildOrderResult(val players: Map<PlayerId, List<BuildOrderEntry>>) : MetricResult

object BuildOrderMetric : Metric<BuildOrderResult> {
    override val id = MetricId("build-order")

    override fun newCollector(ctx: AnalysisContext): MetricCollector<BuildOrderResult> =
        object : MetricCollector<BuildOrderResult> {
            private val out = ctx.replay.players.associate { it.id to ArrayList<BuildOrderEntry>() }

            override fun onEvent(event: GameEvent) {
                val (obj, kind) = when (event) {
                    is GameEvent.Train -> event.unit to if (isHero(event.unit)) BuildOrderKind.HERO else BuildOrderKind.TRAIN
                    is GameEvent.Build -> event.building to BuildOrderKind.BUILD
                    // Town hall and tower upgrades are listed as buildings, like w3gjs does.
                    is GameEvent.UpgradeBuilding -> event.building to BuildOrderKind.BUILD
                    is GameEvent.Research -> event.upgrade to BuildOrderKind.RESEARCH
                    is GameEvent.BuyItem -> event.item to BuildOrderKind.ITEM
                    is GameEvent.CancelQueue -> event.obj to BuildOrderKind.CANCEL
                    else -> return
                }
                out[event.player]?.add(BuildOrderEntry(event.time, obj, kind, ctx.gameData.find(obj)?.name))
            }

            override fun finish() = BuildOrderResult(out)
        }

    /** Hero IDs start with an upper-case letter (W3G_FORMAT §8). */
    fun isHero(id: ObjectId): Boolean = id.code[0] in "HOEUN"
}
