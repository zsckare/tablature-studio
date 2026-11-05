package com.tablaturestudio.importer

import com.tablaturestudio.core.*

/**
 * EN: JVM-only, bounds-checked GP5 (5.00/5.10) score decoder; no browser or external runtime.
 * ES: Decodificador GP5 (5.00/5.10) con límites verificados, sin navegador ni runtime externo.
 *
 * Supports the standard musical structure; some advanced playback effects are consumed but
 * cannot yet be faithfully synthesized by the MIDI backend.
 */
internal class GuitarPro5Decoder(private val bytes: ByteArray) {
    private val r = GuitarProBinaryReader(bytes)
    private var minor = 10
    private var stage = "version"
    private var currentMeasureIndex = 0
    private var currentBeatOnset = 0.0
    private val tempoChanges = mutableListOf<TempoChange>()
    private data class Meta(val title: String, val artist: String)
    private data class TrackHeader(val name: String, val tuning: List<Int>, val midiProgram: Int, val percussion: Boolean)

    fun decode(): Score {
        try {
            require(bytes.size <= 32 * 1024 * 1024) { "GP5 file is too large" }
            val version = r.readFixedString(30)
            val match = Regex("FICHIER GUITAR PRO v5\\.(00|10)").matchEntire(version)
                ?: error("Only Guitar Pro 5.00/5.10 files are supported ($version)")
            minor = match.groupValues[1].toInt()
            stage = "metadata"
            val meta = readMetadata()
            stage = "lyrics and page setup"
            r.skip(4) // lyrics track
            repeat(5) { r.skip(4); r.readIntString() } // lyrics lines
            if (minor > 0) r.skip(19) // RSE master: 2 ints + 11 EQ bytes
            r.skip(30) // page dimensions / margins / flags
            repeat(10) { r.readIntByteString() }
            stage = "tempo and channel setup"
            r.readIntByteString() // tempo name
            val tempo = r.i32le().coerceIn(20, 400)
            if (minor > 0) r.skip(1) // hide tempo
            r.skip(5) // key + octave
            val programs = IntArray(64) { r.i32le().coerceIn(0, 127).also { r.skip(8) } }
            r.skip(38) // 19 direction pointers
            r.skip(4) // master reverb
            val measureCount = r.i32le()
            val trackCount = r.i32le()
            require(measureCount in 1..10_000 && trackCount in 1..100) {
                "Invalid GP5 score size: $measureCount measures, $trackCount tracks"
            }
            stage = "measure headers"
            val signatures = readMeasureHeaders(measureCount)
            stage = "track headers"
            val tracks = (0 until trackCount).map { index -> readTrackHeader(index, programs) }
            r.skip(if (minor == 0) 2 else 1) // tracks terminator
            stage = "measures"
            val measures = Array(trackCount) { mutableListOf<Measure>() }
            val previousFrets = Array(trackCount) { IntArray(8) { -1 } }
            repeat(measureCount) { measureIndex ->
                currentMeasureIndex = measureIndex
                repeat(trackCount) { trackIndex ->
                    stage = "measure ${measureIndex + 1}, track ${trackIndex + 1} at byte ${r.offset}"
                    val voices = (0..1).map {
                        readVoice(tracks[trackIndex].tuning.size, previousFrets[trackIndex])
                    }
                    if (r.remaining > 0) r.skip(1) // optional line break; some files omit the final byte
                    // EN: Combine note events from both polyphonic voices by musical onset.
                    // ES: Combinar notas de ambas voces según su posición musical.
                    val primary = voices[0]
                    val secondary = if (primary.isEmpty()) voices[1]
                        else voices[1].filter { it.notes.isNotEmpty() }
                    val merged = (primary + secondary).sortedWith(compareBy({ it.onset ?: 0.0 }, { -it.notes.size }))
                    measures[trackIndex] += Measure(measureIndex + 1, merged, signatures[measureIndex])
                }
            }
            return Score(meta.title.ifBlank { "Guitar Pro Score" }, meta.artist.ifBlank { null },
                tracks.mapIndexed { i, t -> Track(t.name, t.tuning, measures[i], t.midiProgram, t.percussion) },
                tempo, tempoChanges.distinctBy { it.measureIndex to it.onset })
        } catch (e: Exception) {
            throw IllegalArgumentException("GP5 decoding failed at $stage: ${e.message}", e)
        }
    }

