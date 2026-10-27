package wc3viewer.parser

import kotlinx.coroutines.runBlocking
import wc3viewer.analytics.MetricRegistry
import wc3viewer.analytics.economy.EconomyEstimateMetric
import wc3viewer.analytics.economy.EconomyResult
import wc3viewer.analytics.heroes.HeroSkillsMetric
import wc3viewer.analytics.heroes.HeroSkillsResult
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.usecase.AnalyzeReplay
import wc3viewer.gamedata.ProductionGameData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Production game data and the economy estimate on the real replays in testdata/replays/3.00. */
class GameDataOnReplaysTest {
    private val analyze = AnalyzeReplay(W3gParser(), ProductionGameData.repository, MetricRegistry.default)

    private fun objectIds(e: GameEvent): ObjectId? = when (e) {
        is GameEvent.Train -> e.unit
        is GameEvent.Build -> e.building
        is GameEvent.UpgradeBuilding -> e.building
        is GameEvent.Research -> e.upgrade
        is GameEvent.BuyItem -> e.item
        is GameEvent.LearnSkill -> e.ability
        is GameEvent.CancelQueue -> e.obj
        else -> null
    }

    @Test
    fun heroAndSkillNames() = runBlocking {
        val f = Fixtures.replays.first { it.name == "6ae0bcf801d5ffcb2ad75e54.w3g" }
        val report = analyze(f.readBytes())
        val data = ProductionGameData.repository.forPatch(report.replay.patch)
        val heroes = report.get<HeroSkillsResult>(HeroSkillsMetric.id)!!.players.values.flatMap { it.heroes }
        val hamg = heroes.first { it.heroType?.code == "Hamg" }
        assertEquals("Archmage", data.find(hamg.heroType!!)?.name)
        assertTrue(hamg.skills.any { it.ability.code == "AHbz" })
        assertEquals("Blizzard", data.find(ObjectId.of("AHbz"))?.name)
        // Every learned skill of this replay has a name.
        val unnamed = heroes.flatMap { it.skills }.filter { data.find(it.ability) == null }.map { it.ability.code }
        assertTrue(unnamed.isEmpty(), "skills without a name: $unnamed")
    }

    /** Share of object IDs in orders that get a name; printed per replay for the report. */
    @Test
    fun nameCoverageOnAllReplays() = runBlocking {
        var total = 0
        var named = 0
        val missing = HashMap<String, Int>()
        for (f in Fixtures.replays) {
            val replay = W3gParser().parse(f.readBytes())
            val data = ProductionGameData.repository.forPatch(replay.patch)
            for (e in replay.events) {
                val id = objectIds(e) ?: continue
                total++
                if (data.find(id) != null) named++ else missing.merge(id.code, 1, Int::plus)
            }
        }
        val pct = named * 100.0 / total
        println("name coverage: $named / $total = ${"%.2f".format(pct)}% over ${Fixtures.replays.size} replays")
        println("unnamed IDs: " + missing.entries.sortedByDescending { it.value }.joinToString { "${it.key}×${it.value}" })
        assertTrue(pct >= 95.0, "name coverage $pct%")
    }

    @Test
    fun economyIsNonNegativeAndGrowsExceptAtCancels() = runBlocking {
        for (f in Fixtures.replays) {
            val report = analyze(f.readBytes())
            val eco = report.get<EconomyResult>(EconomyEstimateMetric.id)!!
            for ((pid, p) in eco.players) {
                val who = "${f.name} p${pid.value}"
                assertTrue(p.points.size > 1, "$who: no priced orders")
                for (pt in p.points) {
                    assertTrue(pt.gold >= 0 && pt.lumber >= 0, "$who: negative spending at ${pt.time}")
                    assertTrue(pt.foodUsed >= 0 && pt.foodCap in 0..EconomyEstimateMetric.MAX_FOOD, "$who: food at ${pt.time}")
                }
                for ((a, b) in p.points.zipWithNext()) {
                    assertTrue(b.time >= a.time, "$who: time order")
                    if (!b.refund) assertTrue(b.gold >= a.gold && b.lumber >= a.lumber, "$who: spending fell at ${b.time} without a cancel")
                }
                assertEquals(p.total.gold, p.byCategory.values.sumOf { it.gold }, "$who: categories sum")
                assertTrue(p.total.gold > 0, "$who: total gold ${p.total.gold}")
            }
        }
    }
}
