package wc3viewer.analytics

import wc3viewer.analytics.buildorder.BuildOrderKind
import wc3viewer.analytics.buildorder.BuildOrderMetric
import wc3viewer.analytics.heroes.HeroSkillsMetric
import wc3viewer.analytics.hotkeys.HotkeyMetric
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.UnitRef
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildOrderHeroesHotkeysTest {
    private fun id(c: String) = ObjectId.of(c)

    @Test
    fun buildOrderKindsAndNames() {
        val r = run(
            BuildOrderMetric,
            listOf(
                GameEvent.Train(t(10), P1, id("hpea"), false),
                GameEvent.Build(t(20), P1, id("hhou"), 0f, 0f, false),
                GameEvent.Train(t(30), P1, id("Hamg"), false),
                GameEvent.Research(t(40), P1, id("Rhme")),
                GameEvent.UpgradeBuilding(t(50), P1, id("hkee")),
                GameEvent.BuyItem(t(60), P1, id("phea"), null),
                GameEvent.CancelQueue(t(70), P1, 0, id("hpea")),
            ),
        ).players.getValue(P1)
        assertEquals(
            listOf(BuildOrderKind.TRAIN, BuildOrderKind.BUILD, BuildOrderKind.HERO, BuildOrderKind.RESEARCH, BuildOrderKind.BUILD, BuildOrderKind.ITEM, BuildOrderKind.CANCEL),
            r.map { it.kind },
        )
        assertEquals("Peasant", r.first().name) // from the test fixture
        assertEquals("Keep", r[4].name)
    }

    @Test
    fun heroSkillsDeduplicateDoubleClicks() {
        val hamg = id("Hamg")
        fun learn(ms: Long, a: String, hero: ObjectId? = hamg) = GameEvent.LearnSkill(t(ms), P1, UnitRef(1, 1), hero, id(a))
        val r = run(
            HeroSkillsMetric,
            listOf(
                GameEvent.Train(t(1000), P1, hamg, false),
                learn(5000, "AHbz"), learn(5150, "AHbz"), // double click
                learn(9000, "AHbz"),
                learn(7000, "AOmi", id("Obla")),
            ),
        ).players.getValue(P1)
        assertEquals(listOf("Hamg", "Obla"), r.heroes.map { it.heroType?.code })
        assertEquals(2, r.heroes.first().levelEstimate)
        assertEquals(listOf(1000L), r.orders.map { it.time.ms })
    }

    @Test
    fun tomeOfRetrainingResetsSkills() {
        val hamg = id("Hamg")
        fun learn(ms: Long, a: String) = GameEvent.LearnSkill(t(ms), P1, null, hamg, id(a))
        val r = run(
            HeroSkillsMetric,
            listOf(
                learn(10_000, "AHwe"), learn(100_000, "AHbz"), learn(200_000, "AHwe"),
                GameEvent.BuyItem(t(300_000), P1, id("tret"), null),
                // Re-spent points: legal quick repeats.
                learn(310_000, "AHbz"), learn(310_150, "AHbz"), learn(310_300, "AHab"),
                // Later double click is still deduplicated.
                learn(400_000, "AHab"), learn(400_100, "AHab"),
            ),
        ).players.getValue(P1).heroes.single()
        assertEquals(310_000L, r.retrainedAt?.ms)
        assertEquals(4, r.levelEstimate)
        assertEquals(10_000L, r.firstSkill.ms)
    }

    @Test
    fun heroTypeFallsBackToGameData() {
        val r = run(HeroSkillsMetric, listOf(GameEvent.LearnSkill(t(1), P1, null, null, id("AHbz"))))
        assertEquals("Hamg", r.players.getValue(P1).heroes.single().heroType?.code)
    }

    @Test
    fun hotkeysUseKeyNumbers() {
        val r = run(
            HotkeyMetric,
            listOf(
                GameEvent.HotkeyAssign(t(1), P1, 0, listOf(UnitRef(1, 1), UnitRef(2, 2))),
                GameEvent.HotkeySelect(t(2), P1, 0),
                GameEvent.HotkeySelect(t(3), P1, 0),
                GameEvent.HotkeySelect(t(4), P1, 9),
            ),
        ).players.getValue(P1)
        assertEquals(listOf(1, 0), r.map { it.key })
        assertEquals(2, r.first().selected)
        assertEquals(2, r.first().lastAssignedSize)
    }
}
