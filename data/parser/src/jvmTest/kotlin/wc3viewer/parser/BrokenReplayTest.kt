package wc3viewer.parser

import kotlinx.coroutines.runBlocking
import wc3viewer.domain.model.MatchResult
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.domain.port.ReplayFormatException.Kind
import wc3viewer.parser.mapping.RawToDomainMapper
import wc3viewer.parser.raw.RawLeave
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/** Negative cases (TEST_STRATEGY §7) and light fuzzing (§8). */
class BrokenReplayTest {
    private val parser = W3gParser()
    private val sample by lazy { Fixtures.replays.first { it.name == "Replay_2026_10_23_0240.w3g" }.readBytes() }

    private fun kindOf(bytes: ByteArray): Kind = runBlocking {
        try {
            parser.parse(bytes)
            fail("expected ReplayFormatException")
        } catch (e: ReplayFormatException) {
            assertTrue(e.message!!.isNotBlank())
            e.kind
        }
    }

    @Test
    fun brokenFixturesGiveClearErrors() {
        assertEquals(5, Fixtures.broken.size)
        for (f in Fixtures.broken) {
            val bytes = f.readBytes()
            val expected = if (bytes.isEmpty()) Kind.EMPTY_FILE else Kind.NOT_A_REPLAY
            assertEquals(expected, kindOf(bytes), f.name)
        }
    }

