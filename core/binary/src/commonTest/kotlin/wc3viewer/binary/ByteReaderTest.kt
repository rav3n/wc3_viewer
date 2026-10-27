package wc3viewer.binary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ByteReaderTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun readsLittleEndian() {
        val r = ByteReader(bytes(0x01, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0xFF, 0xFF, 0xFF, 0xFF))
        assertEquals(1, r.u8())
        assertEquals(0x1234, r.u16())
        assertEquals(0x12345678, r.i32())
        assertEquals(0xFFFF_FFFFL, r.u32())
        assertFalse(r.hasRemaining)
    }

    @Test
    fun readsFloatAndCString() {
        // 1.5f = 0x3FC00000
        val r = ByteReader(bytes(0x00, 0x00, 0xC0, 0x3F, 'a'.code, 'b'.code, 0, 'c'.code))
        assertEquals(1.5f, r.f32())
        assertEquals("ab", r.cstr())
        assertEquals(1, r.remaining)
    }

    @Test
    fun boundsAreChecked() {
        val r = ByteReader(bytes(1, 2, 3), 0, 2)
        assertFailsWith<BinaryFormatException> { r.i32() }
        r.u16()
        assertFailsWith<BinaryFormatException> { r.u8() }
        assertFailsWith<BinaryFormatException> { ByteReader(bytes('a'.code)).cstr() }
        assertFailsWith<BinaryFormatException> { ByteReader(bytes(1)).skip(-1) }
    }

    @Test
    fun subReaderIsLimited() {
        val r = ByteReader(bytes(1, 2, 3, 4))
        val s = r.sub(2)
        assertEquals(0x0201, s.u16())
        assertFailsWith<BinaryFormatException> { s.u8() }
        assertEquals(3, r.u8())
    }

    @Test
    fun indexOfFindsPattern() {
        assertEquals(2, ByteReader.indexOf(bytes(0, 0, 7, 8, 9), bytes(7, 8)))
        assertEquals(-1, ByteReader.indexOf(bytes(0, 7), bytes(7, 8)))
    }
}
