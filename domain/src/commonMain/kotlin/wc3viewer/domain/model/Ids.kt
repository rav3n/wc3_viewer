package wc3viewer.domain.model

import kotlin.jvm.JvmInline

/**
 * Object identifier (fourcc): 'hpea', 'Hpal', 'Rhst'.
 * [raw] — u32 LE as stored in the file; the string is the bytes in reverse order.
 */
@JvmInline
value class ObjectId(val raw: Int) {
    val code: String
        get() = buildString(4) {
            append(((raw ushr 24) and 0xFF).toChar())
            append(((raw ushr 16) and 0xFF).toChar())
            append(((raw ushr 8) and 0xFF).toChar())
            append((raw and 0xFF).toChar())
        }

    override fun toString(): String = code

    companion object {
        fun of(code: String): ObjectId {
            require(code.length == 4) { "fourcc must have 4 chars: '$code'" }
            return ObjectId(
                (code[0].code shl 24) or (code[1].code shl 16) or (code[2].code shl 8) or code[3].code,
            )
        }

        /** String ID if the most significant byte is a letter (W3G_FORMAT §7.2). */
        fun isFourCC(raw: Int): Boolean = ((raw ushr 24) and 0xFF) in 0x41..0x7A
    }
}

@JvmInline
value class GameTime(val ms: Long) : Comparable<GameTime> {
    override fun compareTo(other: GameTime): Int = ms.compareTo(other.ms)

    /** "m:ss" */
    fun format(): String {
        val totalSec = ms / 1000
        val sec = (totalSec % 60).toInt()
        return "${totalSec / 60}:${if (sec < 10) "0" else ""}$sec"
    }

    override fun toString(): String = format()
}

@JvmInline
value class PlayerId(val value: Int)

/** In-game object reference (NetTag, 2 × u32). */
data class UnitRef(val a: Int, val b: Int) {
    val isNone: Boolean get() = a == -1 && b == -1
}
