package wc3viewer.parser.container

import wc3viewer.binary.BinaryFormatException
import wc3viewer.binary.ByteReader
import wc3viewer.domain.model.Patch
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.domain.port.ReplayFormatException.Kind
import wc3viewer.parser.raw.ReplayHeader

internal object HeaderReader {
    private val MAGIC = "Warcraft III recorded game\u001A\u0000".encodeToByteArray()

    /** Minimal supported version: patch 3.00. */
    const val MIN_VERSION = 10200

    /** Returns the header and the absolute offset where the first block header starts. */
    fun read(bytes: ByteArray): Pair<ReplayHeader, Int> {
        if (bytes.isEmpty()) throw ReplayFormatException(Kind.EMPTY_FILE, "The file is empty (0 bytes).")
        // Search the magic like w3gjs does: survives wrappers with garbage in front.
        val start = ByteReader.indexOf(bytes, MAGIC)
        if (start < 0) {
            throw ReplayFormatException(
                Kind.NOT_A_REPLAY,
                if (bytes.all { it == 0.toByte() }) "The file contains only zero bytes — it was not saved correctly."
                else "This is not a Warcraft III replay (.w3g signature not found).",
            )
        }
        try {
            val r = ByteReader(bytes, start + MAGIC.size)
            val headerSize = r.u32AsSize()
            val compressedSize = r.u32()
            val headerVersion = r.i32()
            val decompressedSize = r.u32()
            val blockCount = r.u32AsSize()
            if (headerVersion != 1) {
                throw ReplayFormatException(Kind.UNSUPPORTED_VERSION, "Unsupported replay header version $headerVersion (pre-1.07).")
            }
            val product = r.bytes(4).reversedArray().decodeToString()
            val version = r.i32()
            val build = r.u16()
            val flags = r.u16()
            val duration = r.u32()
            r.skip(4) // header CRC32, not validated
            val header = ReplayHeader(
                headerSize, compressedSize, headerVersion, decompressedSize, blockCount,
                product, version, build, flags, duration,
            )
            if (version < MIN_VERSION) {
                throw ReplayFormatException(
                    Kind.UNSUPPORTED_VERSION,
                    "Unsupported game version ${patchOf(version, build)}. Only patch 3.0 and newer is supported.",
                )
            }
            if (build < 6089) {
                throw ReplayFormatException(Kind.UNSUPPORTED_VERSION, "Unsupported build $build (classic block headers).")
            }
            return header to start + headerSize
        } catch (e: BinaryFormatException) {
            throw ReplayFormatException(Kind.CORRUPTED, "The replay header is truncated.", e)
        }
    }

    /** Version → patch, per w3gjs convert.ts: 10200 → 3.00. */
    fun patchOf(version: Int, build: Int): Patch = when {
        version >= 10100 -> Patch(version / 100 - 99, version % 100, build)
        version >= 10000 -> Patch(1, version % 100, build)
        else -> Patch(1, version, build)
    }
}