    private fun readMetadata(): Meta {
        val strings = List(9) { r.gpString() }
        val notices = r.i32le()
        require(notices in 0..500) { "Too many notice lines" }
        repeat(notices) { r.gpString() }
        return Meta(strings[0], strings[2])
    }

    private fun readMeasureHeaders(count: Int): List<TimeSignature> {
        var last = TimeSignature()
        return List(count) { index ->
            if (index != 0) r.skip(1)
            val flags = r.u8()
            val numerator = if (flags and 1 != 0) r.u8() else last.numerator
            val denominator = if (flags and 2 != 0) r.u8() else last.denominator
            if (flags and 8 != 0) r.skip(1) // repeat close
            if (flags and 0x20 != 0) { r.readIntByteString(); r.skip(4) } // section marker and color
            if (flags and 0x40 != 0) r.skip(2) // key root/type
            if (flags and 0x10 != 0) r.skip(1) // alternate endings
            if (flags and 3 != 0) r.skip(4) // beam grouping
            if (flags and 0x10 == 0) r.skip(1)
            r.skip(1) // triplet feel
            last = TimeSignature(numerator, denominator)
            last
        }
    }

    private fun readTrackHeader(index: Int, programs: IntArray): TrackHeader {
        if (index == 0 || minor == 0) r.skip(1)
        val flags = r.u8()
        val name = r.readFixedString(40).ifBlank { "Track ${index + 1}" }
        val count = r.i32le()
        require(count in 1..7) { "Invalid string count $count" }
        val tunings = List(7) { r.i32le() }.take(count)
        require(tunings.all { it in 0..127 }) { "Invalid tuning in $name" }
        r.skip(4) // port
        val channel = r.i32le()
        r.skip(4) // effect channel
        r.skip(8) // fret count + capo
        r.skip(4) // RGB + padding
        r.skip(2 + 3 + 12 + 12) // flags, accent, bank, humanize, clef, unknown
        r.skip(if (minor == 0) 15 else 16) // RSE instrument; 5.00 uses short + padding
        if (minor > 0) { r.skip(4); r.readIntByteString(); r.readIntByteString() }
        return TrackHeader(name, tunings, programs.getOrElse((channel - 1).coerceAtLeast(0)) { 24 },
            flags and 1 != 0 || channel == 10)
    }

    private fun readVoice(stringCount: Int, previousFrets: IntArray): List<Beat> {
        val count = r.i32le()
        require(count in 0..5000) { "Invalid GP5 beat count $count" }
        var onset = 0.0
        return buildList {
            repeat(count) {
                currentBeatOnset = onset
                val (beat, advances) = readBeat(stringCount, previousFrets)
                if (advances) {
                    add(beat.copy(onset = onset))
                    onset += beat.duration?.asWholeNotes() ?: 0.0
                }
            }
        }
    }

    private fun readBeat(stringCount: Int, previousFrets: IntArray): Pair<Beat, Boolean> {
        val flags = r.u8()
        val status = if (flags and 0x40 != 0) r.u8() else 1
        val exponent = r.i8() + 2
        require(exponent in 0..8) { "Invalid rhythmic value $exponent" }
        val denominator = 1 shl exponent
        val dotted = flags and 1 != 0
        val tuplet = if (flags and 0x20 != 0) r.i32le() else 0
        var numerator = if (dotted) 3 else 1
        var divisor = if (dotted) denominator * 2 else denominator
        if (tuplet > 0) {
            val normal = when (tuplet) {
                3 -> 2; 5, 6, 7 -> 4; 9, 10, 11, 12, 13 -> 8; else -> 1
            }
            numerator *= normal
            divisor *= tuplet
        }
        val duration = Duration(numerator, divisor)
        if (flags and 2 != 0) readChord()
        if (flags and 4 != 0) r.readIntByteString() // beat text
        if (flags and 8 != 0) readBeatEffects()
        if (flags and 16 != 0) readMixChange()
        val mask = r.u8()
        val notes = buildList {
            for (string in 1..stringCount) {
                if (mask and (1 shl (7 - string)) != 0) {
                    readNote(string, previousFrets)?.let { add(it) }
                }
            }
        }
        val secondFlags = r.u16le()
        if (secondFlags and 0x800 != 0) r.skip(1)
        return Beat(duration, if (status == 2 || status == 0) emptyList() else notes) to (status != 0)
    }

