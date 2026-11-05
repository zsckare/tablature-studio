package com.tablaturestudio.importer

import kotlin.test.*

class GuitarProBinaryReaderTest {
    @Test fun `reads little endian values`() {
        val reader = GuitarProBinaryReader(byteArrayOf(0x7f, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12))
        assertEquals(127, reader.u8())
        assertEquals(0x1234, reader.u16le())
        assertEquals(0x12345678, reader.i32le())
        assertEquals(0, reader.remaining)
    }

    @Test fun `rejects truncated reads`() {
        val reader = GuitarProBinaryReader(byteArrayOf(1, 2))
        assertFailsWith<IllegalArgumentException> { reader.i32le() }
    }

    @Test fun `reads consecutive guitar pro strings`() {
        val bytes = byteArrayOf(4,0,0,0,3,65,66,67, 2,0,0,0,1,90)
        val reader = GuitarProBinaryReader(bytes)
        assertEquals("ABC", reader.gpString())
        assertEquals("Z", reader.gpString())
    }
}
