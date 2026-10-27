package wc3viewer.presentation

/** UI state of the single screen. */
sealed interface ReplayUiState {
    data object Empty : ReplayUiState
    data class Loading(val fileName: String) : ReplayUiState
    data class Loaded(val fileName: String, val overview: MatchOverview, val sections: ReplaySections) : ReplayUiState
    data class Failed(
        val fileName: String,
        val message: String,
        val diagnostics: List<String>,
        /** Short title of the failure kind, e.g. "Not a 1v1 replay". */
        val title: String = "Can't analyze this replay",
        /** What the user can do about it. */
        val hints: List<String> = emptyList(),
    ) : ReplayUiState
}

enum class RaceUi { HUMAN, ORC, NIGHT_ELF, UNDEAD, UNKNOWN }

data class PlayerUi(
    val id: Int,
    val name: String,
    /** "Human", or "Random → Human" for random players. */
    val race: String,
    val isWinner: Boolean,
    /** Index into the player palette. */
    val colorIndex: Int,
    /** Effective race (detected for random players). */
    val raceKey: RaceUi = RaceUi.UNKNOWN,
    val isRandom: Boolean = false,
)

data class MatchOverview(
    val players: List<PlayerUi>,
    val map: String,
    val duration: String,
    val patch: String,
    /** Winner name or "Unknown". */
    val winner: String,
    val warnings: List<String>,
    val durationSec: Float = 0f,
)

/** Series for a line chart: x — seconds, y — value. */
data class ChartSeries(val playerId: Int, val label: String, val xs: FloatArray, val ys: FloatArray) {
    override fun equals(other: Any?) = other is ChartSeries && playerId == other.playerId && label == other.label &&
        xs.contentEquals(other.xs) && ys.contentEquals(other.ys)
    override fun hashCode() = playerId * 31 + label.hashCode()
}

data class RateSummary(
    val playerId: Int,
    val apm: Int,
    val epm: Int,
    /** Counted APM actions over the whole game. */
    val actions: Int = 0,
    /** (APM − EPM) / APM, percent. */
    val spamPercent: Int = 0,
)

/** Kind of a build-order object; drives the icon placeholder and filters. */
enum class ObjectKind { UNIT, HERO, BUILDING, RESEARCH, ITEM, CANCEL, SKILL }

data class BuildOrderRow(
    val time: String,
    /** Display name: the game-data name, or the fourcc when the name is unknown. */
    val name: String,
    val kind: String,
    val timeSec: Float = 0f,
    /** Raw fourcc, e.g. "hpea". */
    val code: String = name,
    val kindKey: ObjectKind = ObjectKind.UNIT,
    /** False when [name] is just the fourcc. */
    val hasName: Boolean = false,
)

data class SkillPickUi(
    val time: String,
    val timeSec: Float,
    val code: String,
    val name: String?,
    /** Lower-bound hero level after this pick (estimate). */
    val level: Int,
)

/** [levelEstimate] — estimated level by learned skills (may overshoot). */
data class HeroUi(
    /** Hero name from game data; the fourcc if the name is unknown; "Unknown hero" without a type. */
    val hero: String,
    val firstSkill: String,
    val levelEstimate: Int,
    val retrainedAt: String?,
    val heroCode: String? = null,
    val firstSkillSec: Float = 0f,
    val skills: List<SkillPickUi> = emptyList(),
    /** Time of the matching hire order, if any. */
    val hiredAt: String? = null,
    /** False when [hero] is not a game-data name. */
    val hasName: Boolean = false,
)

/** [name] — game-data name, null if the fourcc is unknown. */
data class HeroOrderUi(val time: String, val timeSec: Float, val code: String, val name: String? = null)

/** [name] — game-data name, null if the fourcc is unknown. */
data class TimingUi(val time: String, val timeSec: Float, val code: String, val name: String? = null)

/** Key timings from orders (not completions). */
data class PlayerTimings(
    val heroes: List<TimingUi>,
    val tier2: TimingUi?,
    val tier3: TimingUi?,
    val expansion: TimingUi?,
)

data class HotkeyRow(
    val key: Int,
    val assigned: Int,
    val selected: Int,
    val lastSize: Int,
    val firstAssigned: String? = null,
)

/** Hotkey selections per key and time bucket: counts[i][b] for keys[i]. */
data class HotkeyHeat(val keys: List<Int>, val bucketSec: Int, val counts: List<IntArray>) {
    val max: Int get() = counts.maxOfOrNull { row -> row.maxOrNull() ?: 0 } ?: 0
    override fun equals(other: Any?) = other is HotkeyHeat && keys == other.keys && bucketSec == other.bucketSec &&
        counts.size == other.counts.size && counts.indices.all { counts[it].contentEquals(other.counts[it]) }
    override fun hashCode() = keys.hashCode() * 31 + bucketSec
}

/** Spending of one category, ESTIMATE. */
data class CategorySpendUi(val label: String, val gold: Int, val lumber: Int) {
    val total: Int get() = gold + lumber
}

/**
 * Economy ESTIMATE from orders (PRD "Economy"). Series are step lines in game seconds up to the game end.
 * Food used is an upper bound: deaths are not in the replay.
 */
data class EconomyUi(
    val playerId: Int,
    val gold: ChartSeries,
    val lumber: ChartSeries,
    /** Gold + lumber. */
    val total: ChartSeries,
    val foodUsed: ChartSeries,
    val foodCap: ChartSeries,
    val categories: List<CategorySpendUi>,
    val totalGold: Int,
    val totalLumber: Int,
    /** First time the food used reached 50 / 80 (upkeep thresholds), "m:ss". */
    val upkeep50: String?,
    val upkeep80: String?,
    /** Orders of objects missing in the game data, not priced. */
    val unpricedOrders: Int,
    /** Refunded queue cancels. */
    val cancels: Int,
)

data class ReplaySections(
    val rates: List<RateSummary>,
    val apmSeries: List<ChartSeries>,
    val epmSeries: List<ChartSeries>,
    val chartMaxX: Float,
    val buildOrders: Map<Int, List<BuildOrderRow>>,
    val heroes: Map<Int, List<HeroUi>>,
    val heroOrders: Map<Int, List<HeroOrderUi>>,
    val hotkeys: Map<Int, List<HotkeyRow>>,
    val timings: Map<Int, PlayerTimings> = emptyMap(),
    val hotkeyHeat: Map<Int, HotkeyHeat> = emptyMap(),
    val economy: Map<Int, EconomyUi> = emptyMap(),
)
