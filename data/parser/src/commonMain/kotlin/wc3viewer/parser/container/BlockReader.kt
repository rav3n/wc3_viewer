package wc3viewer.parser.container

import wc3viewer.binary.BinaryFormatException
import wc3viewer.binary.ByteReader
import wc3viewer.binary.InflateException
import wc3viewer.binary.Inflater
import wc3viewer.domain.model.ParseDiagnostic
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.domain.port.ReplayFormatException.Kind
import wc3viewer.parser.raw.ReplayHeader

internal object BlockReader {
    /** Cap on total decompressed data (protection against hostile files). */
    const val MAX_DECOMPRESSED = 64 * 1024 * 1024
    private const val MAX_BLOCK = 1024 * 1024

    /** Inflates every block (12-byte Reforged header) and concatenates them. */
    fun read(bytes: ByteArray, offset: Int, header: ReplayHeader, diagnostics: MutableList<ParseDiagnostic>): ByteArray {
        if (header.blockCount.toLong() * 8192 > MAX_DECOMPRESSED) {
            throw ReplayFormatException(Kind.CORRUPTED, "The replay declares too many blocks (${header.blockCount}).")
        }
        val out = Buffer()
        val r = ByteReader(bytes, offset)
        var index = 0
        while (index < header.blockCount) {
            if (r.remaining < 12) {
                diagnostics += ParseDiagnostic(null, r.pos, "File ends after $index of ${header.blockCount} blocks")
                break
            }
            val at = r.pos
            try {
                val compSize = r.u32AsSize()
                val decompSize = r.u32AsSize()
                r.skip(4) // checksum, not validated
                if (decompSize > MAX_BLOCK) throw ReplayFormatException(Kind.CORRUPTED, "Block $index is too large.")
                val available = minOf(compSize, r.remaining)
                val result = Inflater.inflateZlib(bytes, r.pos, available, maxOutput = maxOf(decompSize, 8192))
                r.skip(available)
                out.append(result.data)
                if (available < compSize) {
                    diagnostics += ParseDiagnostic(null, at, "Block $index is truncated")
                    break
                }
                if (out.size > MAX_DECOMPRESSED) throw ReplayFormatException(Kind.CORRUPTED, "Decompressed data is too large.")
            } catch (e: InflateException) {
                if (index == 0) throw ReplayFormatException(Kind.CORRUPTED, "The replay data is corrupted (block $index: ${e.message}).", e)
                diagnostics += ParseDiagnostic(null, at, "Block $index is corrupted: ${e.message}; the rest is skipped")
                break
            } catch (e: BinaryFormatException) {
                diagnostics += ParseDiagnostic(null, at, "Block $index header is truncated")
                break
            }
            index++
        }
        if (out.size == 0) throw ReplayFormatException(Kind.CORRUPTED, "The replay contains no data blocks.")
        return out.toByteArray()
    }

    private class Buffer {
        private var data = ByteArray(256 * 1024)
        var size = 0
            private set

        fun append(b: ByteArray) {
            if (size + b.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + b.size))
            b.copyInto(data, size)
            size += b.size
        }

        fun toByteArray(): ByteArray = data.copyOf(size)
    }
}
