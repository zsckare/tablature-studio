package com.tablaturestudio.playback

import com.tablaturestudio.core.*
import javax.sound.midi.ShortMessage
import kotlin.test.*

class GuitarProMidiTimingTest {
    @Test fun `empty measures do not collapse the timeline`() {
        val silent = Track("Silent", listOf(64), listOf(
            Measure(1, emptyList()),
            Measure(2, listOf(Beat(Duration(1,4), listOf(Note(1,3)), onset = 0.0)))
        ))
        val sequence = MidiScoreCompiler.compile(Score("Song", tracks=listOf(silent), tempo=140))
        val on = noteOns(sequence.tracks[0]).single()
        assertEquals(4L * MidiScoreCompiler.PPQ, on.tick)
        assertEquals(67, (on.message as ShortMessage).data1)
    }

    @Test fun `second voice notes can start simultaneously`() {
        val track = Track("Polyphonic", listOf(64,59), listOf(Measure(1,listOf(
            Beat(Duration(1,4), listOf(Note(1,0)), onset=0.0),
            Beat(Duration(1,2), listOf(Note(2,2)), onset=0.0)
        ))))
        val on = noteOns(MidiScoreCompiler.compile(Score("X", tracks=listOf(track))).tracks[0])
        assertEquals(2, on.size)
        assertTrue(on.all { it.tick == 0L })
    }

    @Test fun `percussion receives drum channel and existing tempo is used by sequencer`() {
        val track = Track("Drums", listOf(0), listOf(Measure(1, listOf(Beat(Duration(1,4), listOf(Note(1,36)))))), percussion=true)
        val seq = MidiScoreCompiler.compile(Score("Drums",tracks=listOf(track),tempo=140))
        val note = noteOns(seq.tracks[0]).first().message as ShortMessage
        assertEquals(9, note.channel)
        assertEquals(36, note.data1)
        assertEquals(140, Score("X",tracks=listOf(track), tempo=140).tempo)
    }

    @Test fun `metronome creates accented and unaccented beats`() {
        val seq = MidiScoreCompiler.compile(DemoScore.create(), withMetronome=true)
        assertEquals(DemoScore.create().tracks.size + 1, seq.tracks.size)
        val on = noteOns(seq.tracks.last())
        assertTrue(on.size >= 4)
        assertEquals(76, (on[0].message as ShortMessage).data1)
        assertEquals(77, (on[1].message as ShortMessage).data1)
    }

    @Test fun `tie extends preceding note rather than playing a second attack`() {
        val track = Track("Guitar",listOf(64),listOf(Measure(1,listOf(
            Beat(Duration(1,4),listOf(Note(1,3)),onset=0.0),
            Beat(Duration(1,4),listOf(Note(1,0,tied=true)),onset=0.25)
        ))))
        val events = MidiScoreCompiler.compile(Score("Ties",tracks=listOf(track))).tracks[0]
        val on = noteOns(events)
        assertEquals(1,on.size)
        val off = (0 until events.size()).map { events.get(it) }.first {
            (it.message as? ShortMessage)?.command == ShortMessage.NOTE_OFF
        }
        assertEquals(2L * MidiScoreCompiler.PPQ, off.tick)
    }

    private fun noteOns(track: javax.sound.midi.Track) = (0 until track.size()).map {track.get(it)}
        .filter { (it.message as? ShortMessage)?.command == ShortMessage.NOTE_ON }
}
