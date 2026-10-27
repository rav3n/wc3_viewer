package wc3viewer.binary

/** Out-of-bounds read or broken binary structure. */
class BinaryFormatException(val offset: Int, message: String) : Exception("$message (offset $offset)")

/**
 * Bounds-checked little-endian reader.
 * Reads [buf] in the range [start, limit).
 */
class ByteReader(
    private val buf: ByteArray,
    start: Int = 0,
    val limit: Int = buf.size,
) {
    init {
        require(start in 0..limit && limit <= buf.size) { "bad range $start..$limit of ${buf.size}" }
    }

    var pos: Int = start
        private set

    val remaining: Int get() = limit - pos
    val hasRemaining: Boolean get() = pos < limit

    private fun need(n: Int) {
        if (n < 0 || n > limit - pos) throw BinaryFormatException(pos, "need $n bytes, have ${limit - pos}")
    }

    fun seek(newPos: Int) {
        if (newPos < 0 || newPos > limit) throw BinaryFormatException(pos, "seek to $newPos out of [0, $limit]")
        pos = newPos
    }

    fun skip(n: Int) {
        need(n)
        pos += n
    }

    fun peekU8(): Int {
        need(1)
        return buf[pos].toInt() and 0xFF
    }

    fun u8(): Int {
        need(1)
        return buf[pos++].toInt() and 0xFF
    }

    fun u16(): Int {
        need(2)
        val v = (buf[pos].toInt() and 0xFF) or ((buf[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return v
    }

    /** u32 as raw Int bits (fourcc, flags). */
    fun i32(): Int {
        need(4)
        val v = (buf[pos].toInt() and 0xFF) or
            ((buf[pos + 1].toInt() and 0xFF) shl 8) or
            ((buf[pos + 2].toInt() and 0xFF) shl 16) or
            ((buf[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }

    /** Unsigned u32. */
    fun u32(): Long = i32().toLong() and 0xFFFF_FFFFL

    /** u32 that must fit into Int (sizes, lengths). */
    fun u32AsSize(): Int {
        val at = pos
        val v = u32()
        if (v > Int.MAX_VALUE) throw BinaryFormatException(at, "size too large: $v")
        return v.toInt()
    }

    fun f32(): Float = Float.fromBits(i32())

    fun bytes(n: Int): ByteArray {
        need(n)
        val out = buf.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    /** String up to 0x00 (UTF-8). The terminator is required. */
    fun cstrBytes(): ByteArray {
        var end = pos
        while (end < limit && buf[end] != 0.toByte()) end++
        if (end >= limit) throw BinaryFormatException(pos, "unterminated string")
        val out = buf.copyOfRange(pos, end)
        pos = end + 1
        return out
    }

    fun cstr(): String = cstrBytes().decodeToString()

    /** Sub-reader over the next [n] bytes; this reader moves past them. */
    fun sub(n: Int): ByteReader {
        need(n)
        val r = ByteReader(buf, pos, pos + n)
        pos += n
        return r
    }

    /** Index of the first [pattern] occurrence in [buf] from [from]; -1 if absent. */
    companion object {
        fun indexOf(buf: ByteArray, pattern: ByteArray, from: Int = 0): Int {
            if (pattern.isEmpty()) return from
            outer@ for (i in from..buf.size - pattern.size) {
                for (j in pattern.indices) if (buf[i + j] != pattern[j]) continue@outer
                return i
            }
            return -1
        }
    }
}
