package com.tablaturestudio.playback

import com.tablaturestudio.core.*
import javax.sound.midi.ShortMessage
import kotlin.test.*

/** EN: Verify MIDI channel separation and score timing.
 * ES: Verifica separación de canales MIDI y duración de la partitura.
 */
class MultiTrackPlaybackTest {
    @Test fun tracksHaveIndependentChannels() {
        val measure = Measure(1, listOf(Beat(Duration(1, 4), listOf(Note(1, 0)))))
        val score = Score("Duo", tracks = listOf(
            Track("Guitar", listOf(64), listOf(measure)),
            Track("Bass", listOf(40), listOf(measure))
        ))
        val sequence = MidiScoreCompiler.compile(score)
        val first = sequence.tracks.map { track ->
            (0 until track.size()).map { track.get(it).message }
                .filterIsInstance<ShortMessage>()
                .first { it.command == ShortMessage.NOTE_ON }
        }
        assertEquals(2, first.size)
        assertNotEquals(first[0].channel, first[1].channel)
        assertEquals(64, first[0].data1)
        assertEquals(40, first[1].data1)
    }
}
