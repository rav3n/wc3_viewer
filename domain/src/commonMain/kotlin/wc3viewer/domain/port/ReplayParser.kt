package wc3viewer.domain.port

import wc3viewer.domain.model.Replay

interface ReplayParser {
    /** Throws [ReplayFormatException] on fatal errors (broken file, unsupported version, not 1v1). */
    suspend fun parse(bytes: ByteArray): Replay
}

/** User-facing parse error. [kind] is for the UI and tests. */
class ReplayFormatException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        EMPTY_FILE,
        NOT_A_REPLAY,
        UNSUPPORTED_VERSION,
        UNSUPPORTED_GAME_MODE,
        CORRUPTED,
    }
}
