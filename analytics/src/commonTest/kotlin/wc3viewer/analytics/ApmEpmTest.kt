package wc3viewer.analytics

import wc3viewer.analytics.apm.ApmMetric
import wc3viewer.analytics.apm.EpmMetric
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameEvent.HotkeySelect
import wc3viewer.domain.model.GameEvent.Order
import wc3viewer.domain.model.GameEvent.Select
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.SelectMode
import wc3viewer.domain.model.Target
import wc3viewer.domain.model.UnitRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApmEpmTest {
    private val rightClick = 0x000D0003
    private fun click(ms: Long, x: Float = 1f, p: wc3viewer.domain.model.PlayerId = P1) =
        Order(t(ms), p, rightClick, null, Target.Point(x, 2f), false)
    private val a = UnitRef(1, 1)
    private val b = UnitRef(2, 2)

    private fun epm(vararg e: GameEvent) = run(EpmMetric, e.toList()).of(P1)!!.actions
    private fun apm(vararg e: GameEvent) = run(ApmMetric, e.toList()).of(P1)!!.actions

    @Test
    fun apmCountsDeselectSelectPairOnce() {
        val pair = arrayOf(
            Select(t(100), P1, SelectMode.REMOVE, listOf(a), countsForApm = true),
            Select(t(100), P1, SelectMode.ADD, listOf(b), countsForApm = false),
        )
        assertEquals(1, apm(*pair))
    }

    @Test
    fun apmAverageUsesLastCommandTime() {
        // 30 actions, the last at 30 s → 60 APM.
        val events = (1..30).map { click(it * 1000L, x = it.toFloat()) }
        val r = run(ApmMetric, events).of(P1)!!
        assertEquals(60, r.average)
        assertTrue(r.series.isNotEmpty())
    }

    @Test
    fun repeatedOrderWithinThreshold() {
        assertEquals(1, epm(click(0), click(499)))
        assertEquals(2, epm(click(0), click(500)))
        assertEquals(2, epm(click(0), click(100, x = 5f)))
    }

    @Test
    fun burstOfIdenticalClicksCountsOnce() {
        assertEquals(1, epm(*Array(10) { click(it * 100L) }))
    }

    @Test
    fun repeatedHotkeySelect() {
        assertEquals(1, epm(HotkeySelect(t(0), P1, 1), HotkeySelect(t(50), P1, 1)))
        assertEquals(3, epm(HotkeySelect(t(0), P1, 1), HotkeySelect(t(50), P1, 2), HotkeySelect(t(90), P1, 1)))
    }

    @Test
    fun reselectingSameUnits() {
        fun sel(ms: Long, units: List<UnitRef>) = arrayOf(
            Select(t(ms), P1, SelectMode.REMOVE, listOf(a, b), countsForApm = true),
            Select(t(ms), P1, SelectMode.ADD, units, countsForApm = false),
        )
        assertEquals(1, epm(*sel(0, listOf(a, b)), *sel(1000, listOf(a, b))))
        assertEquals(1, epm(*sel(0, listOf(a, b)), *sel(1000, listOf(b, a))))
        assertEquals(2, epm(*sel(0, listOf(a)), *sel(1000, listOf(b))))
    }

    @Test
    fun repeatsOfDifferentPlayersAreNotMerged() {
        val r = run(EpmMetric, listOf(click(0), click(100, p = P2)))
        assertEquals(1, r.of(P1)!!.actions)
        assertEquals(1, r.of(P2)!!.actions)
    }

    @Test
    fun productionOrdersAreNotFiltered() {
        val peasant = ObjectId.of("hpea")
        assertEquals(3, epm(*Array(3) { GameEvent.Train(t(it * 100L), P1, peasant, false) }))
    }

    @Test
    fun thresholdIsAParameter() {
        val opts = wc3viewer.domain.metric.AnalysisOptions(epmRepeatThresholdMs = 1000)
        assertEquals(1, run(EpmMetric, listOf(click(0), click(900)), opts).of(P1)!!.actions)
    }
}
