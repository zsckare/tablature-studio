package com.tablaturestudio.renderer

import com.tablaturestudio.core.*

/** EN: UI-independent score geometry in logical units.
 * ES: Geometría de partitura independiente de la interfaz.
 */
data class PositionedNote(val x: Float, val y: Float, val fret: Int)
data class PositionedMeasure(val number: Int, val x: Float, val width: Float, val notes: List<PositionedNote>)
data class TabLayout(val width: Float, val height: Float, val stringCount: Int, val measures: List<PositionedMeasure>)

object TabLayoutEngine {
    const val STRING_SPACING = 22f
    const val MEASURE_WIDTH = 180f
    fun layout(track: Track): TabLayout {
        val measures = track.measures.mapIndexed { index, measure ->
            val left = index * MEASURE_WIDTH
            val beatCount = measure.beats.size.coerceAtLeast(1)
            val notes = measure.beats.flatMapIndexed { beatIndex, beat ->
                beat.notes.map { note ->
                    PositionedNote(left + (beatIndex + .5f) * MEASURE_WIDTH / beatCount,
                        (note.string - 1) * STRING_SPACING, note.fret)
                }
            }
            PositionedMeasure(measure.number, left, MEASURE_WIDTH, notes)
        }
        return TabLayout(measures.size * MEASURE_WIDTH, (track.tuning.size - 1) * STRING_SPACING + 1, track.tuning.size, measures)
    }
}
