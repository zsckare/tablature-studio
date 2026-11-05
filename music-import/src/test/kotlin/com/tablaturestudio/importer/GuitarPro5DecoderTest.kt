package com.tablaturestudio.importer

import java.io.ByteArrayOutputStream
import kotlin.test.*

/** EN: GP5 test fixtures are generated, never copied from copyrighted songs.
 * ES: Los casos GP5 se generan en pruebas, sin distribuir canciones de terceros.
 */
class GuitarPro5DecoderTest {
    @Test fun `loads one complete GP5 5_10 measure with pitch and program`() {
        val result = assertIs<ImportResult.Success>(GuitarProImporter().import(fixture()))
        assertEquals("Test Song", result.score.title)
        assertEquals("Composer", result.score.artist)
        assertEquals(140, result.score.tempo)
        val track = result.score.tracks.single()
        assertEquals("Guitar", track.name)
        assertEquals(listOf(64, 59, 55, 50, 45, 40), track.tuning)
        assertEquals(24, track.midiProgram)
        assertEquals(1, track.measures.size)
        assertEquals(3, track.measures.single().beats.single().notes.single().fret)
        assertEquals(0.0, track.measures.single().beats.single().onset)
    }

    @Test fun `supports the GP5 5_00 variant`() {
        val result = assertIs<ImportResult.Success>(GuitarProImporter().import(fixture(0)))
        assertEquals("Test Song", result.score.title)
        assertEquals(1, result.score.tracks.first().measures.size)
    }

    @Test fun `truncated GP5 returns a descriptive error without a false score`() {
        val broken = fixture().copyOf(500)
        val result = assertIs<ImportResult.Failure>(GuitarProImporter().import(broken))
        assertTrue(result.message.contains("GP5 decoding failed"))
    }

    private class Bin {
        val out = ByteArrayOutputStream()
        fun b(n: Int) { out.write(n and 255) }
        fun short(n: Int) { b(n); b(n shr 8) }
        fun int(n: Int) { repeat(4) { b(n shr (it * 8)) } }
        fun zeros(count: Int) { repeat(count) { b(0) } }
        fun fixed(text: String, width: Int) {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            require(bytes.size <= width)
            b(bytes.size); out.write(bytes); zeros(width - bytes.size)
        }
        fun text(text: String) {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            int(bytes.size + 1); b(bytes.size); out.write(bytes)
        }
        fun intByteString(text: String = "") {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            int(if (bytes.isEmpty()) 0 else bytes.size + 1)
            if (bytes.isNotEmpty()) { b(0); out.write(bytes) }
        }
    }

    private fun fixture(minor: Int = 10): ByteArray = Bin().apply {
        fixed("FICHIER GUITAR PRO v5.${minor.toString().padStart(2, '0')}", 30)
        listOf("Test Song", "", "Composer", "Album", "", "", "", "", "").forEach(::text)
        int(0) // notice lines
        int(0) // lyrics track
        repeat(5) { int(0); int(0) } // 5 empty lyrics lines
        if (minor > 0) zeros(19) // RSE master in 5.10+
        zeros(30) // page setup metrics + flags
        repeat(10) { intByteString() }
        intByteString() // tempo description
        int(140)
        if (minor > 0) b(0) // hide tempo in 5.10+
        zeros(5) // key + octave
        repeat(64) { channel -> int(if (channel == 0) 24 else 0); zeros(8) }
        zeros(38) // direction pointers
        int(0) // master reverb
        int(1); int(1) // 1 measure, 1 track
        b(0); zeros(2) // time sig inherited, alternative and triplet
        b(0); b(0) // first track separator + track flags
        fixed("Guitar", 40)
        int(6)
        listOf(64, 59, 55, 50, 45, 40, 0).forEach(::int)
        int(1); int(1); int(1) // port, channel, FX channel
        int(24); int(0); zeros(4) // frets, capo, color
        zeros(2 + 3 + 12 + 12 + (if (minor > 0) 16 else 15)) // settings + RSE
        if (minor > 0) {
            zeros(4) // EQ
            intByteString(); intByteString() // effect and category
        }
        zeros(if (minor > 0) 1 else 2) // track terminator
        int(1) // voice 1 beat count
        b(0); b(0); b(0x40) // flags, quarter note, first string
        b(0x20); b(1); b(3); b(0) // note flags, normal, fret, accidental
        short(0) // beat drawing flags
        int(0) // voice 2 empty
        b(0) // layout break
    }.out.toByteArray()
}
