package com.tablaturestudio.importer

import com.tablaturestudio.core.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EN: Offline, XXE-hardened MusicXML partwise reader for basic fretted-instrument scores.
 * ES: Lector MusicXML partwise sin conexión, protegido contra XXE, para instrumentos con trastes.
 * Unsupported score structures fail explicitly rather than silently corrupting notes.
 */
class MusicXmlImporter : ScoreImporter {
    override val extensions = setOf("musicxml", "xml")

    override fun import(bytes: ByteArray): ImportResult = try {
        require(bytes.size <= 20_000_000) { "MusicXML file exceeds 20 MB" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setXIncludeAware(false)
            isExpandEntityReferences = false
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val root = document.documentElement
        require(root.tagName == "score-partwise") { "Only score-partwise MusicXML is supported" }
        val names = mutableMapOf<String, String>()
        root.child("part-list")?.children("score-part")?.forEach { part ->
            names[part.getAttribute("id")] = part.child("part-name")?.textContent?.trim().orEmpty()
        }
        val tracks = root.children("part").map { part ->
            val tuning = mutableMapOf<Int, Int>()
            val measures = mutableListOf<Measure>()
            var divisions = 1
            var signature = TimeSignature()
            part.children("measure").forEachIndexed { index, measure ->
                val beats = mutableListOf<Beat>()
                measure.children().forEach { item ->
                    when (item.tagName) {
                        "attributes" -> {
                            item.child("divisions")?.textContent?.trim()?.toIntOrNull()?.let {
                                require(it > 0) { "Invalid MusicXML divisions" }; divisions = it
                            }
                            item.child("time")?.let { time ->
                                val n = time.child("beats")?.textContent?.trim()?.toIntOrNull() ?: 4
                                val d = time.child("beat-type")?.textContent?.trim()?.toIntOrNull() ?: 4
                                signature = TimeSignature(n, d)
                            }
                            item.children("staff-details").flatMap { it.children("staff-tuning") }.forEach tuningLoop@{ st ->
                                val line = st.getAttribute("line").toIntOrNull() ?: return@tuningLoop
                                val step = st.child("tuning-step")?.textContent?.trim() ?: return@tuningLoop
                                val octave = st.child("tuning-octave")?.textContent?.trim()?.toIntOrNull() ?: return@tuningLoop
                                val alter = st.child("tuning-alter")?.textContent?.trim()?.toIntOrNull() ?: 0
                                val pitch = pitch(step, octave, alter)
                                tuning[line] = pitch
                            }
                        }
                        "backup", "forward" -> error("Polyphonic MusicXML voices are not supported yet")
                        "note" -> {
                            val rawDuration = item.child("duration")?.textContent?.trim()?.toIntOrNull()
                            val duration = rawDuration?.let { Duration(it, divisions * 4) }
                            val isChord = item.child("chord") != null
                            val isRest = item.child("rest") != null
                            val technical = item.child("notations")?.child("technical")
                            val string = technical?.child("string")?.textContent?.trim()?.toIntOrNull()
                            val fret = technical?.child("fret")?.textContent?.trim()?.toIntOrNull()
                            val note = if (!isRest && string != null && fret != null) Note(string, fret) else null
                            if (isChord) {
                                require(beats.isNotEmpty()) { "Chord without preceding note" }
                                val previous = beats.removeAt(beats.lastIndex)
                                beats.add(previous.copy(notes = previous.notes + listOfNotNull(note)))
                            } else {
                                beats.add(Beat(duration, listOfNotNull(note)))
                            }
                        }
                    }
                }
                measures.add(Measure(index + 1, beats, signature))
            }
            require(measures.isNotEmpty()) { "MusicXML part contains no measures" }
            val stringCount = (tuning.keys.maxOrNull() ?: 6).coerceIn(1, 12)
            val defaults = if (stringCount == 4) listOf(43, 38, 33, 28) else listOf(64, 59, 55, 50, 45, 40)
            // EN: MusicXML staff line 1 is the lowest string; our model uses highest first.
            // ES: MusicXML numera desde la cuerda grave; nuestro modelo desde la aguda.
            val pitches = (1..stringCount).map { string ->
                tuning[stringCount + 1 - string] ?: defaults.getOrElse(string - 1) { 40 }
            }
            Track(names[part.getAttribute("id")].orEmpty().ifBlank { "Part" }, pitches, measures)
        }
        require(tracks.isNotEmpty()) { "MusicXML contains no parts" }
        val title = root.child("work")?.child("work-title")?.textContent?.trim()
            ?: root.child("movement-title")?.textContent?.trim() ?: "Imported MusicXML"
        val score = Score(title, tracks = tracks)
        val issues = ScoreValidator.validate(score)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        ImportResult.Success(score)
    } catch (e: Exception) {
        ImportResult.Failure("Cannot import MusicXML: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun pitch(step: String, octave: Int, alter: Int): Int {
        val offset = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)[step.uppercase()]
            ?: error("Invalid tuning step: $step")
        return ((octave + 1) * 12 + offset + alter).also { require(it in 0..127) }
    }

    private fun Element.child(name: String): Element? = children().firstOrNull { it.tagName == name }
    private fun Element.children(name: String): List<Element> = children().filter { it.tagName == name }
    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull {
        (childNodes.item(it) as? Element)
    }
}
