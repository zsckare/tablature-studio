package com.tablaturestudio.playback

import com.tablaturestudio.core.*
import kotlin.test.*
import javax.sound.midi.ShortMessage

class MidiScoreCompilerTest {
    @Test fun demoProducesNoteEvents() {
        val sequence = MidiScoreCompiler.compile(DemoScore.create())
        assertEquals(2, sequence.tracks.size)
        val events = (0 until sequence.tracks[0].size()).map { sequence.tracks[0].get(it) }
        assertTrue(events.any { (it.message as? ShortMessage)?.command == ShortMessage.NOTE_ON })
        assertTrue(events.any { (it.message as? ShortMessage)?.command == ShortMessage.NOTE_OFF })
    }
    @Test fun quarterNoteLastsOneBeat() {
        val score = Score("Test", tracks = listOf(Track("Guitar", listOf(64), listOf(Measure(1, listOf(Beat(Duration(1, 4), listOf(Note(1, 0)))))))))
        val events = MidiScoreCompiler.compile(score).tracks[0]
        val noteEvents = (0 until events.size()).map { events.get(it) }
            .filter { (it.message as? ShortMessage)?.command in
                setOf(ShortMessage.NOTE_ON, ShortMessage.NOTE_OFF) }
        assertEquals(0L, noteEvents[0].tick)
        assertEquals(864L, noteEvents[1].tick)
    }
}
