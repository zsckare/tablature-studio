package com.tablaturestudio.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.tablaturestudio.core.*
import com.tablaturestudio.importer.*
import com.tablaturestudio.renderer.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import com.tablaturestudio.playback.*

private val background = Color(0xFF121824)
private val surface = Color(0xFF202B3B)
private val accent = Color(0xFFE0B676)

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Tablature Studio",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)) {
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = background, surface = surface)) {
            StudioApp()
        }
    }
}

/** EN: Desktop shell and file-open workflow. ES: Interfaz principal y apertura de archivos. */
@Composable
private fun StudioApp() {
    var score by remember { mutableStateOf(DemoScore.create()) }
    var selectedTrack by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var message by remember { mutableStateOf("Demo score ready · Open a GP5 file to play real tablatures") }
    val registry = remember { ImportRegistry() }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val player = remember {
        try { JvmMidiPlaybackEngine() } catch (e: Exception) {
            playbackError = "MIDI unavailable: ${e.message}"
            null
        }
    }
    DisposableEffect(player) { onDispose { player?.close() } }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var muted by remember { mutableStateOf(setOf<Int>()) }
    var solo by remember { mutableStateOf(setOf<Int>()) }
    var trackVolumes by remember { mutableStateOf(mapOf<Int, Float>()) }
    var loopEnabled by remember { mutableStateOf(false) }
    var loopStart by remember { mutableLongStateOf(0L) }
    var metronome by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            position = player?.positionTicks ?: 0L
            length = player?.lengthTicks ?: 0L
            kotlinx.coroutines.delay(100)
        }
    }
    fun loadPlayback(current: Score) {
        try {
            player?.load(current)
            muted = emptySet()
            solo = emptySet()
            trackVolumes = emptyMap()
            playbackError = null
            loopEnabled = false
            loopStart = 0L
            metronome = false
        } catch (e: Exception) { playbackError = "Audio error: ${e.message}" }
    }
    LaunchedEffect(player) { loadPlayback(score) }
    Column(Modifier.fillMaxSize().background(background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("TABLATURE STUDIO", fontWeight = FontWeight.Bold, color = accent)
            Button(onClick = {
                val dialog = FileDialog(null as Frame?, "Open tablature", FileDialog.LOAD)
                dialog.isVisible = true
                val filename = dialog.file
                if (filename != null) {
                    val file = File(dialog.directory, filename)
                    try {
                        when (val result = registry.open(file.name, file.readBytes())) {
                            is ImportResult.Success -> { score = result.score; selectedTrack = 0; loadPlayback(score); message = "Opened ${file.name}" }
                            is ImportResult.Failure -> message = result.message
                        }
                    } catch (e: Exception) { message = "Could not open file: ${e.message}" }
                }
            }) { Text("Open file") }
            OutlinedButton(onClick = { score = DemoScore.create(); selectedTrack = 0; loadPlayback(score); message = "Demo score loaded" }) { Text("Demo") }
            Spacer(Modifier.weight(1f))
            Text("Offline · Native Kotlin", color = Color.LightGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(score.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(score.artist.orEmpty(), color = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(225.dp).fillMaxHeight().background(surface)
                .verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text("TRACKS", color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                score.tracks.forEachIndexed { index, track ->
                    TextButton(onClick = { selectedTrack = index }, modifier = Modifier.fillMaxWidth()) {
                        Text(track.name, color = if (index == selectedTrack) accent else Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(enabled = player != null, onClick = {
                            val next = index !in muted
                            player?.setTrackMuted(index, next)
                            muted = if (next) muted + index else muted - index
                        }) { Text(if (index in muted) "Unmute" else "Mute", fontSize = 10.sp) }
                        OutlinedButton(enabled = player != null, onClick = {
                            val next = index !in solo
                            player?.setTrackSolo(index, next)
                            solo = if (next) solo + index else solo - index
                        }) { Text(if (index in solo) "Unsolo" else "Solo", fontSize = 10.sp) }
                    }
                    Slider(value = trackVolumes[index] ?: 95f, onValueChange = { volume ->
                        trackVolumes = trackVolumes + (index to volume)
                        player?.setTrackVolume(index, volume.toInt())
                    }, valueRange = 0f..127f, enabled = player != null)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(surface).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${score.tracks[selectedTrack].name} · ${score.tracks[selectedTrack].tuning.size} strings", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { zoom = (zoom - .2f).coerceAtLeast(.6f) }) { Text("−") }
                    Text(" ${(zoom * 100).toInt()}% ", color = Color.White)
                    OutlinedButton(onClick = { zoom = (zoom + .2f).coerceAtMost(2.4f) }) { Text("+") }
                }
                Spacer(Modifier.height(12.dp))
                val barTicks = remember(score) { MidiScoreCompiler.measureStartTicks(score) }
                val currentBar = remember(position, barTicks) {
                    barTicks.indexOfLast { it <= position }.coerceAtLeast(0)
                }
                TabCanvas(score.tracks[selectedTrack], zoom, currentBar, player?.isPlaying == true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Position", color = Color.LightGray, fontSize = 12.sp)
            Slider(value = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f,
                onValueChange = { player?.seekTo((it * length).toLong()) },
                enabled = player != null && length > 0, modifier = Modifier.weight(1f))
            Text("${position / MidiScoreCompiler.PPQ} / ${length / MidiScoreCompiler.PPQ} beats",
                color = Color.LightGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().background(surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = player != null, onClick = {
                try { player?.play() } catch (e: Exception) { playbackError = e.message }
            }) { Text("Play") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(enabled = player != null, onClick = { player?.pause() }) { Text("Pause") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(enabled = player != null, onClick = { player?.stop() }) { Text("Stop") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(enabled = player != null, onClick = {
                speed = (speed - 0.25).coerceAtLeast(0.25)
                player?.setTempoMultiplier(speed)
            }) { Text("−") }
            Text("${(speed * 100).toInt()}%", color = Color.White)
            OutlinedButton(enabled = player != null, onClick = {
                speed = (speed + 0.25).coerceAtMost(2.0)
                player?.setTempoMultiplier(speed)
            }) { Text("+") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(enabled = player != null && length > 1, onClick = {
                if (loopEnabled) {
                    player?.clearLoop()
                    loopEnabled = false
                } else {
                    val start = (position / MidiScoreCompiler.PPQ) * MidiScoreCompiler.PPQ
                    val end = (start + 4L * MidiScoreCompiler.PPQ).coerceAtMost(length)
                    if (end > start) {
                        player?.setLoop(start, end)
                        loopEnabled = true
                        message = "Loop enabled: 4 beats from current position"
                    }
                }
            }) { Text(if (loopEnabled) "Loop off" else "Loop 4 beats") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(enabled = player != null, onClick = {
                val dialog = FileDialog(null as Frame?, "Load SoundFont (.sf2)", FileDialog.LOAD)
                dialog.isVisible = true
                dialog.file?.let { name ->
                    try {
                        message = if (player?.loadSoundFont(File(dialog.directory, name)) == true) "SoundFont loaded" else "SoundFont unsupported by MIDI synthesizer"
                    } catch (e: Exception) { playbackError = "SoundFont error: ${e.message}" }
                }
            }) { Text("SF2") }
            Spacer(Modifier.width(12.dp))
            Text(playbackError ?: message, color = Color.LightGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().background(surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = player != null, onClick = {
                loopStart = position
                message = "A set at beat ${position / MidiScoreCompiler.PPQ}"
            }) { Text("Set A") }
            OutlinedButton(enabled = player != null && length > 0, onClick = {
                if (position > loopStart) {
                    player?.setLoop(loopStart, position)
                    loopEnabled = true
                    message = "A/B loop enabled"
                } else message = "Move after A before setting B"
            }) { Text("Set B / Loop") }
            OutlinedButton(enabled = player != null, onClick = {
                player?.clearLoop()
                loopEnabled = false
                message = "Loop cleared"
            }) { Text("Clear loop") }
            OutlinedButton(enabled = player != null, onClick = {
                metronome = !metronome
                player?.setMetronome(metronome)
            }) { Text(if (metronome) "Metronome on" else "Metronome off") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {
                val dialog = FileDialog(null as Frame?, "Export MIDI", FileDialog.SAVE)
                dialog.file = score.title.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".mid"
                dialog.isVisible = true
                dialog.file?.let { filename ->
                    try {
                        val target = File(dialog.directory, filename)
                        javax.sound.midi.MidiSystem.write(
                            MidiScoreCompiler.compile(score), 1, target)
                        message = "MIDI exported: ${target.name}"
                    } catch (e: Exception) { message = "MIDI export failed: ${e.message}" }
                }
            }) { Text("Export MIDI") }
        }
    }
}

/**
 * EN: Virtualized native Compose tab renderer. Only visible measures create text overlays.
 * ES: Renderizador Compose virtualizado: solo dibuja los compases visibles.
 */
@Composable
private fun TabCanvas(track: Track, zoom: Float, currentMeasure: Int, playing: Boolean) {
    val layout = remember(track) { TabLayoutEngine.layout(track) }
    val listState = rememberLazyListState()
    LaunchedEffect(currentMeasure, playing, track) {
        if (playing && currentMeasure in layout.measures.indices &&
            currentMeasure != listState.firstVisibleItemIndex) {
            listState.animateScrollToItem(currentMeasure)
        }
    }
    LazyRow(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp)) {
        items(layout.measures.size) { i ->
            val measure = layout.measures[i]
            val isCurrent = i == currentMeasure
            val width = (measure.width * zoom).dp
            val height = ((layout.height + 65f) * zoom).dp
            Box(Modifier.width(width).height(height)) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = zoom * density
                    val originY = 35f * density
                    if (isCurrent) {
                        drawRect(Color(0x22E0B676))
                    }
                    for (s in 0 until layout.stringCount) {
                        val y = originY + s * TabLayoutEngine.STRING_SPACING * scale
                        drawLine(Color(0xFF728097), Offset(0f, y),
                            Offset(measure.width * scale, y), 1.dp.toPx())
                    }
                    drawLine(Color(0xFFA3AFC1), Offset(0f, originY),
                        Offset(0f, originY + layout.height * scale), 1.dp.toPx())
                    if (i == layout.measures.lastIndex) {
                        val x = measure.width * scale
                        drawLine(Color(0xFFA3AFC1), Offset(x, originY),
                            Offset(x, originY + layout.height * scale), 1.dp.toPx())
                    }
                }
                Text("${measure.number}", modifier = Modifier.offset(y = 5.dp),
                    color = if (isCurrent) accent else Color.LightGray, fontSize = 11.sp)
                measure.notes.forEach { note ->
                    Box(Modifier.offset(x = ((note.x - measure.x) * zoom - 10).dp,
                        y = (35 + note.y * zoom - 10).dp).size(20.dp)
                        .background(if (isCurrent) Color(0xFF3A3540) else surface),
                        contentAlignment = Alignment.Center) {
                        Text(note.fret.toString(), color = Color.White, fontSize = 12.sp,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
