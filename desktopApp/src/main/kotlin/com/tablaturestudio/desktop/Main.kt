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

private val background = Color(0xFF121824)
private val surface = Color(0xFF202B3B)
private val accent = Color(0xFFE0B676)

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Tablature Studio") {
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
    var message by remember { mutableStateOf("Demo score loaded · Playback available in Sprint 5") }
    val registry = remember { ImportRegistry() }
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
                            is ImportResult.Success -> { score = result.score; selectedTrack = 0; message = "Opened ${file.name}" }
                            is ImportResult.Failure -> message = result.message
                        }
                    } catch (e: Exception) { message = "Could not open file: ${e.message}" }
                }
            }) { Text("Open file") }
            OutlinedButton(onClick = { score = DemoScore.create(); selectedTrack = 0; message = "Demo score loaded" }) { Text("Demo") }
            Spacer(Modifier.weight(1f))
            Text("Offline · Native Kotlin", color = Color.LightGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(score.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(score.artist.orEmpty(), color = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(190.dp).fillMaxHeight().background(surface).padding(12.dp)) {
                Text("TRACKS", color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                score.tracks.forEachIndexed { index, track ->
                    TextButton(onClick = { selectedTrack = index }, modifier = Modifier.fillMaxWidth()) {
                        Text(track.name, color = if (index == selectedTrack) accent else Color.White)
                    }
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
                TabCanvas(score.tracks[selectedTrack], zoom)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().background(surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◀     ▶     ■", color = Color.Gray, fontSize = 18.sp)
            Spacer(Modifier.width(20.dp))
            Text(message, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

/** EN: Native Canvas renderer driven by layout data. ES: Dibujo nativo desde geometría calculada. */
@Composable
private fun TabCanvas(track: Track, zoom: Float) {
    val layout = remember(track) { TabLayoutEngine.layout(track) }
    val strings = listOf("e", "B", "G", "D", "A", "E")
    val rowHeight = ((layout.height + 60f) * zoom).dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        // EN: One continuous system; advanced pagination comes later.
        // ES: Un sistema continuo; la paginación avanzada se implementará después.
        Box(Modifier.width((layout.width * zoom + 65).dp).height(rowHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = zoom * density
                val originX = 40f * density
                val originY = 24f * density
                for (s in 0 until layout.stringCount) {
                    val y = originY + s * TabLayoutEngine.STRING_SPACING * scale
                    drawLine(Color(0xFF728097), Offset(originX, y), Offset(originX + layout.width * scale, y), 1.dp.toPx())
                }
                layout.measures.forEach { m ->
                    val x = originX + m.x * scale
                    drawLine(Color(0xFFA3AFC1), Offset(x, originY), Offset(x, originY + layout.height * scale), 1.dp.toPx())
                }
                val endX = originX + layout.width * scale
                drawLine(Color(0xFFA3AFC1), Offset(endX, originY), Offset(endX, originY + layout.height * scale), 1.dp.toPx())
            }
            // EN: Text overlays share the same logical coordinate space as Canvas.
            // ES: Los textos usan las mismas coordenadas lógicas que Canvas.
            layout.measures.forEach { measure ->
                Text("${measure.number}", modifier = Modifier.offset(x = (40 + measure.x * zoom).dp, y = 0.dp), color = accent, fontSize = 11.sp)
                measure.notes.forEach { note ->
                    Box(Modifier.offset(x = (40 + note.x * zoom - 9).dp, y = (24 + note.y * zoom - 10).dp)
                        .size(20.dp).background(surface), contentAlignment = Alignment.Center) {
                        Text(note.fret.toString(), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
