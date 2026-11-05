package com.tablaturestudio.importer

import com.tablaturestudio.core.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EN: Imports Guitar Pro 7/8 ZIP-based .gp scores through Content/score.gpif.
 * ES: Importa partituras .gp de Guitar Pro 7/8 mediante Content/score.gpif.
 * EN: Unsupported expressive effects are ignored, not fabricated.
 * ES: Los efectos expresivos no compatibles se omiten, sin inventar información.
 */
class GuitarProGpImporter : ScoreImporter {
    override val extensions = setOf("gp")

    override fun import(bytes: ByteArray): ImportResult = try {
        // EN: GP archives may contain ZIP entries with nonstandard local size headers.
        // ES: Los archivos GP pueden tener cabeceras ZIP locales con tamaños especiales.
        val tempFile = Files.createTempFile("tablature-gp-", ".zip")
        val gpif = try {
            Files.write(tempFile, bytes)
            ZipFile(tempFile.toFile()).use { archive ->
                val entry = archive.getEntry("Content/score.gpif")
                    ?: error("Missing Content/score.gpif in .gp archive")
                archive.getInputStream(entry).use { it.readBytes() }
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(gpif)).documentElement
        val tracks = root.child("Tracks")?.children("Track").orEmpty()
        val masterBars = root.child("MasterBars")?.children("MasterBar").orEmpty()
        val bars = root.child("Bars").indexed("Bar")
        val voices = root.child("Voices").indexed("Voice")
        val beats = root.child("Beats").indexed("Beat")
        val notes = root.child("Notes").indexed("Note")
        val rhythms = root.child("Rhythms").indexed("Rhythm")
        if (tracks.isEmpty() || masterBars.isEmpty()) error("No tracks or measures in GPIF")

        val scoreTracks = tracks.mapIndexed { trackIndex, track ->
            val rawTuning = track.child("Staves")?.child("Staff")?.child("Properties")
                ?.children("Property")?.firstOrNull { it.getAttribute("name") == "Tuning" }
                ?.child("Pitches")?.textContent?.trim()?.split(Regex("\\s+"))
                ?.mapNotNull(String::toIntOrNull).orEmpty()
            // EN: GPIF stores tuning from low to high; our model uses high to low.
            // ES: GPIF guarda la afinación de grave a aguda; el modelo usa el orden inverso.
            val tuning = rawTuning.reversed().ifEmpty { listOf(64, 59, 55, 50, 45, 40) }
            val program = track.child("Sounds")?.child("Sound")?.child("MIDI")
                ?.child("Program")?.textContent?.trim()?.toIntOrNull()?.coerceIn(0, 127) ?: 24
            val measures = masterBars.mapIndexed { index, master ->
                val signatureParts = master.child("Time")?.textContent?.trim()?.split('/')
                val signature = if (signatureParts?.size == 2) {
                    val n = signatureParts[0].toIntOrNull() ?: 4
                    val d = signatureParts[1].toIntOrNull() ?: 4
                    if (n > 0 && d > 0 && d and (d - 1) == 0) TimeSignature(n, d) else TimeSignature()
                } else TimeSignature()
                val barId = master.child("Bars")?.ids()?.getOrNull(trackIndex)
                val bar = barId?.let(bars::get)
                val result = mutableListOf<Beat>()
                bar?.child("Voices")?.ids()?.filter { it >= 0 }?.forEach { voiceId ->
                    val voice = voices[voiceId] ?: return@forEach
                    var onset = 0.0
                    voice.child("Beats")?.ids()?.forEach beatLoop@ { beatId ->
                        val gpBeat = beats[beatId] ?: return@beatLoop
                        val rhythmId = gpBeat.child("Rhythm")?.getAttribute("ref")?.toIntOrNull()
                        val rhythm = rhythmId?.let(rhythms::get)
                        val denominator = when (rhythm?.child("NoteValue")?.textContent?.trim()) {
                            "Whole" -> 1; "Half" -> 2; "Quarter" -> 4; "Eighth" -> 8
                            "16th" -> 16; "32nd" -> 32; "64th" -> 64; "128th" -> 128
                            else -> 4
                        }
                        val dots = rhythm?.child("AugmentationDot")?.getAttribute("count")?.toIntOrNull()
                            ?: rhythm?.children("AugmentationDot")?.size ?: 0
                        val multiplier = if (dots >= 2) 7 else if (dots == 1) 3 else 1
                        val divisor = if (dots >= 2) 4 else if (dots == 1) 2 else 1
                        val tuplet = rhythm?.child("PrimaryTuplet")
                        val num = tuplet?.getAttribute("num")?.toIntOrNull() ?: 1
                        val den = tuplet?.getAttribute("den")?.toIntOrNull() ?: 1
                        val duration = Duration(multiplier * den, denominator * divisor * num)
                        val beatNotes = gpBeat.child("Notes")?.ids()?.mapNotNull { noteId ->
                            val gpNote = notes[noteId] ?: return@mapNotNull null
                            val properties = gpNote.child("Properties")?.children("Property").orEmpty()
                            fun prop(name: String) = properties.firstOrNull { it.getAttribute("name") == name }
                            val stringValue = prop("String")?.child("String")?.textContent?.trim()?.toIntOrNull()
                            val fret = prop("Fret")?.child("Fret")?.textContent?.trim()?.toIntOrNull()
                            // EN: GPIF strings are zero-based from the lowest string.
                            // ES: GPIF numera las cuerdas desde cero, empezando por la más grave.
                            val stringIndex = stringValue?.let { tuning.size - it }
                            if (stringIndex == null || stringIndex !in 1..tuning.size || fret == null || fret < 0) null
                            else Note(stringIndex, fret, tied = gpNote.child("Tie") != null,
                                dead = gpNote.child("Properties")?.children("Property")
                                    ?.any { it.getAttribute("name") == "Dead" } == true)
                        }.orEmpty()
                        result += Beat(duration, beatNotes, onset)
                        onset += duration.asWholeNotes()
                    }
                }
                Measure(index + 1, result.sortedBy { it.onset }, signature)
            }
            Track(track.child("Name")?.textContent?.trim().orEmpty().ifBlank { "Track ${trackIndex + 1}" },
                tuning, measures, program)
        }
        val tempoEvents = root.child("MasterTrack")?.child("Automations")?.children("Automation")
            ?.filter { it.child("Type")?.textContent?.trim() == "Tempo" }.orEmpty()
        val tempoChanges = tempoEvents.mapNotNull { automation ->
            val bpm = automation.child("Value")?.textContent?.trim()?.split(Regex("\\s+"))
                ?.firstOrNull()?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            val bar = automation.child("Bar")?.textContent?.trim()?.toIntOrNull() ?: 0
            val onset = automation.child("Position")?.textContent?.trim()?.toDoubleOrNull() ?: 0.0
            if (bpm in 1..500) TempoChange(bar, onset, bpm) else null
        }
        val title = root.child("Score")?.child("Title")?.textContent?.trim().orEmpty().ifBlank { "Imported Guitar Pro" }
        val artist = root.child("Score")?.child("Artist")?.textContent?.trim()?.ifBlank { null }
        ImportResult.Success(Score(title, artist, scoreTracks,
            tempoChanges.firstOrNull()?.bpm ?: 120, tempoChanges))
    } catch (e: Exception) {
        ImportResult.Failure("Unable to read Guitar Pro .gp file: ${e.message}")
    }
}

private fun Element.child(tag: String): Element? = children(tag).firstOrNull()
private fun Element.children(tag: String): List<Element> = buildList {
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tag) add(node as Element)
    }
}
private fun Element?.indexed(tag: String): Map<Int, Element> = this?.children(tag)
    ?.mapNotNull { element -> element.getAttribute("id").toIntOrNull()?.let { it to element } }
    ?.toMap().orEmpty()
private fun Element.ids(): List<Int> = textContent.trim().split(Regex("\\s+"))
    .mapNotNull(String::toIntOrNull)
