package wc3viewer.analytics.apm

import wc3viewer.domain.metric.MetricResult
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.model.Replay
import kotlin.math.roundToInt

/** One point of an actions-per-minute series: rate over the window ending at [time]. */
data class RatePoint(val time: GameTime, val perMinute: Float)

data class PlayerRate(
    val player: PlayerId,
    val actions: Int,
    /** actions / minutes until the player's last command block (w3gjs rule), rounded. */
    val average: Int,
    val series: List<RatePoint>,
)

/** Shared shape of APM and EPM results. */
interface ActionRateResult : MetricResult {
    val players: List<PlayerRate>
    fun of(player: PlayerId): PlayerRate? = players.firstOrNull { it.player == player }
}

/** Collects counted action times per player and turns them into averages and a sliding-window series. */
internal class RateAccumulator(private val replay: Replay, private val windowMs: Long, private val stepMs: Long) {
    private val times = replay.players.associate { it.id to ArrayList<Long>() }

    fun add(player: PlayerId, time: GameTime) {
        times[player]?.add(time.ms)
    }

    fun build(): List<PlayerRate> = replay.players.map { p ->
        val t = times.getValue(p.id)
        val lastMs = p.lastCommandTime.ms
        val average = if (lastMs <= 0) 0 else (t.size / (lastMs / 60_000.0)).roundToInt()
        PlayerRate(p.id, t.size, average, series(t, replay.duration.ms))
    }

    private fun series(sorted: List<Long>, endMs: Long): List<RatePoint> {
        val out = ArrayList<RatePoint>()
        var lo = 0
        var hi = 0
        var at = stepMs
        while (at <= endMs + stepMs - 1) {
            val end = minOf(at, endMs)
            val start = end - windowMs
            while (hi < sorted.size && sorted[hi] <= end) hi++
            while (lo < hi && sorted[lo] <= start) lo++
            // At the start of the game the window is shorter than windowMs.
            val span = minOf(windowMs, end).coerceAtLeast(1)
            out += RatePoint(GameTime(end), (hi - lo) * 60_000f / span)
            at += stepMs
        }
        return out
    }
}
