package com.tablaturestudio.playback

import com.tablaturestudio.core.*
import java.io.File
import javax.sound.midi.*
import kotlin.math.max
import kotlin.math.roundToLong

/** EN: Playback boundary independent of Compose. ES: Contrato de reproducción independiente de Compose. */
interface PlaybackEngine : AutoCloseable {
    fun load(score: Score)
    fun play()
    fun pause()
    fun stop()
    fun setTempoMultiplier(value: Double)
    fun loadSoundFont(file: File): Boolean
    val isPlaying: Boolean
    val positionTicks: Long
    val lengthTicks: Long
    fun seekTo(tick: Long)
    fun setTrackMuted(index: Int, muted: Boolean)
    fun setTrackSolo(index: Int, solo: Boolean)
    fun setTrackVolume(index: Int, volume: Int)
    fun setMetronome(enabled: Boolean)
    fun setLoop(startTick: Long, endTick: Long)
    fun clearLoop()
}

/**
 * EN: Offline MIDI playback with JVM synthesizer and optional SF2 soundbank.
 * ES: Reproducción MIDI local con sintetizador JVM y banco SF2 opcional.
 * No browser, server, database, or internet is involved.
 */
class JvmMidiPlaybackEngine : PlaybackEngine {
    private val sequencer: Sequencer = MidiSystem.getSequencer(false).also { it.open() }
    private val synthesizer: Synthesizer = MidiSystem.getSynthesizer().also { it.open() }
    private val receiver: Receiver = synthesizer.receiver
    private var baseTempo = 120f
    private var tempoMultiplier = 1.0
    private var trackChannels = emptyList<Int>()
    private var loadedScore: Score? = null
    private var metronomeEnabled = false
    init { sequencer.transmitter.receiver = receiver }

    override val isPlaying: Boolean get() = sequencer.isRunning
    override val positionTicks: Long get() = sequencer.tickPosition
    override val lengthTicks: Long get() = sequencer.tickLength

    override fun seekTo(tick: Long) {
        silence()
        sequencer.tickPosition = tick.coerceIn(0L, lengthTicks)
    }

    override fun setTrackMuted(index: Int, muted: Boolean) {
        require(index in 0 until sequencer.sequence.tracks.size)
        sequencer.setTrackMute(index, muted)
        if (muted) silence()
    }

    override fun setTrackSolo(index: Int, solo: Boolean) {
        require(index in 0 until sequencer.sequence.tracks.size)
        sequencer.setTrackSolo(index, solo)
        if (solo) silence()
    }

    /** EN: MIDI volume 0..127 per instrument. ES: Volumen MIDI 0..127 por instrumento. */
    override fun setTrackVolume(index: Int, volume: Int) {
        require(volume in 0..127 && index in trackChannels.indices)
        synthesizer.channels[trackChannels[index]]?.controlChange(7, volume)
    }

    /** EN: Rebuild MIDI sequence with or without metronome while retaining playhead.
     * ES: Reconstruir la secuencia con o sin metrónomo conservando la posición.
     */
    override fun setMetronome(enabled: Boolean) {
        if (metronomeEnabled == enabled) return
        metronomeEnabled = enabled
        val score = loadedScore ?: return
        val wasPlaying = sequencer.isRunning
        val tick = sequencer.tickPosition
        sequencer.stop()
        sequencer.sequence = MidiScoreCompiler.compile(score, enabled)
        sequencer.tickPosition = tick.coerceIn(0, sequencer.tickLength)
        sequencer.tempoFactor = tempoMultiplier.toFloat()
        if (wasPlaying) sequencer.start()
    }

    override fun setLoop(startTick: Long, endTick: Long) {
        require(startTick >= 0 && endTick > startTick && endTick <= lengthTicks)
        sequencer.loopCount = 0
        sequencer.loopStartPoint = startTick
        sequencer.loopEndPoint = endTick
        sequencer.loopCount = Sequencer.LOOP_CONTINUOUSLY
    }

    override fun clearLoop() {
        sequencer.loopCount = 0
        sequencer.loopStartPoint = 0
        sequencer.loopEndPoint = -1
    }

    override fun load(score: Score) {
        stop()
        clearLoop()
        loadedScore = score
        metronomeEnabled = false
        sequencer.sequence = MidiScoreCompiler.compile(score)
        trackChannels = score.tracks.mapIndexed { i, track -> MidiScoreCompiler.channelFor(i, track.percussion) }
        baseTempo = score.tempo.toFloat()
        sequencer.tempoFactor = tempoMultiplier.toFloat()
    }

    override fun play() { if (sequencer.sequence != null) sequencer.start() }
    override fun pause() { sequencer.stop(); silence() }
    override fun stop() { sequencer.stop(); sequencer.tickPosition = 0; silence() }

    override fun setTempoMultiplier(value: Double) {
        require(value in 0.25..2.0)
        tempoMultiplier = value
        sequencer.tempoFactor = value.toFloat()
    }

    override fun loadSoundFont(file: File): Boolean {
        require(file.isFile && file.extension.equals("sf2", ignoreCase = true)) { "Select an existing .sf2 file" }
        val soundbank = MidiSystem.getSoundbank(file)
        if (!synthesizer.isSoundbankSupported(soundbank)) return false
        synthesizer.defaultSoundbank?.let { synthesizer.unloadAllInstruments(it) }
        return synthesizer.loadAllInstruments(soundbank)
    }

    private fun silence() { synthesizer.channels.forEach { it?.allNotesOff() } }

