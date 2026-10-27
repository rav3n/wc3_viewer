package wc3viewer.analytics.economy

import wc3viewer.domain.gamedata.Cost
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
import wc3viewer.domain.model.Race

/** Spending categories (PRD: economy / army / tech / heroes-items). */
enum class SpendCategory { ECONOMY, ARMY, TECH, HEROES, ITEMS }

/**
 * State after one priced order. [gold]/[lumber] — net spending so far (orders minus refunded cancels);
 * [refund] — this point comes from a cancel, so the totals went down.
 */
data class EconomyPoint(
    val time: GameTime,
    val gold: Int,
    val lumber: Int,
    val foodUsed: Int,
    val foodCap: Int,
    val refund: Boolean = false,
)

data class PlayerEconomy(
    /** First point is the game start (starting workers and main hall). */
    val points: List<EconomyPoint>,
    /** Net spending per category; every category is present. */
    val byCategory: Map<SpendCategory, Cost>,
    /** First time the estimated food used reached the upkeep thresholds. */
    val upkeep50: GameTime?,
    val upkeep80: GameTime?,
    /** Orders of objects missing in the game data: not priced. */
    val unpricedOrders: Int,
    /** Cancels that were refunded. */
    val cancels: Int,
) {
    val total: Cost get() = points.lastOrNull()?.let { Cost(it.gold, it.lumber) } ?: Cost(0, 0)
}

data class EconomyResult(val players: Map<PlayerId, PlayerEconomy>) : MetricResult

/**
 * ESTIMATE of spending and food from orders (PRD "Economy", ADR 9). The replay has only player input:
 * an order may fail (no gold, no food, blocked placement), and deaths are invisible. So:
 * - spending is the sum of order prices at order time, minus refunded queue cancels;
 * - food used is the sum of food of ordered units and heroes: an upper bound, deaths never lower it;
 * - food cap is the sum of food given by ordered buildings, capped at [MAX_FOOD].
 *
 * Pricing rules:
 * - building upgrades (Keep, towers) cost [GameObject.Building.upgradeCost], not the cumulative price;
 * - the player's first hero costs [GameObject.Hero.firstHeroCost] (free); a hero type is counted once,
 *   repeats are failed double orders or revivals (revival price is not modelled);
 * - research level N is the N-th research order of that upgrade; a repeat before the previous level
 *   could finish is a failed double order and is skipped;
 * - the same building ordered at the same point is one building (re-issued build order);
 * - a building upgrade repeated within its upgrade time is one upgrade.
 */
object EconomyEstimateMetric : Metric<EconomyResult> {
    override val id = MetricId("economy-estimate")
    const val MAX_FOOD = 100
    const val UPKEEP_LOW = 50
    const val UPKEEP_HIGH = 80

    /** Every race starts with 5 food of workers (5 workers, or 3 Acolytes + 1 Ghoul). */
    const val START_FOOD = 5

    private val MAIN_HALLS = mapOf(
        Race.HUMAN to ObjectId.of("htow"),
        Race.ORC to ObjectId.of("ogre"),
        Race.UNDEAD to ObjectId.of("unpl"),
        Race.NIGHT_ELF to ObjectId.of("etol"),
    )
    private val ECONOMY_BUILDINGS = (MAIN_HALLS.values + listOf("ugol", "egol").map(ObjectId::of)).toSet()
    private val TOWERS = listOf(
        "hwtw", "hgtw", "hctw", "hatw", "owtw", "otrb", "uzg1", "uzg2", "etrp",
    ).map(ObjectId::of).toSet()

