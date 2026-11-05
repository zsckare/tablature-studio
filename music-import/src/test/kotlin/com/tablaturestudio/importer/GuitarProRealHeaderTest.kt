package com.tablaturestudio.importer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** EN: Regression tests for genuine GP5-style headers and string fields.
 * ES: Pruebas de regresión para cabeceras y campos reales de GP5.
 */
class GuitarProRealHeaderTest {
    private fun header(): ByteArray {
        val signature = "FICHIER GUITAR PRO v5.10".toByteArray(Charsets.US_ASCII)
        return byteArrayOf(signature.size.toByte()) + signature + ByteArray(30 - signature.size)
    }

    private fun field(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        return ByteBuffer.allocate(5 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(bytes.size + 1).put(bytes.size.toByte()).put(bytes).array()
    }

    @Test
    fun `recognizes real GP5 version prefix`() {
        assertEquals(5, GuitarProHeader.read(header()))
    }

    @Test
    fun `reads sequential metadata without skipping extra bytes`() {
        val bytes = header() + field("Buried Alive") + field("") +
            field("Avenged Sevenfold") + field("Nightmare")
        val result = assertIs<GuitarProInspection.Success>(GuitarProInspector.inspect(bytes))
        assertEquals("Buried Alive", result.metadata.title)
        assertEquals("Avenged Sevenfold", result.metadata.artist)
        assertEquals("Nightmare", result.metadata.album)
    }
}
