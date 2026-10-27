package wc3viewer.analytics

import wc3viewer.analytics.economy.EconomyEstimateMetric
import wc3viewer.analytics.economy.SpendCategory
import wc3viewer.domain.gamedata.Cost
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Arithmetic on the synthetic fixture prices (TestFixtureGameData), not real balance. */
class EconomyEstimateTest {
    private fun id(c: String) = ObjectId.of(c)

    @Test
    fun sumsOrdersByCategoryWithUpgradeCostAndFreeFirstHero() {
        val e = run(
            EconomyEstimateMetric,
            listOf(
                GameEvent.Train(t(1_000), P1, id("hpea"), false), // 10/0, food 1
                GameEvent.Build(t(2_000), P1, id("hhou"), 1f, 1f, false), // 30/10, cap +6
                GameEvent.Build(t(2_500), P1, id("hhou"), 1f, 1f, false), // same spot: re-issued order
                GameEvent.Train(t(3_000), P1, id("Hamg"), false), // first hero: free, food 5
                GameEvent.Train(t(3_100), P1, id("Hamg"), false), // same hero again: not counted
                GameEvent.Train(t(4_000), P1, id("Hmkg"), false), // second hero: 400/100
                GameEvent.UpgradeBuilding(t(5_000), P1, id("hkee")), // upgradeCost 300/200, cap 12 → 14
                GameEvent.Research(t(6_000), P1, id("Rhme")), // level 1: 50/50
                GameEvent.BuyItem(t(7_000), P1, id("phea"), null), // 100/0
                GameEvent.Train(t(8_000), P1, id("zzzz"), false), // unknown: unpriced
            ),
        ).players.getValue(P1)
        assertEquals(Cost(10 + 30 + 400 + 300 + 50 + 100, 10 + 100 + 200 + 50), e.total)
        assertEquals(Cost(10 + 30, 10), e.byCategory[SpendCategory.ECONOMY])
        assertEquals(Cost(400, 100), e.byCategory[SpendCategory.HEROES])
        assertEquals(Cost(300 + 50, 200 + 50), e.byCategory[SpendCategory.TECH])
        assertEquals(Cost(100, 0), e.byCategory[SpendCategory.ITEMS])
        assertEquals(Cost(0, 0), e.byCategory[SpendCategory.ARMY])
        assertEquals(1, e.unpricedOrders)
        val last = e.points.last()
        assertEquals(5 + 1 + 5 + 5, last.foodUsed)
        assertEquals(12 + 6 + 2, last.foodCap)
        // Start point: 5 workers, Town Hall food.
        assertEquals(5, e.points.first().foodUsed)
        assertEquals(12, e.points.first().foodCap)
    }

    @Test
    fun researchLevelsAndDoubleOrders() {
        val e = run(
            EconomyEstimateMetric,
            listOf(
                GameEvent.Research(t(1_000), P1, id("Rhme")), // level 1, 40 s
                GameEvent.Research(t(1_200), P1, id("Rhme")), // double order while researching: skipped
                GameEvent.Research(t(50_000), P1, id("Rhme")), // only one level in the fixture: skipped
            ),
        ).players.getValue(P1)
        assertEquals(Cost(50, 50), e.total)
    }

    @Test
    fun cancelRefundsAndFreesTheFirstHero() {
        val e = run(
            EconomyEstimateMetric,
            listOf(
                GameEvent.Train(t(1_000), P1, id("hfoo"), false),
                GameEvent.Train(t(1_100), P1, id("hfoo"), false),
                GameEvent.CancelQueue(t(2_000), P1, 1, id("hfoo")),
                GameEvent.CancelQueue(t(2_100), P1, 0, id("hhou")), // nothing priced to refund
                GameEvent.Train(t(3_000), P1, id("Hamg"), false),
                GameEvent.CancelQueue(t(3_500), P1, 0, id("Hamg")),
                GameEvent.Train(t(4_000), P1, id("Hmkg"), false), // still the first hero: free
            ),
        ).players.getValue(P1)
        assertEquals(Cost(20, 0), e.total)
        assertEquals(2, e.cancels)
        assertEquals(5 + 2 + 5, e.points.last().foodUsed)
        assertEquals(listOf(false, false, false, true, false, true, false), e.points.map { it.refund })
    }

    @Test
    fun upkeepThresholds() {
        // 23 footmen × 2 food + 5 start = 51 → crosses 50 at the 23rd order.
        val events = (1..40).map { GameEvent.Train(t(it * 1_000L), P1, id("hfoo"), false) }
        val e = run(EconomyEstimateMetric, events).players.getValue(P1)
        assertEquals(23_000L, e.upkeep50?.ms)
        assertEquals(38_000L, e.upkeep80?.ms)
        assertNull(run(EconomyEstimateMetric, events.take(5)).players.getValue(P1).upkeep50)
    }
}