    /** Category of a priced object. */
    fun categoryOf(obj: GameObject): SpendCategory = when (obj) {
        is GameObject.Unit -> if (obj.isWorker) SpendCategory.ECONOMY else SpendCategory.ARMY
        is GameObject.Hero -> SpendCategory.HEROES
        is GameObject.Item -> SpendCategory.ITEMS
        is GameObject.Upgrade, is GameObject.Ability -> SpendCategory.TECH
        is GameObject.Building -> when {
            obj.id in TOWERS || obj.upgradesFrom in TOWERS || obj.upgradesFrom == ObjectId.of("uzig") -> SpendCategory.ARMY
            obj.upgradesFrom != null -> SpendCategory.TECH // main hall tiers
            obj.id in ECONOMY_BUILDINGS || obj.foodProvided > 0 -> SpendCategory.ECONOMY
            else -> SpendCategory.TECH
        }
    }

    private class Acc(start: EconomyPoint) {
        val points = arrayListOf(start)
        var gold = 0
        var lumber = 0
        var foodUsed = start.foodUsed
        var foodCap = start.foodCap
        val byCategory = SpendCategory.entries.associateWith { intArrayOf(0, 0) }
        var upkeep50: GameTime? = null
        var upkeep80: GameTime? = null
        var unpriced = 0
        var cancels = 0
        var freeHeroUsedBy: ObjectId? = null
        val heroes = HashSet<ObjectId>()
        /** Research orders per upgrade and the time the last counted level could finish. */
        val researchLevel = HashMap<ObjectId, Int>()
        val researchBusyUntil = HashMap<ObjectId, Long>()
        val upgradeBusyUntil = HashMap<ObjectId, Long>()
        val builtAt = HashSet<Triple<ObjectId, Int, Int>>()
        /** Priced orders that a cancel can refund: object → (price, food) of each outstanding order. */
        val outstanding = HashMap<ObjectId, ArrayDeque<Pair<Cost, Int>>>()
    }

    override fun newCollector(ctx: AnalysisContext): MetricCollector<EconomyResult> =
        object : MetricCollector<EconomyResult> {
            private val data = ctx.gameData
            private val acc = ctx.replay.players.associate { p ->
                val hall = MAIN_HALLS[p.effectiveRace]?.let { data.find(it) as? GameObject.Building }
                p.id to Acc(EconomyPoint(GameTime(0), 0, 0, START_FOOD, hall?.foodProvided ?: 0))
            }

            override fun onEvent(event: GameEvent) {
                val a = acc[event.player] ?: return
                when (event) {
                    is GameEvent.Train -> train(a, event)
                    is GameEvent.Build -> build(a, event)
                    is GameEvent.UpgradeBuilding -> upgrade(a, event)
                    is GameEvent.Research -> research(a, event)
                    is GameEvent.BuyItem -> {
                        val item = data.find(event.item) as? GameObject.Item ?: return unpriced(a)
                        spend(a, event.time, item.cost, SpendCategory.ITEMS)
                    }
                    is GameEvent.CancelQueue -> cancel(a, event)
                    else -> Unit
                }
            }

            private fun unpriced(a: Acc) {
                a.unpriced++
            }

            private fun train(a: Acc, e: GameEvent.Train) {
                when (val obj = data.find(e.unit)) {
                    is GameObject.Unit -> {
                        a.foodUsed += obj.food
                        spend(a, e.time, obj.cost, categoryOf(obj))
                        a.outstanding.getOrPut(obj.id) { ArrayDeque() }.addLast(obj.cost to obj.food)
                    }
                    is GameObject.Hero -> {
                        if (!a.heroes.add(obj.id)) return
                        val price = if (a.freeHeroUsedBy == null) obj.firstHeroCost.also { a.freeHeroUsedBy = obj.id } else obj.cost
                        a.foodUsed += obj.food
                        spend(a, e.time, price, SpendCategory.HEROES)
                        a.outstanding.getOrPut(obj.id) { ArrayDeque() }.addLast(price to obj.food)
                    }
                    else -> unpriced(a)
                }
            }

            private fun build(a: Acc, e: GameEvent.Build) {
                val b = data.find(e.building) as? GameObject.Building ?: return unpriced(a)
                // A re-issued order for the same spot is still one building.
                if (!a.builtAt.add(Triple(b.id, e.x.toInt(), e.y.toInt()))) return
                a.foodCap += b.foodProvided
                spend(a, e.time, b.cost, categoryOf(b))
            }

