package wc3viewer.binary

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross-check against java.util.zip as the reference (ADR 4). */
class InflaterJvmTest {

    private fun deflate(data: ByteArray, level: Int, strategy: Int, finish: Boolean): ByteArray {
        val d = Deflater(level)
        d.setStrategy(strategy)
        d.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        if (finish) {
            d.finish()
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        } else {
            // Like .w3g: sync flush without a final block and adler32.
            // The first call after setStrategy may only apply params and return 0 bytes.
            while (true) {
                val n = d.deflate(buf, 0, buf.size, Deflater.SYNC_FLUSH)
                out.write(buf, 0, n)
                if (n == 0 && d.needsInput()) break
            }
        }
        d.end()
        return out.toByteArray()
    }

    private fun javaInflate(src: ByteArray, off: Int, len: Int): ByteArray {
        val inf = java.util.zip.Inflater()
        inf.setInput(src, off, len)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = try { inf.inflate(buf) } catch (e: Exception) { break }
            if (n == 0) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    private fun sample(rnd: Random): ByteArray {
        val size = rnd.nextInt(1, 200_000)
        val alphabet = rnd.nextInt(1, 256)
        return when (rnd.nextInt(3)) {
            0 -> ByteArray(size) { rnd.nextInt(alphabet).toByte() }
            1 -> { // repeats: long matches
                val chunk = ByteArray(rnd.nextInt(1, 300)) { rnd.nextInt(alphabet).toByte() }
                ByteArray(size) { chunk[it % chunk.size] }
            }
            else -> rnd.nextBytes(size)
        }
    }

    @Test
    fun matchesJavaZipOnRandomData() {
        val rnd = Random(42)
        val strategies = intArrayOf(Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY)
        repeat(60) { i ->
            val data = sample(rnd)
            val level = rnd.nextInt(0, 10)
            val finish = i % 2 == 0
            val z = deflate(data, level, strategies[i % 3], finish)
            val r = Inflater.inflateZlib(z)
            assertContentEquals(data, r.data, "case $i level=$level finish=$finish")
            assertEquals(finish, r.complete)
        }
    }

    @Test
    fun truncatedStreamsGivePrefixLikeJavaZip() {
        val rnd = Random(7)
        repeat(30) { i ->
            val data = sample(rnd)
            val z = deflate(data, 6, Deflater.DEFAULT_STRATEGY, finish = true)
            val cut = if (z.size <= 2) z.size else rnd.nextInt(2, z.size)
            val ours = Inflater.inflateZlib(z, 0, cut).data
            assertTrue(data.copyOf(ours.size).contentEquals(ours), "case $i: output is not a prefix")
            // java.util.zip may return a few bytes less because of its internal window buffering.
            val ref = javaInflate(z, 0, cut)
            assertTrue(ours.size >= ref.size, "case $i: ours=${ours.size} java=${ref.size}")
        }
    }

    @Test
    fun matchesJavaZipOnAllReplayBlocks() {
        val dir = File(System.getProperty("wc3.root"), "testdata/replays/3.00")
        val files = dir.listFiles { f -> f.extension == "w3g" && f.length() > 0 }.orEmpty()
        var blocks = 0
        for (f in files) {
            val b = f.readBytes()
            if (!String(b, 0, 26, Charsets.ISO_8859_1).startsWith("Warcraft III recorded game")) continue
            var o = 68
            while (o + 12 <= b.size) {
                val r = ByteReader(b, o)
                val c = r.u32AsSize()
                r.skip(8)
                o += 12
                if (o + c > b.size) break
                val ref = javaInflate(b, o, c)
                val ours = Inflater.inflateZlib(b, o, c)
                assertContentEquals(ref, ours.data, "${f.name} block at $o")
                assertEquals(8192, ours.data.size, "${f.name} block at $o")
                o += c
                blocks++
            }
        }
        assertTrue(blocks > 0, "no replay blocks found in $dir")
        println("Inflater: $blocks replay blocks verified")
    }
}
