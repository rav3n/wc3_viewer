package wc3viewer.domain.model

enum class Race { HUMAN, ORC, NIGHT_ELF, UNDEAD, RANDOM, UNKNOWN }

/** Game patch: 3.00 → major 3, minor 0. */
data class Patch(val major: Int, val minor: Int, val build: Int) : Comparable<Patch> {
    override fun compareTo(other: Patch): Int =
        compareValuesBy(this, other, Patch::major, Patch::minor, Patch::build)

    override fun toString(): String = "$major.${minor.toString().padStart(2, '0')} (build $build)"
}

data class Player(
    val id: PlayerId,
    val name: String,
    /** Race from the slot; [Race.RANDOM] for random. */
    val race: Race,
    /** Race inferred from the first orders; set for random players. */
    val detectedRace: Race?,
    val team: Int,
    val color: Int,
    val isObserver: Boolean,
    /** Time of the player's last command block (any actions). APM denominator, as in w3gjs. */
    val lastCommandTime: GameTime = GameTime(0),
) {
    /** Actual race for display and metrics. */
    val effectiveRace: Race get() = detectedRace ?: race
}

data class MapInfo(val path: String, val creator: String) {
    /** Map file name without directory, W3C prefix `NNN_w3c_YYMMDD_HHMM_` and extension. */
    val name: String
        get() = path.substringAfterLast('\\').substringAfterLast('/').substringBeforeLast('.')
            .replace(W3C_PREFIX, "")

    private companion object {
        val W3C_PREFIX = Regex("^\\d+_w3c_\\d+_\\d+_")
    }
}

data class ChatMessage(val time: GameTime, val player: PlayerId, val text: String)

data class LeaveEvent(val time: GameTime, val player: PlayerId, val reason: Int, val result: Int)

sealed interface MatchResult {
    data class Winner(val player: PlayerId) : MatchResult
    data object Unknown : MatchResult
}

data class ParseDiagnostic(val time: GameTime?, val offset: Int, val message: String)

data class Replay(
    val patch: Patch,
    /** Match length: time of the last player leave (observers excluded); header duration if there are none. */
    val duration: GameTime,
    /** Duration from SubHeader. Excludes pauses, includes the time FLO stays after players left. */
    val headerDuration: GameTime,
    val map: MapInfo,
    val gameName: String,
    /** Match players only, without observers and FLO. */
    val players: List<Player>,
    /** Sorted by time. */
    val events: List<GameEvent>,
    val chat: List<ChatMessage>,
    val leaves: List<LeaveEvent>,
    val result: MatchResult,
    val diagnostics: List<ParseDiagnostic>,
) {
    fun player(id: PlayerId): Player? = players.firstOrNull { it.id == id }
}
