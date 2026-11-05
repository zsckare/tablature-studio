package com.tablaturestudio.importer

import java.nio.charset.Charset

/**
 * EN: Bounds-checked little-endian reader for classic Guitar Pro structures.
 * ES: Lector little-endian con comprobación de límites para Guitar Pro clásico.
 */
internal class GuitarProBinaryReader(private val bytes: ByteArray, start: Int = 0) {
    var offset: Int = start
        private set

    init { require(start in 0..bytes.size) { "Invalid initial offset: $start" } }

    val remaining: Int get() = bytes.size - offset

    fun skip(count: Int) {
        require(count >= 0 && count <= remaining) { "Truncated file at byte $offset (skip $count)" }
        offset += count
    }

    fun u8(): Int {
        require(remaining >= 1) { "Truncated file at byte $offset (u8)" }
        return bytes[offset++].toInt() and 0xff
    }

    fun i8(): Int = u8().toByte().toInt()

    fun u16le(): Int = u8() or (u8() shl 8)

    fun i32le(): Int = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)

    fun read(count: Int): ByteArray {
        require(count >= 0 && count <= remaining) { "Truncated file at byte $offset (read $count)" }
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    /** EN: GP byte-sized string length preceded by its 32-bit declared size.
     * ES: Longitud de cadena GP de un byte precedida por tamaño declarado de 32 bits.
     */
    fun gpString(maxLength: Int = 16384, charset: Charset = Charsets.ISO_8859_1): String {
        val declared = i32le()
        require(declared in 0..maxLength + 1) { "Invalid GP string size $declared at byte ${offset - 4}" }
        val actual = u8()
        require(actual <= maxLength && actual <= declared) { "Invalid GP string length $actual at byte ${offset - 1}" }
        return read(actual).toString(charset)
    }
}
