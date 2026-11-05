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
    // EN: Desktop workspace shell. The score renderer below remains unchanged.
    // ES: Contenedor del estudio. El renderizador de partituras permanece intacto.
    val barTicks = remember(score) { MidiScoreCompiler.measureStartTicks(score) }
    val currentBar = remember(position, score) {
        barTicks.indexOfLast { it <= position }.coerceAtLeast(0)
    }
    fun openScore() {
        val dialog = FileDialog(null as Frame?, "Open tablature", FileDialog.LOAD)
        dialog.isVisible = true
        dialog.file?.let { name ->
            val file = File(dialog.directory, name)
            try {
                when (val result = registry.open(file.name, file.readBytes())) {
                    is ImportResult.Success -> {
                        score = result.score
                        selectedTrack = 0
                        loadPlayback(score)
                        message = "Opened ${file.name}"
                    }
                    is ImportResult.Failure -> message = result.message
                }
            } catch (e: Exception) { message = "Could not open file: ${e.message}" }
        }
    }
    val panel = Color(0xFF1C2635)
    val elevated = Color(0xFF263347)
    val subtle = Color(0xFF9CAFC5)
    val selectedColor = Color(0xFF344761)
    val currentTrack = score.tracks[selectedTrack]
    Column(Modifier.fillMaxSize().background(background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // EN: Application toolbar. ES: Barra de herramientas principal.
        Row(Modifier.fillMaxWidth().height(52.dp).background(panel).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("◈", color = accent, fontSize = 23.sp)
            Text("TABLATURE STUDIO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Button(onClick = ::openScore) { Text("Open file") }
            OutlinedButton(onClick = {
                score = DemoScore.create()
                selectedTrack = 0
                loadPlayback(score)
                message = "Demo score loaded"
            }) { Text("Demo") }
            Spacer(Modifier.weight(1f))
            Text("NATIVE  •  OFFLINE", color = subtle, fontSize = 11.sp)
        }
        // EN: Project header and compact transport.
        // ES: Cabecera del proyecto y controles principales de reproducción.
        Column(Modifier.fillMaxWidth().background(panel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(score.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        maxLines = 1)
                    Text(score.artist?.takeIf { it.isNotBlank() } ?: "Untitled artist",
                        color = subtle, fontSize = 12.sp, maxLines = 1)
                }
                Text("${score.tempo} BPM", color = accent, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold)
                Text("BAR ${currentBar + 1}", color = subtle, fontSize = 12.sp)
                Button(enabled = player != null, onClick = {
                    try { player?.play() } catch (e: Exception) { playbackError = e.message }
                }) { Text("▶ Play") }
                OutlinedButton(enabled = player != null, onClick = { player?.pause() }) { Text("Ⅱ Pause") }
                OutlinedButton(enabled = player != null, onClick = { player?.stop() }) { Text("■ Stop") }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("POSITION", color = subtle, fontSize = 10.sp)
                Slider(
                    value = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f,
                    onValueChange = { player?.seekTo((it * length).toLong()) },
                    enabled = player != null && length > 0,
                    modifier = Modifier.weight(1f)
                )
                Text("${position / MidiScoreCompiler.PPQ} / ${length / MidiScoreCompiler.PPQ} beats",
                    color = subtle, fontSize = 11.sp)
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // EN: Scrollable track inspector with real playback controls.
            // ES: Inspector desplazable de pistas con controles de reproducción reales.
            Column(Modifier.width(245.dp).fillMaxHeight().background(panel)
                .verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("INSTRUMENTS  ·  ${score.tracks.size}",
                    color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                score.tracks.forEachIndexed { index, track ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(if (index == selectedTrack) selectedColor else elevated)
                            .clickable { selectedTrack = index }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("${index + 1}. ${track.name}", color = Color.White,
                            fontWeight = if (index == selectedTrack) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp)
                        Text(track.tuning.joinToString(" · ") { midiPitchLabel(it) },
                            color = subtle, fontSize = 10.sp, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(enabled = player != null, onClick = {
                                val next = index !in muted
                                player?.setTrackMuted(index, next)
                                muted = if (next) muted + index else muted - index
                            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)) {
                                Text(if (index in muted) "Unmute" else "Mute", fontSize = 10.sp)
                            }
                            OutlinedButton(enabled = player != null, onClick = {
                                val next = index !in solo
                                player?.setTrackSolo(index, next)
                                solo = if (next) solo + index else solo - index
                            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)) {
                                Text(if (index in solo) "Unsolo" else "Solo", fontSize = 10.sp)
                            }
                        }
                        Slider(value = trackVolumes[index] ?: 95f, onValueChange = { volume ->
                            trackVolumes = trackVolumes + (index to volume)
                            player?.setTrackVolume(index, volume.toInt())
                        }, valueRange = 0f..127f, enabled = player != null,
                            modifier = Modifier.height(24.dp))
                    }
                }
            }
            // EN: Score viewer is invoked exactly as before; no changes to TabCanvas.
            // ES: Se invoca el visor original sin modificar TabCanvas.
            Column(Modifier.weight(1f).fillMaxHeight().background(panel).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(currentTrack.name, color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, maxLines = 1)
                        Text("${currentTrack.tuning.size} strings · ${currentTrack.measures.size} measures",
                            color = subtle, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { zoom = (zoom - .2f).coerceAtLeast(.6f) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(34.dp)) { Text("−") }
                    Text("${(zoom * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    OutlinedButton(onClick = { zoom = (zoom + .2f).coerceAtMost(2.4f) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(34.dp)) { Text("+") }
                }
                Spacer(Modifier.height(8.dp))
                TabCanvas(currentTrack, zoom, currentBar, player?.isPlaying == true,
                    position, barTicks.toList())
            }
        }
        // EN: Secondary transport and practice tools.
        // ES: Herramientas secundarias de transporte y práctica.
        Column(Modifier.fillMaxWidth().background(panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("SPEED", color = subtle, fontSize = 11.sp)
                OutlinedButton(enabled = player != null, onClick = {
                    speed = (speed - .25).coerceAtLeast(.25)
                    player?.setTempoMultiplier(speed)
                }, contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(32.dp)) { Text("−") }
                Text("${(speed * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                OutlinedButton(enabled = player != null, onClick = {
                    speed = (speed + .25).coerceAtMost(2.0)
                    player?.setTempoMultiplier(speed)
                }, contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(32.dp)) { Text("+") }
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
                }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(if (loopEnabled) "Loop off" else "Loop 4 beats", fontSize = 11.sp)
                }
                OutlinedButton(enabled = player != null, onClick = {
                    metronome = !metronome
                    player?.setMetronome(metronome)
                }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(if (metronome) "Metronome on" else "Metronome off", fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(enabled = player != null, onClick = {
                    val dialog = FileDialog(null as Frame?, "Load SoundFont (.sf2)", FileDialog.LOAD)
                    dialog.isVisible = true
                    dialog.file?.let { name ->
                        try {
                            message = if (player?.loadSoundFont(File(dialog.directory, name)) == true)
                                "SoundFont loaded" else "SoundFont unsupported by MIDI synthesizer"
                        } catch (e: Exception) { playbackError = "SoundFont error: ${e.message}" }
                    }
                }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)) { Text("SF2", fontSize = 11.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(enabled = player != null, onClick = {
                    loopStart = position
                    message = "A set at beat ${position / MidiScoreCompiler.PPQ}"
                }, modifier = Modifier.height(32.dp)) { Text("Set A", fontSize = 11.sp) }
                OutlinedButton(enabled = player != null && length > 0, onClick = {
                    if (position > loopStart) {
                        player?.setLoop(loopStart, position)
                        loopEnabled = true
                        message = "A/B loop enabled"
                    } else message = "Move after A before setting B"
                }, modifier = Modifier.height(32.dp)) { Text("Set B / Loop", fontSize = 11.sp) }
                OutlinedButton(enabled = player != null, onClick = {
                    player?.clearLoop()
                    loopEnabled = false
                    message = "Loop cleared"
                }, modifier = Modifier.height(32.dp)) { Text("Clear loop", fontSize = 11.sp) }
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
                }, modifier = Modifier.height(32.dp)) { Text("Export MIDI", fontSize = 11.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(playbackError ?: message, color = if (playbackError != null) Color(0xFFFF9B9B) else subtle,
                fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("Measure ${currentBar + 1} / ${currentTrack.measures.size}",
                color = subtle, fontSize = 11.sp)
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
private fun TabCanvas(
    track: Track,
    zoom: Float,
    currentMeasure: Int,
    playing: Boolean,
    positionTicks: Long,
    measureStartTicks: List<Long>
) {
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
                        Spacer(Modifier.height(12.dp))
                        // EN: Display the actual tuning from the imported track, high to low.
                        // ES: Mostrar la afinación real de la pista importada, de aguda a grave.
                        Text(
                            "TUNING  ·  " + track.tuning.joinToString("  ·  ") { midiPitchLabel(it) },
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(22.dp))
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
                                    // EN: Draw one moving playhead, not permanent lines on every note.
                                    // ES: Dibujar un único cursor móvil, no líneas permanentes en cada nota.
                                    if (active) {
                                        val measureStart = measureStartTicks.getOrElse(absolute) { 0L }
                                        val measureEnd = measureStartTicks.getOrNull(absolute + 1)
                                            ?: (measureStart + MidiScoreCompiler.PPQ * 4L)
                                        val duration = (measureEnd - measureStart).coerceAtLeast(1L)
                                        val fraction = ((positionTicks - measureStart).toFloat() / duration)
                                            .coerceIn(0f, 1f)
                                        Canvas(Modifier.fillMaxSize()) {
                                            // EN: The playhead travels across the same horizontal area as notes.
                                            // ES: El cursor recorre el mismo espacio horizontal que las notas.
                                            val x = (0.10f + fraction * 0.80f) * size.width
                                            val top = 42.dp.toPx() * zoom
                                            val bottom = staffTop.toPx() + staffHeight.toPx() + 12.dp.toPx()
                                            drawLine(
                                                color = selected,
                                                start = Offset(x, top),
                                                end = Offset(x, bottom),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                            drawCircle(
                                                color = selected,
                                                radius = 4.dp.toPx(),
                                                center = Offset(x, top)
                                            )
                                        }
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
                                        // EN: A fret label must not cover the label on the adjacent string.
                                        // Each string is only 15.dp apart, so the old 21.dp square
                                        // background caused overlapping white blocks in chords.
                                        // ES: El fondo del traste no debe tapar el número de otra cuerda.
                                        // Las cuerdas están a 15.dp, y el fondo anterior de 21.dp
                                        // se superponía cuando había acordes.
                                        val fretLabel = note.fret.toString()
                                        val labelWidth = if (fretLabel.length > 1) 21.dp else 14.dp
                                        Box(
                                            modifier = Modifier
                                                .offset(x = x - labelWidth / 2, y = y - 7.dp)
                                                .width(labelWidth)
                                                .height(14.dp)
                                                .background(if (active) Color(0xFFECE6FB) else paper),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = fretLabel,
                                                color = if (active) selected else ink,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false,
                                                lineHeight = 13.sp
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

/**
 * EN: Convert MIDI open-string pitch to a readable tuning label (e.g. E4, B3).
 * ES: Convertir el MIDI de cuerda al nombre de afinación (ej. E4, B3).
 */
private fun midiPitchLabel(midi: Int): String {
    val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    return names[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)
}
