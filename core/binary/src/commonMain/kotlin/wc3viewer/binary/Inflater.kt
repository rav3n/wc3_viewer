package wc3viewer.binary

/** Corrupted DEFLATE stream (invalid code, distance, etc.). */
class InflateException(message: String) : Exception(message)

/**
 * Inflate result.
 * [complete] — the final DEFLATE block was seen. Streams in .w3g are not finished (W3G_FORMAT §4),
 * so `false` is normal, not an error.
 */
class InflateResult(val data: ByteArray, val complete: Boolean)

/**
 * Pure Kotlin DEFLATE (RFC 1951), modelled after puff.c (zlib/contrib).
 * Running out of input is not an error: returns everything inflated so far (like Z_SYNC_FLUSH in zlib).
 */
object Inflater {
    /** zlib wrapper (RFC 1950): 2 header bytes; adler32 is not checked. */
    fun inflateZlib(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
        maxOutput: Int = 64 * 1024 * 1024,
    ): InflateResult {
        if (length < 2) throw InflateException("zlib header too short")
        val cmf = input[offset].toInt() and 0xFF
        val flg = input[offset + 1].toInt() and 0xFF
        if (cmf and 0x0F != 8) throw InflateException("unsupported zlib method ${cmf and 0x0F}")
        if ((cmf shl 8 or flg) % 31 != 0) throw InflateException("bad zlib header check")
        if (flg and 0x20 != 0) throw InflateException("zlib preset dictionary not supported")
        return inflateRaw(input, offset + 2, length - 2, maxOutput)
    }

    fun inflateRaw(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
        maxOutput: Int = 64 * 1024 * 1024,
    ): InflateResult {
        require(offset >= 0 && length >= 0 && offset + length <= input.size) { "bad input range" }
        val st = State(input, offset, offset + length, maxOutput)
        var complete = false
        try {
            while (true) {
                val last = st.bits(1)
                when (st.bits(2)) {
                    0 -> st.stored()
                    1 -> st.codes(FIXED_LEN, FIXED_DIST)
                    2 -> st.dynamic()
                    else -> throw InflateException("invalid block type 3")
                }
                if (last == 1) {
                    complete = true
                    break
                }
            }
        } catch (_: EndOfInput) {
            // Truncated stream: return what we have.
        }
        return InflateResult(st.output(), complete)
    }

    /** "Input exhausted" signal; caught in [inflateRaw]. */
    private object EndOfInput : RuntimeException()

    /** Canonical Huffman code: count[len] and symbol[] (as in puff). */
    private class Huffman(val count: ShortArray, val symbol: ShortArray)

    private const val MAXBITS = 15

    private val LEN_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LEN_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
    private val CL_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    /** Builds the table; returns null if the code is over-subscribed. */
    private fun build(lengths: IntArray, n: Int): Pair<Huffman, Int>? {
        val count = ShortArray(MAXBITS + 1)
        val symbol = ShortArray(n)
        for (s in 0 until n) count[lengths[s]]++
        if (count[0].toInt() == n) return Huffman(count, symbol) to 0
        var left = 1
        for (len in 1..MAXBITS) {
            left = (left shl 1) - count[len]
            if (left < 0) return null
        }
        val offs = IntArray(MAXBITS + 1)
        for (len in 1 until MAXBITS) offs[len + 1] = offs[len] + count[len]
        for (s in 0 until n) if (lengths[s] != 0) symbol[offs[lengths[s]]++] = s.toShort()
        return Huffman(count, symbol) to left
    }

    private val FIXED_LEN: Huffman
    private val FIXED_DIST: Huffman

    init {
        val l = IntArray(288)
        for (s in 0 until 144) l[s] = 8
        for (s in 144 until 256) l[s] = 9
        for (s in 256 until 280) l[s] = 7
        for (s in 280 until 288) l[s] = 8
        FIXED_LEN = build(l, 288)!!.first
        val d = IntArray(30) { 5 }
        FIXED_DIST = build(d, 30)!!.first
    }

    private class State(val inp: ByteArray, var pos: Int, val end: Int, val maxOutput: Int) {
        var out = ByteArray(16 * 1024)
        var outLen = 0
        var bitBuf = 0
        var bitCnt = 0

        fun output(): ByteArray = out.copyOf(outLen)

