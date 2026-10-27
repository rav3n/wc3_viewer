package wc3viewer.presentation

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import wc3viewer.analytics.MetricRegistry
import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.AnalysisOptions
import wc3viewer.domain.metric.AnalysisReport
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.MapInfo
import wc3viewer.domain.model.MatchResult
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Patch
import wc3viewer.domain.model.Player
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.model.Race
import wc3viewer.domain.model.Replay
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.gamedata.fixture.TestFixtureGameData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReplayViewModelTest {
    private val replay = Replay(
        patch = Patch(3, 0, 7003),
        duration = GameTime(125_000), headerDuration = GameTime(125_000),
        map = MapInfo("Maps/W3Champions\\44_w3c_260919_1153_AutumnLeaves_v2.0.w3x", "FLO"),
        gameName = "g",
        players = listOf(
            Player(PlayerId(1), "A#1", Race.RANDOM, Race.HUMAN, 0, 0, false, GameTime(60_000)),
            Player(PlayerId(2), "B#2", Race.ORC, null, 1, 1, false, GameTime(60_000)),
        ),
        events = listOf(
            GameEvent.Train(GameTime(1000), PlayerId(1), ObjectId.of("hpea"), false),
            GameEvent.HotkeySelect(GameTime(61_000), PlayerId(1), 0),
            GameEvent.HotkeySelect(GameTime(62_000), PlayerId(1), 0),
        ),
        chat = emptyList(), leaves = emptyList(),
        result = MatchResult.Winner(PlayerId(2)),
        diagnostics = emptyList(),
    )

    private fun report(): AnalysisReport {
        val ctx = AnalysisContext(replay, TestFixtureGameData.data, AnalysisOptions())
        val results = MetricRegistry.default.associate { m ->
            val c = m.newCollector(ctx)
            replay.events.forEach(c::onEvent)
            m.id to c.finish()
        }
        return AnalysisReport(replay, results)
    }

    @Test
    fun loadsReport() = runTest {
        val vm = ReplayViewModel({ report() }, TestScope(StandardTestDispatcher(testScheduler)), TestFixtureGameData.repository)
        vm.onFileSelected("x.w3g", ByteArray(0))
        assertIs<ReplayUiState.Loading>(vm.state.value)
        advanceUntilIdle()
        val s = assertIs<ReplayUiState.Loaded>(vm.state.value)
        assertEquals("AutumnLeaves_v2.0", s.overview.map)
        assertEquals("2:05", s.overview.duration)
        assertEquals("B#2", s.overview.winner)
        assertEquals("Random → Human", s.overview.players[0].race)
        assertEquals("Peasant", s.sections.buildOrders.getValue(1).single().name)
        assertEquals(3, s.sections.rates.first().apm)
        assertEquals(RaceUi.HUMAN, s.overview.players[0].raceKey)
        val heat = s.sections.hotkeyHeat.getValue(1)
        assertEquals(listOf(1), heat.keys)
        assertEquals(2, heat.counts.single()[1])
    }

    @Test
    fun namesFromGameData() {
        val p1 = PlayerId(1)
        val hamg = ObjectId.of("Hamg")
        val r = replay.copy(
            events = listOf(
                GameEvent.Train(GameTime(1000), p1, hamg, false),
                GameEvent.LearnSkill(GameTime(2000), p1, null, hamg, ObjectId.of("AHbz")),
                GameEvent.LearnSkill(GameTime(3000), p1, null, null, ObjectId.of("AXyz")), // unknown skill and hero
            ),
        )
        val ctx = AnalysisContext(r, TestFixtureGameData.data, AnalysisOptions())
        val report = AnalysisReport(
            r,
            MetricRegistry.default.associate { m -> m.id to m.newCollector(ctx).also { c -> r.events.forEach(c::onEvent) }.finish() },
        )
        val s = ReportMapper.sections(report, TestFixtureGameData.data)
        val heroes = s.heroes.getValue(1)
        assertEquals("Archmage", heroes[0].hero)
        assertTrue(heroes[0].hasName)
        assertEquals("Blizzard", heroes[0].skills.single().name)
        assertEquals("Unknown hero", heroes[1].hero)
        assertEquals(null, heroes[1].skills.single().name)
        assertEquals("Archmage", s.heroOrders.getValue(1).single().name)
        assertEquals("Archmage", s.timings.getValue(1).heroes.single().name)
        // Economy: the first hero is free; series end at the game end.
        val eco = s.economy.getValue(1)
        assertEquals(0, eco.totalGold)
        assertEquals(125f, eco.total.xs.last())
    }

    @Test
    fun timingsFromBuildOrder() {
        fun row(sec: Int, code: String, kind: ObjectKind) =
            BuildOrderRow("$sec", code, "", sec.toFloat(), code, kind)
        val t = ReportMapper.timings(
            listOf(
                row(10, "Hpal", ObjectKind.HERO), row(200, "hkee", ObjectKind.BUILDING),
                row(300, "htow", ObjectKind.BUILDING), row(400, "Hamg", ObjectKind.HERO), row(500, "Hpal", ObjectKind.HERO),
            ),
        )
        assertEquals(listOf("Hpal", "Hamg"), t.heroes.map { it.code })
        assertEquals(200f, t.tier2?.timeSec)
        assertEquals(null, t.tier3)
        assertEquals("htow", t.expansion?.code)
    }

    @Test
    fun failsWithClearMessage() = runTest {
        val vm = ReplayViewModel(
            { throw ReplayFormatException(ReplayFormatException.Kind.EMPTY_FILE, "empty") },
            TestScope(StandardTestDispatcher(testScheduler)),
            TestFixtureGameData.repository,
        )
        vm.onFileSelected("x.w3g", ByteArray(0))
        advanceUntilIdle()
        val s = assertIs<ReplayUiState.Failed>(vm.state.value)
        assertTrue(s.message.startsWith("File is empty"))
        assertEquals("The file is empty", s.title)
        assertTrue(s.hints.isNotEmpty())
    }

    @Test
    fun everyErrorKindHasText() {
        for (k in ReplayFormatException.Kind.entries) {
            assertTrue(ReportMapper.errorMessage(ReplayFormatException(k, "details")).isNotBlank())
        }
    }
}
