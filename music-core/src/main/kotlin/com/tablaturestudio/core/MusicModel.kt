package com.tablaturestudio.core

/** EN: Immutable musical score. ES: Partitura musical inmutable. */
data class TempoChange(val measureIndex: Int, val onset: Double, val bpm: Int)

data class Score(
    val title: String,
    val artist: String? = null,
    val tracks: List<Track>,
    val tempo: Int = 120,
    val tempoChanges: List<TempoChange> = emptyList()
) {
    init {
        require(tracks.isNotEmpty()) { "A score requires at least one track" }
        require(tempo in 1..500) { "Tempo must be between 1 and 500 BPM" }
    }
}

/** EN: Tuning is ordered from highest to lowest string. ES: Afinación de aguda a grave. */
data class Track(val name: String, val tuning: List<Int>, val measures: List<Measure>, val midiProgram: Int = 24, val percussion: Boolean = false) {
    init { require(tuning.isNotEmpty()); require(measures.isNotEmpty()) }
}

data class TimeSignature(val numerator: Int = 4, val denominator: Int = 4) {
    init { require(numerator > 0); require(denominator > 0 && denominator and (denominator - 1) == 0) }
}

data class Measure(val number: Int, val beats: List<Beat>, val signature: TimeSignature = TimeSignature()) {
    init { require(number > 0) }
}

/** EN: Rational duration measured in whole notes. ES: Duración racional en redondas. */
data class Duration(val numerator: Int, val denominator: Int) {
    init { require(numerator > 0 && denominator > 0) }
    fun asWholeNotes(): Double = numerator.toDouble() / denominator
}

/** EN: Explicit onset is measured as a fraction of a whole note, supporting polyphonic GP5 voices.
 * ES: El inicio explícito es una fracción de redonda, compatible con voces GP5 simultáneas.
 */
data class Beat(val duration: Duration?, val notes: List<Note> = emptyList(), val onset: Double? = null)

enum class NoteTechnique { BEND, HAMMER, PALM_MUTE, SLIDE, HARMONIC, VIBRATO, STACCATO }

data class Note(
    val string: Int,
    val fret: Int,
    val tied: Boolean = false,
    val dead: Boolean = false,
    val techniques: Set<NoteTechnique> = emptySet()
) {
    init { require(string > 0); require(fret >= 0) }
}

/** EN: Validates cross-object rules. ES: Valida reglas entre entidades. */
object ScoreValidator {
    fun validate(score: Score): List<String> = buildList {
        score.tracks.forEach { track ->
            track.measures.forEach { measure ->
                measure.beats.forEach { beat ->
                    beat.notes.forEach { note ->
                        if (note.string > track.tuning.size) add("String ${note.string} exceeds ${track.tuning.size} strings")
                    }
                }
            }
        }
    }
}
