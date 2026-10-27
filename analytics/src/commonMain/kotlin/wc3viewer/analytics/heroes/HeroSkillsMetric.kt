package wc3viewer.analytics.heroes

import wc3viewer.analytics.buildorder.BuildOrderMetric
import wc3viewer.domain.gamedata.GameObject
import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricCollector
import wc3viewer.domain.metric.MetricId
import wc3viewer.domain.metric.MetricResult
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.PlayerId

data class SkillPick(val time: GameTime, val ability: ObjectId)

data class HeroSkills(
    /** Hero type: from the replay selection, else from game data (ability → hero); null if unknown. */
    val heroType: ObjectId?,
    val firstSkill: GameTime,
    /** Current skill picks (after the last detected retraining, if any). */
    val skills: List<SkillPick>,
    /** Time of the first re-learned skill after a Tome of Retraining; null if none was detected. */
    val retrainedAt: GameTime? = null,
) {
    /**
     * ESTIMATE, not an exact value: one level per learned skill. A failed learn order (no skill point)
     * is indistinguishable in the replay, so this may overshoot (TEST_STRATEGY §6).
     */
    val levelEstimate: Int get() = skills.size
}

data class HeroOrder(val time: GameTime, val hero: ObjectId)

data class PlayerHeroes(
    /** Ordered by the first learned skill: the hero surely exists by then. */
    val heroes: List<HeroSkills>,
    /** Hire/train orders. An order is not the hero's appearance; keep both. */
    val orders: List<HeroOrder>,
)

data class HeroSkillsResult(val players: Map<PlayerId, PlayerHeroes>) : MetricResult

/**
 * Hero skill order. A repeat of the same skill by the same hero within [DOUBLE_CLICK_MS] is one pick:
 * players often double-click and the second order fails (TEST_STRATEGY §6).
 *
 * Tome of Retraining ('tret'/'tert'): only the purchase is visible in the replay. Like w3gjs
 * `getRetrainingIndex`, a burst of at least 3 learn orders within [RETRAIN_WINDOW_MS] that starts within
 * [RETRAIN_WINDOW_MS] after a purchase resets the hero's skills. Inside that burst repeats are legal
 * (points are re-spent quickly), so they are not deduplicated.
 */
object HeroSkillsMetric : Metric<HeroSkillsResult> {
    override val id = MetricId("hero-skills")
    const val DOUBLE_CLICK_MS = 500L
    const val RETRAIN_WINDOW_MS = 60_000L
    private val TOMES = setOf(ObjectId.of("tret"), ObjectId.of("tert"))

    private class Acc {
        /** Raw learn orders, without deduplication. */
        val raw = ArrayList<SkillPick>()
    }

    /** Index of the first skill re-learned after the last tome purchase, or -1 (w3gjs getRetrainingIndex). */
    internal fun retrainingIndex(raw: List<SkillPick>, purchases: List<Long>): Int {
        if (raw.size < 3 || purchases.isEmpty()) return -1
        var found = -1
        var start = 0
        var inRange = 0
        for (i in 1 until raw.size) {
            if (raw[i].time.ms - raw[start].time.ms < RETRAIN_WINDOW_MS) {
                inRange++
            } else {
                inRange = 0
                start = i
            }
            val t = raw[start].time.ms
            if (inRange == 2 && purchases.any { it <= t && t - it <= RETRAIN_WINDOW_MS }) found = start
        }
        return found
    }

    /** Removes double clicks; picks inside [keepFrom, keepUntil) are kept as is (retraining burst). */
    internal fun dedup(raw: List<SkillPick>, burstEndMs: Long = Long.MIN_VALUE): List<SkillPick> {
        val out = ArrayList<SkillPick>()
        val last = HashMap<ObjectId, Long>()
        for (p in raw) {
            val prev = last[p.ability]
            last[p.ability] = p.time.ms
            val inBurst = p.time.ms < burstEndMs
            if (!inBurst && prev != null && p.time.ms - prev < DOUBLE_CLICK_MS) continue
            out += p
        }
        return out
    }

    override fun newCollector(ctx: AnalysisContext): MetricCollector<HeroSkillsResult> =
        object : MetricCollector<HeroSkillsResult> {
            private val heroes = ctx.replay.players.associate { it.id to LinkedHashMap<ObjectId?, Acc>() }
            private val orders = ctx.replay.players.associate { it.id to ArrayList<HeroOrder>() }
            private val tomes = ctx.replay.players.associate { it.id to ArrayList<Long>() }

            override fun onEvent(event: GameEvent) {
                when (event) {
                    is GameEvent.Train -> if (BuildOrderMetric.isHero(event.unit)) {
                        orders[event.player]?.add(HeroOrder(event.time, event.unit))
                    }
                    is GameEvent.LearnSkill -> {
                        val map = heroes[event.player] ?: return
                        val type = event.heroType
                            ?: (ctx.gameData.find(event.ability) as? GameObject.Ability)?.hero
                        map.getOrPut(type) { Acc() }.raw += SkillPick(event.time, event.ability)
                    }
                    is GameEvent.BuyItem -> if (event.item in TOMES) tomes[event.player]?.add(event.time.ms)
                    else -> Unit
                }
            }

            override fun finish() = HeroSkillsResult(
                ctx.replay.players.associate { p ->
                    val list = heroes.getValue(p.id).filterValues { it.raw.isNotEmpty() }.map { (type, acc) ->
                        val idx = retrainingIndex(acc.raw, tomes.getValue(p.id))
                        if (idx < 0) {
                            HeroSkills(type, acc.raw.first().time, dedup(acc.raw))
                        } else {
                            val from = acc.raw[idx].time
                            val skills = dedup(acc.raw.subList(idx, acc.raw.size), from.ms + RETRAIN_WINDOW_MS)
                            HeroSkills(type, acc.raw.first().time, skills, retrainedAt = from)
                        }
                    }.sortedBy { it.firstSkill.ms }
                    p.id to PlayerHeroes(list, orders.getValue(p.id))
                },
            )
        }
}
