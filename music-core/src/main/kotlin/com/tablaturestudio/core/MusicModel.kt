package com.tablaturestudio.core

/** EN: Immutable musical score. ES: Partitura musical inmutable. */
data class Score(val title: String, val artist: String? = null, val tracks: List<Track>) {
    init { require(tracks.isNotEmpty()) { "A score requires at least one track" } }
}

/** EN: Tuning is ordered from highest to lowest string. ES: Afinación de aguda a grave. */
data class Track(val name: String, val tuning: List<Int>, val measures: List<Measure>) {
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

data class Beat(val duration: Duration?, val notes: List<Note> = emptyList())

data class Note(val string: Int, val fret: Int) {
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
