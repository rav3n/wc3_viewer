package wc3viewer.binary

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fixtures produced with zlib (Python): JS and JVM must give the same result. */
class InflaterTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // zlib.compress(b"hello hello hello hello")
    private val complete = hex("789ccb48cdc9c957c8402701680308b1")

    @Test
    fun inflatesCompleteStream() {
        val r = Inflater.inflateZlib(complete)
        assertTrue(r.complete)
        assertEquals("hello hello hello hello", r.data.decodeToString())
    }

    @Test
    fun inflatesSyncFlushedStreamWithoutFinalBlock() {
        // compressobj(); compress(b"abcabcabc warcraft") + flush(Z_SYNC_FLUSH)
        val r = Inflater.inflateZlib(hex("789c4a4c4a4e042385f2c4a2e4a2c4b41200000000ffff"))
        assertFalse(r.complete)
        assertEquals("abcabcabc warcraft", r.data.decodeToString())
    }

    @Test
    fun truncatedStreamReturnsPrefix() {
        val r = Inflater.inflateZlib(complete, 0, 6)
        assertFalse(r.complete)
        assertTrue("hello hello hello hello".startsWith(r.data.decodeToString()))
    }

    @Test
    fun storedBlock() {
        // raw: final stored block "abc"
        val r = Inflater.inflateRaw(hex("010300fcff616263"))
        assertTrue(r.complete)
        assertContentEquals("abc".encodeToByteArray(), r.data)
    }

    @Test
    fun badHeaderFails() {
        assertFailsWith<InflateException> { Inflater.inflateZlib(hex("0000")) }
        assertFailsWith<InflateException> { Inflater.inflateRaw(hex("07")) } // block type 3
    }

    @Test
    fun outputLimitIsEnforced() {
        assertFailsWith<InflateException> { Inflater.inflateZlib(complete, maxOutput = 5) }
    }
}
