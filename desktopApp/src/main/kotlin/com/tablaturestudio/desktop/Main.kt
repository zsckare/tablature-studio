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
import androidx.compose.ui.platform.LocalDensity
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
 * EN: Sheet-music layout inspired by MuseScore: responsive rows of measures on paper.
 * ES: Diseño de partitura inspirado en MuseScore: filas adaptables de compases sobre papel.
 *
 * EN: Uses only native Compose drawing; it does not use WebView.
 * ES: Utiliza únicamente dibujo nativo Compose, sin WebView.
 */
@Composable
private fun TabCanvas(track: Track, zoom: Float, currentMeasure: Int, playing: Boolean) {
    val layout = remember(track) { TabLayoutEngine.layout(track) }
    val paper = Color(0xFFFFFEFA)
    val ink = Color(0xFF263242)
    val staffColor = Color(0xFF8C95A2)
    val selected = Color(0xFF6E55BB)
    val state = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF303947))) {
        // EN: Fit complete systems to the available width, with no horizontal scroll.
        // ES: Ajustar los sistemas al ancho disponible, sin desplazamiento horizontal.
        val pagePadding = 36.dp
        val usableWidth = (maxWidth - pagePadding * 2 - 92.dp).coerceAtLeast(180.dp)
        val targetMeasureWidth = 205.dp * zoom
        val measuresPerSystem = (usableWidth.value / targetMeasureWidth.value).toInt().coerceIn(1, 5)
        val systems = remember(layout, measuresPerSystem) { layout.measures.chunked(measuresPerSystem) }
        val activeSystem = (currentMeasure / measuresPerSystem).coerceIn(0, (systems.size - 1).coerceAtLeast(0))

        LaunchedEffect(activeSystem, playing, track, measuresPerSystem) {
            if (playing && systems.isNotEmpty()) state.animateScrollToItem(activeSystem)
        }

        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(systems.size) { systemIndex ->
                val measures = systems[systemIndex]
                val first = systemIndex == 0
                Column(
                    Modifier.fillMaxWidth()
                        .background(paper)
                        .padding(horizontal = pagePadding, vertical = 22.dp)
                ) {
                    if (first) {
                        Text(track.name, color = ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "GUITAR TABLATURE  ·  ${track.tuning.size} STRINGS",
                            color = Color(0xFF647183), fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.width(54.dp).padding(top = 43.dp)) {
                            Text("TAB", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.weight(1f)) {
                            measures.forEachIndexed { index, measure ->
                                val absolute = systemIndex * measuresPerSystem + index
                                val active = absolute == currentMeasure
                                BoxWithConstraints(
                                    Modifier.weight(1f).height(310.dp * zoom)
                                ) {
                                    val measureWidthPx = constraints.maxWidth.toFloat()
                                    val pixelDensity = LocalDensity.current.density
                                    val staffTop = 198.dp * zoom
                                    val staffSpacing = 15.dp * zoom
                                    val staffHeight = staffSpacing * (layout.stringCount - 1)
                                    Canvas(Modifier.fillMaxSize()) {
                                        val top = staffTop.toPx()
                                        val spacing = staffSpacing.toPx()
                                        if (active) {
                                            drawRect(
                                                selected.copy(alpha = 0.11f),
                                                topLeft = Offset(0f, top - 10.dp.toPx()),
                                                size = androidx.compose.ui.geometry.Size(
                                                    size.width, spacing * (layout.stringCount - 1) + 20.dp.toPx()
                                                )
                                            )
                                        }
                                        for (string in 0 until layout.stringCount) {
                                            val y = top + string * spacing
                                            drawLine(staffColor, Offset(0f, y), Offset(size.width, y), 0.85.dp.toPx())
                                        }
                                        drawLine(
                                            ink, Offset(size.width - 1.dp.toPx(), top),
                                            Offset(size.width - 1.dp.toPx(), top + staffHeight.toPx()),
                                            1.dp.toPx()
                                        )
                                        if (index == 0) drawLine(
                                            ink, Offset(0f, top),
                                            Offset(0f, top + staffHeight.toPx()), 1.dp.toPx()
                                        )
                                    }
                                    // EN: Conventional treble staff above the tablature, sharing the same measure.
                                    // ES: Pentagrama en clave de sol sobre la tablatura, en el mismo compás.
                                    StandardStaff(
                                        measure = track.measures[absolute],
                                        tuning = track.tuning,
                                        active = active,
                                        zoom = zoom,
                                        modifier = Modifier.fillMaxWidth().height(175.dp * zoom)
                                    )
                                    Text(
                                        "${measure.number}",
                                        modifier = Modifier.offset(x = 7.dp, y = 5.dp),
                                        color = if (active) selected else Color(0xFF657184),
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                    measure.notes.forEach { note ->
                                        // EN: Scale each note's original position into the available measure width.
                                        // ES: Escalar la posición original de cada nota al ancho disponible.
                                        val relative = ((note.x - measure.x) / measure.width)
                                            .coerceIn(0.05f, 0.94f)
                                        val x = (measureWidthPx / pixelDensity * relative).dp
                                        val y = staffTop + (note.y / TabLayoutEngine.STRING_SPACING) * staffSpacing
                                        Box(
                                            Modifier.offset(x = x - 10.dp, y = y - 10.dp)
                                                .size(21.dp)
                                                .background(
                                                    if (active) Color(0xFFECE6FB) else paper
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                note.fret.toString(),
                                                color = if (active) selected else ink,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * EN: Draw conventional pitches using the actual MIDI pitch (open-string tuning + fret).
 * A treble clef is assumed for guitar; guitar sounds an octave lower than written.
 * ES: Dibuja alturas musicales a partir del MIDI real (afinación + traste).
 * Se usa clave de sol para guitarra, que suena una octava debajo de lo escrito.
 */
@Composable
private fun StandardStaff(
    measure: Measure,
    tuning: List<Int>,
    active: Boolean,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    val ink = Color(0xFF27313F)
    val lineColor = Color(0xFF737F8E)
    val accidental = Color(0xFF6752B8)
    BoxWithConstraints(modifier) {
        val beats = measure.beats
        val measureLength = measure.signature.numerator.toDouble() / measure.signature.denominator
        val positions = remember(measure) {
            var elapsed = 0.0
            beats.map { beat ->
                val start = beat.onset ?: elapsed
                if (beat.onset == null) elapsed += beat.duration?.asWholeNotes() ?: 0.25
                (start / measureLength).coerceIn(0.0, 1.0).toFloat() to beat
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val top = 46.dp.toPx() * zoom
            val spacing = 12.dp.toPx() * zoom
            val half = spacing / 2f
            val left = 0f
            val right = size.width
            for (line in 0..4) {
                val y = top + line * spacing
                drawLine(lineColor, Offset(left, y), Offset(right, y), 0.85.dp.toPx())
            }
            drawLine(ink, Offset(right - 1.dp.toPx(), top),
                Offset(right - 1.dp.toPx(), top + 4 * spacing), 1.dp.toPx())
            // EN: Bottom treble line is E4 (MIDI 64), top line is F5.
            // ES: Línea inferior: Mi4 (MIDI 64); línea superior: Fa5.
            val naturalSemitones = intArrayOf(0, 2, 4, 5, 7, 9, 11)
            fun diatonicIndex(pitch: Int): Int {
                val octave = Math.floorDiv(pitch, 12) - 1
                val semitone = Math.floorMod(pitch, 12)
                val letter = naturalSemitones.indexOfLast { it <= semitone }.coerceAtLeast(0)
                return octave * 7 + letter
            }
            val e4Index = 4 * 7 + 2
            positions.forEach { (fraction, beat) ->
                val x = (0.10f + fraction * 0.80f) * right
                beat.notes.forEach { note ->
                    val pitch = tuning.getOrNull(note.string - 1)?.plus(note.fret) ?: return@forEach
                    // EN: Transpose guitar one octave up for standard written notation.
                    // ES: Transponer guitarra una octava arriba para notación escrita estándar.
                    val writtenPitch = pitch + 12
                    val steps = diatonicIndex(writtenPitch) - e4Index
                    val y = top + 4 * spacing - steps * half
                    // EN: Ledger lines for pitches outside the five-line staff.
                    // ES: Líneas adicionales para notas fuera del pentagrama.
                    if (steps < 0) {
                        for (step in -2 downTo steps step 2) {
                            val ledgerY = top + 4 * spacing - step * half
                            drawLine(ink, Offset(x - 11.dp.toPx(), ledgerY),
                                Offset(x + 11.dp.toPx(), ledgerY), 0.8.dp.toPx())
                        }
                    } else if (steps > 8) {
                        for (step in 10..steps step 2) {
                            val ledgerY = top + 4 * spacing - step * half
                            drawLine(ink, Offset(x - 11.dp.toPx(), ledgerY),
                                Offset(x + 11.dp.toPx(), ledgerY), 0.8.dp.toPx())
                        }
                    }
                    drawOval(
                        if (active) accidental else ink,
                        topLeft = Offset(x - 6.dp.toPx(), y - 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 8.dp.toPx())
                    )
                    // EN: Basic stems. Rhythm engraving can be extended for beams/rests.
                    // ES: Plicas básicas. El grabado rítmico podrá ampliarse para barras y silencios.
                    drawLine(ink, Offset(x + 6.dp.toPx(), y),
                        Offset(x + 6.dp.toPx(), y - 29.dp.toPx()), 1.2.dp.toPx())
                }
            }
        }
        // EN: Staff label, independent of installed music fonts.
        // ES: Etiqueta del pentagrama, independiente de fuentes musicales.
        Text(
            "G", color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.offset(x = 2.dp, y = 42.dp * zoom)
        )
        // EN: Show sharps/flats where necessary, including pitch alterations.
        // ES: Mostrar sostenidos/bemoles cuando la altura requiere alteración.
        positions.forEach { (fraction, beat) ->
            beat.notes.forEach { note ->
                val pitch = tuning.getOrNull(note.string - 1)?.plus(note.fret + 12) ?: return@forEach
                val semitone = Math.floorMod(pitch, 12)
                if (semitone in setOf(1, 3, 6, 8, 10)) {
                    val octave = Math.floorDiv(pitch, 12) - 1
                    val naturals = intArrayOf(0, 2, 4, 5, 7, 9, 11)
                    val letter = naturals.indexOfLast { it <= semitone }.coerceAtLeast(0)
                    val steps = (octave * 7 + letter) - (4 * 7 + 2)
                    val y = (46f + 48f - steps * 6f) * zoom
                    val x = maxWidth * (0.10f + fraction * 0.80f)
                    Text("#", color = accidental, fontSize = 12.sp,
                        modifier = Modifier.offset(x = x - 19.dp, y = y.dp - 10.dp))
                }
            }
        }
    }
}