    private fun readChord() {
        r.skip(17)
        r.readFixedString(21)
        r.skip(4 + 4 + 7 * 4 + 1 + 5 * 3 + 7 + 1 + 7 + 1)
    }

    private fun readBeatEffects() {
        val f1 = r.u8()
        val f2 = r.u8()
        if (f1 and 0x20 != 0) r.skip(1)
        if (f2 and 0x04 != 0) readBend()
        if (f1 and 0x40 != 0) r.skip(2)
        if (f2 and 0x02 != 0) r.skip(1)
    }

    private fun readMixChange() {
        r.skip(1) // instrument program
        r.skip(if (minor == 0) 15 else 16)
        if (minor == 0) r.skip(1)
        val values = List(6) { r.i8() }
        r.readIntByteString()
        val tempo = r.i32le()
        if (tempo in 20..400) {
            tempoChanges += TempoChange(currentMeasureIndex, currentBeatOnset, tempo)
        }
        repeat(values.count { it >= 0 }) { r.skip(1) }
        if (tempo >= 0) {
            r.skip(1)
            if (minor > 0) r.skip(1)
        }
        r.skip(2) // mix flags + wah
        if (minor > 0) { r.readIntByteString(); r.readIntByteString() }
    }

    private fun readNote(string: Int, previousFrets: IntArray): Note? {
        val flags = r.u8()
        val type = if (flags and 0x20 != 0) r.u8() else 1
        if (flags and 0x10 != 0) r.skip(1) // dynamic
        val fret = if (flags and 0x20 != 0) r.i8() else 0
        if (flags and 0x80 != 0) r.skip(2) // fingering
        if (flags and 1 != 0) r.skip(8) // duration percentage (BE double)
        r.skip(1) // GP5 accidental flags
        val techniques = if (flags and 8 != 0) readNoteEffects() else emptySet()
        val playedFret = if (type == 2 && previousFrets[string] >= 0)
            previousFrets[string] else fret.coerceIn(0, 99)
        if (type != 2) previousFrets[string] = playedFret
        return Note(string, playedFret, tied = type == 2,
            dead = type == 3, techniques = techniques)
    }

    private fun readNoteEffects(): Set<NoteTechnique> {
        val f1 = r.u8()
        val f2 = r.u8()
        val effects = mutableSetOf<NoteTechnique>()
        if (f1 and 1 != 0) { readBend(); effects += NoteTechnique.BEND }
        if (f1 and 2 != 0) effects += NoteTechnique.HAMMER
        if (f2 and 2 != 0) effects += NoteTechnique.PALM_MUTE
        if (f2 and 1 != 0) effects += NoteTechnique.STACCATO
        if (f2 and 0x40 != 0) effects += NoteTechnique.VIBRATO
        if (f2 and 8 != 0) effects += NoteTechnique.SLIDE
        if (f2 and 0x10 != 0) effects += NoteTechnique.HARMONIC
        if (f1 and 0x10 != 0) r.skip(5) // grace fret, dynamic, transition, duration, flags
        if (f2 and 4 != 0) r.skip(1) // tremolo picking
        if (f2 and 8 != 0) r.skip(1) // slide type
        if (f2 and 0x10 != 0) {
            when (r.u8()) {
                2 -> r.skip(3)
                3 -> r.skip(1)
            }
        }
        if (f2 and 0x20 != 0) r.skip(2) // trill fret + speed
        return effects
    }

    private fun readBend() {
        r.skip(5) // type + value
        val points = r.i32le()
        require(points in 0..10_000) { "Invalid bend point count" }
        r.skip(points * 9) // position, pitch, vibrato
    }
}

/** EN: Extra GP5 string encodings. ES: Codificaciones de cadenas propias de GP5. */
private fun GuitarProBinaryReader.readFixedString(width: Int): String {
    val size = u8()
    require(size in 0..width) { "Invalid fixed string length $size" }
    val value = read(size).toString(Charsets.ISO_8859_1)
    skip(width - size)
    return value.trim()
}
private fun GuitarProBinaryReader.readIntString(): String {
    val size = i32le()
    require(size in 0..1_000_000) { "Invalid text length $size" }
    return read(size).toString(Charsets.ISO_8859_1)
}
private fun GuitarProBinaryReader.readIntByteString(): String {
    val length = i32le()
    require(length in 0..1_000_001) { "Invalid text length $length" }
    if (length == 0) return ""
    skip(1)
    return read(length - 1).toString(Charsets.ISO_8859_1)
}