    override fun close() {
        stop()
        sequencer.close()
        synthesizer.close()
    }
}

/**
 * EN: Translates tab notes, polyphonic onsets, time signatures and patches into standard MIDI.
 * ES: Traduce notas, voces simultáneas, compases e instrumentos a MIDI estándar.
 */
object MidiScoreCompiler {
    const val PPQ = 960
    private val melodicChannels = (0..15).filter { it != 9 }

    fun channelFor(index: Int, percussion: Boolean): Int =
        if (percussion) 9 else melodicChannels[index % melodicChannels.size]

    fun measureStartTicks(score: Score): LongArray {
        val reference = score.tracks.first().measures
        var tick = 0L
        return LongArray(reference.size) { index ->
            val start = tick
            val sig = reference[index].signature
            tick += (sig.numerator.toDouble() * 4 * PPQ / sig.denominator).roundToLong().coerceAtLeast(1)
            start
        }
    }

    private data class PendingNote(val string: Int, val pitch: Int, val velocity: Int,
        val startTick: Long, var endTick: Long)

    fun compile(score: Score, withMetronome: Boolean = false): Sequence {
        val sequence = Sequence(Sequence.PPQ, PPQ)
        val starts = measureStartTicks(score)
        val total = if (starts.isEmpty()) 1L else {
            val sig = score.tracks.first().measures.last().signature
            starts.last() + (sig.numerator.toDouble() * 4 * PPQ / sig.denominator).roundToLong()
        }
        score.tracks.forEachIndexed { index, track ->
            val midiTrack = sequence.createTrack()
            if (index == 0) {
                tempoEvent(midiTrack, score.tempo, 0L)
                score.tempoChanges.forEach { tempo ->
                    if (tempo.measureIndex in starts.indices && tempo.bpm in 1..500) {
                        val tick = starts[tempo.measureIndex] +
                            (tempo.onset * 4 * PPQ).roundToLong()
                        tempoEvent(midiTrack, tempo.bpm, tick)
                    }
                }
            }
            val channel = channelFor(index, track.percussion)
            add(midiTrack, ShortMessage.PROGRAM_CHANGE, channel, track.midiProgram.coerceIn(0,127), 0, 0)
            val notesToSchedule = mutableListOf<PendingNote>()
            val previousByString = mutableMapOf<Int, PendingNote>()
            track.measures.forEachIndexed barLoop@{ bar, measure ->
                if (bar >= starts.size) return@barLoop
                val barStart = starts[bar]
                var runningWholeNotes = 0.0
                measure.beats.forEach { beat ->
                    val start = beat.onset ?: runningWholeNotes
                    val duration = beat.duration?.asWholeNotes() ?: 0.25
                    val startTick = barStart + (start * 4 * PPQ).roundToLong()
                    val durationTicks = (duration * 4 * PPQ).roundToLong().coerceAtLeast(1)
                    for (note in beat.notes) {
                        if (note.string !in 1..track.tuning.size) continue
                        if (note.tied) {
                            previousByString[note.string]?.let { previous ->
                                previous.endTick = max(previous.endTick, startTick + durationTicks)
                            }
                            continue
                        }
                        val pitch = if (track.percussion) note.fret else track.tuning[note.string - 1] + note.fret
                        if (pitch !in 0..127) continue
                        val velocity = if (note.dead) 48 else 96
                        val fraction = when {
                            note.dead || NoteTechnique.STACCATO in note.techniques -> 0.4
                            NoteTechnique.PALM_MUTE in note.techniques -> 0.6
                            else -> 0.9
                        }
                        val noteEvent = PendingNote(note.string, pitch, velocity, startTick,
                            startTick + max(1, (durationTicks * fraction).roundToLong()))
                        previousByString[note.string] = noteEvent
                        notesToSchedule += noteEvent
                    }
                    if (beat.onset == null) runningWholeNotes += duration
                }
            }
            notesToSchedule.forEach { event ->
                add(midiTrack, ShortMessage.NOTE_ON, channel, event.pitch, event.velocity, event.startTick)
                add(midiTrack, ShortMessage.NOTE_OFF, channel, event.pitch, 0, event.endTick)
            }
            add(midiTrack, ShortMessage.CONTROL_CHANGE, channel, 123, 0, total + 1)
        }
        if (withMetronome) {
            val clickTrack = sequence.createTrack()
            score.tracks.first().measures.forEachIndexed { i, measure ->
                val start = starts[i]
                val beatTicks = (4.0 * PPQ / measure.signature.denominator).roundToLong()
                repeat(measure.signature.numerator) { beat ->
                    val tick = start + beat * beatTicks
                    add(clickTrack, ShortMessage.NOTE_ON, 9, if (beat == 0) 76 else 77, 95, tick)
                    add(clickTrack, ShortMessage.NOTE_OFF, 9, if (beat == 0) 76 else 77, 0, tick + 90)
                }
            }
        }
        return sequence
    }

    private fun tempoEvent(track: javax.sound.midi.Track, bpm: Int, tick: Long) {
        val micros = (60_000_000 / bpm).coerceAtLeast(1)
        val data = byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte())
        track.add(MidiEvent(MetaMessage(0x51, data, 3), tick))
    }

    private fun add(track: javax.sound.midi.Track, command: Int, channel: Int, data1: Int, data2: Int, tick: Long) {
        track.add(MidiEvent(ShortMessage(command, channel, data1, data2), tick))
    }
}
