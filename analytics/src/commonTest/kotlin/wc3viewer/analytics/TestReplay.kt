package wc3viewer.analytics

import wc3viewer.domain.metric.AnalysisContext
import wc3viewer.domain.metric.AnalysisOptions
import wc3viewer.domain.metric.Metric
import wc3viewer.domain.metric.MetricResult
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.MapInfo
import wc3viewer.domain.model.MatchResult
import wc3viewer.domain.model.Patch
import wc3viewer.domain.model.Player
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.model.Race
import wc3viewer.domain.model.Replay
import wc3viewer.gamedata.fixture.TestFixtureGameData

val P1 = PlayerId(1)
val P2 = PlayerId(2)

fun t(ms: Long) = GameTime(ms)

fun replayOf(events: List<GameEvent>, durationMs: Long = 60_000): Replay {
    val last = { p: PlayerId -> GameTime(events.filter { it.player == p }.maxOfOrNull { it.time.ms } ?: 0) }
    return Replay(
        patch = Patch(3, 0, 7003),
        duration = GameTime(durationMs),
        headerDuration = GameTime(durationMs),
        map = MapInfo("Maps/test.w3x", "test"),
        gameName = "test",
        players = listOf(
            Player(P1, "A", Race.HUMAN, null, 0, 0, false, last(P1)),
            Player(P2, "B", Race.ORC, null, 1, 1, false, last(P2)),
        ),
        events = events.sortedBy { it.time.ms },
        chat = emptyList(),
        leaves = emptyList(),
        result = MatchResult.Unknown,
        diagnostics = emptyList(),
    )
}

fun <R : MetricResult> run(metric: Metric<R>, events: List<GameEvent>, options: AnalysisOptions = AnalysisOptions()): R {
    val replay = replayOf(events)
    val c = metric.newCollector(AnalysisContext(replay, TestFixtureGameData.data, options))
    replay.events.forEach(c::onEvent)
    return c.finish()
}