        fun bits(need: Int): Int {
            var v = bitBuf
            while (bitCnt < need) {
                if (pos >= end) throw EndOfInput
                v = v or ((inp[pos++].toInt() and 0xFF) shl bitCnt)
                bitCnt += 8
            }
            bitBuf = v ushr need
            bitCnt -= need
            return v and ((1 shl need) - 1)
        }

        private fun ensure(extra: Int) {
            val needed = outLen + extra
            if (needed > maxOutput) throw InflateException("output exceeds limit $maxOutput")
            if (needed > out.size) {
                var n = out.size * 2
                while (n < needed) n *= 2
                out = out.copyOf(minOf(n, maxOutput))
            }
        }

        private fun put(b: Int) {
            if (outLen == out.size) ensure(1)
            out[outLen++] = b.toByte()
        }

        fun stored() {
            bitBuf = 0
            bitCnt = 0
            if (end - pos < 4) throw EndOfInput
            val len = (inp[pos].toInt() and 0xFF) or ((inp[pos + 1].toInt() and 0xFF) shl 8)
            val nlen = (inp[pos + 2].toInt() and 0xFF) or ((inp[pos + 3].toInt() and 0xFF) shl 8)
            pos += 4
            if (len != (nlen.inv() and 0xFFFF)) throw InflateException("stored block length mismatch")
            val avail = minOf(len, end - pos)
            ensure(avail)
            inp.copyInto(out, outLen, pos, pos + avail)
            outLen += avail
            pos += avail
            if (avail < len) throw EndOfInput
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..MAXBITS) {
                code = code or bits(1)
                val count = h.count[len].toInt()
                if (code - count < first) return h.symbol[index + (code - first)].toInt()
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            throw InflateException("invalid Huffman code")
        }

        fun codes(lencode: Huffman, distcode: Huffman) {
            while (true) {
                var symbol = decode(lencode)
                if (symbol < 256) {
                    put(symbol)
                } else if (symbol == 256) {
                    return
                } else {
                    symbol -= 257
                    if (symbol >= 29) throw InflateException("invalid length symbol")
                    val len = LEN_BASE[symbol] + bits(LEN_EXTRA[symbol])
                    val ds = decode(distcode)
                    if (ds >= 30) throw InflateException("invalid distance symbol")
                    val dist = DIST_BASE[ds] + bits(DIST_EXTRA[ds])
                    if (dist > outLen) throw InflateException("distance too far back")
                    ensure(len)
                    var from = outLen - dist
                    repeat(len) { out[outLen++] = out[from++] }
                }
            }
        }

        fun dynamic() {
            val nlen = bits(5) + 257
            val ndist = bits(5) + 1
            val ncode = bits(4) + 4
            if (nlen > 286 || ndist > 30) throw InflateException("bad counts")
            val lengths = IntArray(320)
            for (i in 0 until ncode) lengths[CL_ORDER[i]] = bits(3)
            val (lencodeCl, leftCl) = build(lengths, 19) ?: throw InflateException("bad code lengths")
            if (leftCl != 0) throw InflateException("incomplete code length code")
            var index = 0
            val all = IntArray(nlen + ndist)
            while (index < nlen + ndist) {
                var symbol = decode(lencodeCl)
                if (symbol < 16) {
                    all[index++] = symbol
                } else {
                    var len = 0
                    when (symbol) {
                        16 -> {
                            if (index == 0) throw InflateException("repeat with no first length")
                            len = all[index - 1]
                            symbol = 3 + bits(2)
                        }
                        17 -> symbol = 3 + bits(3)
                        else -> symbol = 11 + bits(7)
                    }
                    if (index + symbol > nlen + ndist) throw InflateException("too many lengths")
                    repeat(symbol) { all[index++] = len }
                }
            }
            if (all[256] == 0) throw InflateException("no end-of-block code")
            val (lencode, leftL) = build(all.copyOfRange(0, nlen), nlen)
                ?: throw InflateException("bad literal/length code")
            if (leftL != 0 && nlen - lencode.count[0] != 1) throw InflateException("incomplete literal/length code")
            val (distcode, leftD) = build(all.copyOfRange(nlen, nlen + ndist), ndist)
                ?: throw InflateException("bad distance code")
            if (leftD != 0 && ndist - distcode.count[0] != 1) throw InflateException("incomplete distance code")
            codes(lencode, distcode)
        }
    }
}