    @Test
    fun notAReplay() {
        assertEquals(Kind.NOT_A_REPLAY, kindOf("hello, this is text".encodeToByteArray()))
        assertEquals(Kind.NOT_A_REPLAY, kindOf(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
    }

    @Test
    fun garbagePrefixIsSkipped() = runBlocking {
        val replay = parser.parse(Random(1).nextBytes(512) + sample)
        assertEquals(2, replay.players.size)
    }

    private fun patched(offset: Int, value: Int, size: Int = 4): ByteArray {
        val b = sample.copyOf()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (size == 4) bb.putInt(offset, value) else bb.putShort(offset, value.toShort())
        return b
    }

    @Test
    fun oldVersionIsUnsupported() {
        val e = runBlocking { runCatching { parser.parse(patched(0x34, 10032)) }.exceptionOrNull() }
        assertTrue(e is ReplayFormatException && e.kind == Kind.UNSUPPORTED_VERSION, "$e")
        assertTrue(e.message!!.contains("1.32"), e.message)
        assertEquals(Kind.UNSUPPORTED_VERSION, kindOf(patched(0x24, 0)))
    }

    @Test
    fun hugeBlockCountIsRejectedBeforeAllocation() {
        assertEquals(Kind.CORRUPTED, kindOf(patched(0x2C, -1)))
    }

    @Test
    fun truncatedFileKeepsWholeBlocks() = runBlocking {
        // Cut in the middle of the data: metadata survives, the rest is reported.
        val replay = parser.parse(sample.copyOf(sample.size / 2))
        assertEquals(2, replay.players.size)
        assertTrue(replay.diagnostics.isNotEmpty())
        assertTrue(replay.events.isNotEmpty())
    }

    @Test
    fun notOneVsOneIsRejected() = runBlocking {
        val raw = parser.parseRaw(sample)
        val third = raw.metadata.slots.first { it.status == 0 }.copy(status = 2, playerId = 5, team = 0)
        val threePlayers = raw.copy(metadata = raw.metadata.copy(slots = raw.metadata.slots + third))
        val e = assertFailsWith<ReplayFormatException> { RawToDomainMapper.map(threePlayers) }
        assertEquals(Kind.UNSUPPORTED_GAME_MODE, e.kind)
    }

    @Test
    fun teamGamesAreRejectedWithClearError() {
        // 2v2 (Twisted Meadows) and 4v4 (Arathor) from the user's replay folder.
        for (name in listOf("6ad50154eae5e82cc9a4f183.w3g", "6ae0be4201d5ffcb2ad75ee3.w3g")) {
            val f = java.io.File(Fixtures.root, "testdata/replays/team/$name")
            assertTrue(f.exists(), f.path)
            assertEquals(Kind.UNSUPPORTED_GAME_MODE, kindOf(f.readBytes()), name)
            // The raw stream itself (including 0x60 trigger chat in 4v4) parses to the boundary.
            val raw = runBlocking { parser.parseRaw(f.readBytes()) }
            assertEquals(raw.stats.commandBlocks, raw.stats.commandBlocksExact, name)
            assertTrue(raw.diagnostics.isEmpty(), "$name: ${raw.diagnostics.take(3)}")
        }
    }

    @Test
    fun humanObserverIsFiltered() = runBlocking {
        val raw = parser.parseRaw(sample)
        val obs = raw.metadata.slots.first { it.status == 0 }.copy(status = 2, playerId = 7, team = 24)
        val replay = RawToDomainMapper.map(raw.copy(metadata = raw.metadata.copy(slots = raw.metadata.slots + obs)))
        assertEquals(listOf(1, 2), replay.players.map { it.id.value })
    }

    @Test
    fun winnerRules() = runBlocking {
        val raw = parser.parseRaw(sample)
        fun withLeaves(vararg l: RawLeave) = RawToDomainMapper.map(raw.copy(leaves = l.toList()))
        assertEquals(MatchResult.Winner(wc3viewer.domain.model.PlayerId(2)), withLeaves(RawLeave(10, 1, 1, 7), RawLeave(20, 1, 2, 9)).result)
        // FLO (pid 24) leaving with 0x09 is not a winner.
        assertEquals(MatchResult.Unknown, withLeaves(RawLeave(10, 1, 24, 9), RawLeave(20, 1, 1, 7)).result)
        val both = withLeaves(RawLeave(10, 1, 1, 9), RawLeave(20, 1, 2, 9))
        assertEquals(MatchResult.Unknown, both.result)
        assertTrue(both.diagnostics.isNotEmpty())
        // No leaves: duration falls back to the header.
        val none = withLeaves()
        assertEquals(none.headerDuration, none.duration)
    }

    @Test
    fun unknownActionSkipsRestOfCommandBlock() = runBlocking {
        val raw = parser.parseRaw(sample)
        val data = decompressed(sample)
        // Find the first 0x7A action at the start of some command block and replace its ID.
        val start = raw.metadata.gameDataOffset
        var patchedAt = -1
        var i = start
        while (i < data.size - 3) {
            if (data[i] == 0x1F.toByte()) {
                val len = (data[i + 1].toInt() and 0xFF) or ((data[i + 2].toInt() and 0xFF) shl 8)
                if (len > 6 && data[i + 5 + 3] == 0x7A.toByte()) { patchedAt = i + 5 + 3; break }
                i += 3 + len
            } else i++
        }
        assertTrue(patchedAt > 0)
        data[patchedAt] = 0xF0.toByte()
        val broken = parser.parseDecompressed(raw.header, data)
        assertEquals(raw.stats.commandBlocks, broken.stats.commandBlocks)
        assertEquals(raw.stats.commandBlocksExact - 1, broken.stats.commandBlocksExact)
        assertEquals(1, broken.diagnostics.size)
        assertTrue(broken.diagnostics.single().message.contains("0xf0"))
    }

    private fun decompressed(bytes: ByteArray): ByteArray {
        var o = 68
        val out = java.io.ByteArrayOutputStream()
        while (o + 12 <= bytes.size) {
            val c = ByteBuffer.wrap(bytes, o, 4).order(ByteOrder.LITTLE_ENDIAN).int
            o += 12
            out.write(wc3viewer.binary.Inflater.inflateZlib(bytes, o, c).data)
            o += c
        }
        return out.toByteArray()
    }

    /** Any corrupted input: either a Replay or ReplayFormatException, never another exception. */
    @Test
    fun fuzzCorruptedInputs() = runBlocking {
        val rnd = Random(20261027)
        val data = decompressed(sample)
        val header = parser.parseRaw(sample).header
        repeat(300) { iter ->
            val n = listOf(1, 8, 64)[iter % 3]
            val file = sample.copyOf()
            repeat(n) { file[rnd.nextInt(file.size)] = rnd.nextInt(256).toByte() }
            val dec = data.copyOf()
            repeat(n) { dec[rnd.nextInt(dec.size)] = rnd.nextInt(256).toByte() }
            val cut = sample.copyOf(rnd.nextInt(sample.size))
            for ((label, run) in listOf<Pair<String, suspend () -> Any>>(
                "file" to { parser.parse(file) },
                "decompressed" to { RawToDomainMapper.map(parser.parseDecompressed(header, dec)) },
                "truncated" to { parser.parse(cut) },
            )) {
                try {
                    run()
                } catch (e: ReplayFormatException) {
                    // expected
                } catch (e: Throwable) {
                    fail("iteration $iter ($label): unexpected ${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }
}