            private fun upgrade(a: Acc, e: GameEvent.UpgradeBuilding) {
                val b = data.find(e.building) as? GameObject.Building ?: return unpriced(a)
                if (e.time.ms < (a.upgradeBusyUntil[b.id] ?: Long.MIN_VALUE)) return
                a.upgradeBusyUntil[b.id] = e.time.ms + b.buildTimeSec * 1000L
                val from = b.upgradesFrom?.let { data.find(it) as? GameObject.Building }
                val price = b.upgradeCost ?: b.cost
                val foodDelta = b.foodProvided - (from?.foodProvided ?: 0)
                a.foodCap += foodDelta
                spend(a, e.time, price, categoryOf(b))
                a.outstanding.getOrPut(b.id) { ArrayDeque() }.addLast(price to -foodDelta)
            }

            private fun research(a: Acc, e: GameEvent.Research) {
                val u = data.find(e.upgrade) as? GameObject.Upgrade ?: return unpriced(a)
                val level = a.researchLevel[u.id] ?: 0
                if (level >= u.levels.size) return
                if (e.time.ms < (a.researchBusyUntil[u.id] ?: Long.MIN_VALUE)) return
                a.researchLevel[u.id] = level + 1
                a.researchBusyUntil[u.id] = e.time.ms + (u.researchTimeSec.getOrNull(level) ?: 0) * 1000L
                spend(a, e.time, u.levels[level], SpendCategory.TECH)
                a.outstanding.getOrPut(u.id) { ArrayDeque() }.addLast(u.levels[level] to 0)
            }

            private fun cancel(a: Acc, e: GameEvent.CancelQueue) {
                // Refund only what we priced; the last order of that object is the likeliest one in the queue.
                val (price, food) = a.outstanding[e.obj]?.removeLastOrNull() ?: return
                val obj = data.find(e.obj) ?: return
                when (obj) {
                    is GameObject.Hero -> {
                        a.heroes.remove(obj.id)
                        if (a.freeHeroUsedBy == obj.id) a.freeHeroUsedBy = null
                        a.foodUsed -= food
                    }
                    is GameObject.Upgrade -> {
                        a.researchLevel[obj.id] = ((a.researchLevel[obj.id] ?: 1) - 1).coerceAtLeast(0)
                        a.researchBusyUntil.remove(obj.id)
                    }
                    is GameObject.Building -> {
                        a.foodCap += food // food holds the negated cap delta of the upgrade
                        a.upgradeBusyUntil.remove(obj.id)
                    }
                    else -> a.foodUsed -= food
                }
                a.cancels++
                spend(a, e.time, Cost(-price.gold, -price.lumber), categoryOf(obj), refund = true)
            }

            private fun spend(a: Acc, time: GameTime, cost: Cost, category: SpendCategory, refund: Boolean = false) {
                a.gold += cost.gold
                a.lumber += cost.lumber
                a.byCategory.getValue(category).let { it[0] += cost.gold; it[1] += cost.lumber }
                if (a.upkeep50 == null && a.foodUsed >= UPKEEP_LOW) a.upkeep50 = time
                if (a.upkeep80 == null && a.foodUsed >= UPKEEP_HIGH) a.upkeep80 = time
                a.points += EconomyPoint(time, a.gold, a.lumber, a.foodUsed, a.foodCap.coerceAtMost(MAX_FOOD), refund)
            }

            override fun finish() = EconomyResult(
                acc.mapValues { (_, a) ->
                    PlayerEconomy(
                        points = a.points,
                        byCategory = a.byCategory.mapValues { (_, v) -> Cost(v[0], v[1]) },
                        upkeep50 = a.upkeep50,
                        upkeep80 = a.upkeep80,
                        unpricedOrders = a.unpriced,
                        cancels = a.cancels,
                    )
                },
            )
        }
}
