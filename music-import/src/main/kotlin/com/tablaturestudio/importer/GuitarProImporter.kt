package com.tablaturestudio.importer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * EN: Classic Guitar Pro binary inspection, deliberately separate from score decoding.
 * ES: Inspección binaria de Guitar Pro clásico, separada de la decodificación musical.
 */
class GuitarProImporter : ScoreImporter {
    override val extensions: Set<String> = setOf("gp3", "gp4", "gp5")

    override fun import(bytes: ByteArray): ImportResult {
        val version = GuitarProHeader.read(bytes)
            ?: return ImportResult.Failure("Invalid or truncated Guitar Pro 3/4/5 header")
        if (version != 5) return ImportResult.Failure("Guitar Pro $version musical decoding is not implemented yet")
        return try {
            ImportResult.Success(GuitarPro5Decoder(bytes).decode())
        } catch (error: Exception) {
            ImportResult.Failure(error.message ?: "Could not decode GP5")
        }
    }

}

data class GuitarProMetadata(val version: Int, val title: String?, val subtitle: String?, val artist: String?, val album: String?)

sealed interface GuitarProInspection {
    data class Success(val metadata: GuitarProMetadata) : GuitarProInspection
    data class Failure(val reason: String) : GuitarProInspection
}

/** EN: Bounded metadata parser. ES: Parser de metadatos con límites de lectura. */
object GuitarProInspector {
    private const val MAX_FIELD_BYTES = 16_384
    private val latin1: Charset = Charsets.ISO_8859_1

    fun inspect(bytes: ByteArray): GuitarProInspection {
        val version = GuitarProHeader.read(bytes)
            ?: return GuitarProInspection.Failure("Invalid or truncated Guitar Pro 3/4/5 header")
        return try {
            val cursor = Cursor(bytes, 31)
            // EN: GP3/4/5 use a 32-bit byte count and an 8-bit string length.
            // ES: GP3/4/5 usan tamaño de campo de 32 bits y longitud de texto de 8 bits.
            val title = cursor.readText()
            val subtitle = cursor.readText()
            val artist = cursor.readText()
            val album = cursor.readText()
            GuitarProInspection.Success(GuitarProMetadata(version, title, subtitle, artist, album))
        } catch (e: IllegalArgumentException) {
            GuitarProInspection.Failure("Invalid or truncated Guitar Pro metadata: ${e.message}")
        }
    }

    private class Cursor(private val data: ByteArray, private var position: Int) {
        fun readText(): String? {
            require(data.size - position >= 5) { "missing string length" }
            val fieldSize = ByteBuffer.wrap(data, position, 4).order(ByteOrder.LITTLE_ENDIAN).int
            position += 4
            val textSize = data[position++].toInt() and 0xff
            require(fieldSize in 0..MAX_FIELD_BYTES) { "invalid string field size" }
            require(textSize <= fieldSize) { "string exceeds declared field size" }
            require(textSize <= data.size - position) { "string extends past end of file" }
            val value = data.copyOfRange(position, position + textSize).toString(latin1).trim().ifEmpty { null }
            position += textSize
            return value
        }
    }
}

/** EN: Fixed-size classic GP signature reader. ES: Lector de firma GP clásica de tamaño fijo. */
internal object GuitarProHeader {
    private val signature = Regex("^FICHIER GUITAR PRO v?([345])\\.[0-9]{1,2}$")

    fun read(bytes: ByteArray): Int? {
        if (bytes.size < 31) return null
        val length = bytes[0].toInt() and 0xff
        if (length !in 1..30) return null
        val header = bytes.copyOfRange(1, 1 + length).toString(Charsets.US_ASCII)
        return signature.matchEntire(header)?.groupValues?.get(1)?.toIntOrNull()
    }
}
