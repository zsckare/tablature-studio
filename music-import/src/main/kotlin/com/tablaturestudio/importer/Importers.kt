package com.tablaturestudio.importer

import com.tablaturestudio.core.*

/** EN: Import result with explicit failures. ES: Resultado de importación con errores explícitos. */
sealed interface ImportResult {
    data class Success(val score: Score) : ImportResult
    data class Failure(val message: String) : ImportResult
}

interface ScoreImporter {
    val extensions: Set<String>
    fun import(bytes: ByteArray): ImportResult
}

/** EN: Reads plain ASCII tab with six or four labeled string rows.
 * ES: Lee tablaturas ASCII con seis o cuatro líneas de cuerdas etiquetadas.
 * ASCII tabs do not reliably encode rhythm: duration stays null.
 */
class AsciiTabImporter : ScoreImporter {
    override val extensions = setOf("txt", "tab")
    override fun import(bytes: ByteArray): ImportResult {
        val text = bytes.toString(Charsets.UTF_8)
        val rows = text.lines().mapNotNull { line ->
            val match = Regex("^\\s*([A-Ga-g][#b]?)\\s*\\|(.*)$").find(line)
            match?.let { it.groupValues[1] to it.groupValues[2] }
        }
        if (rows.size != 6 && rows.size != 4) return ImportResult.Failure("Expected 4 or 6 labeled string rows (e|--0--)")
        val segments = rows.map { it.second.split('|') }
        val measureCount = segments.minOf { it.size }
        if (measureCount == 0) return ImportResult.Failure("No tablature measures found")
        val measures = (0 until measureCount).map { index ->
            val lines = segments.map { it[index] }
            val notes = mutableListOf<Pair<Int, Note>>()
            lines.forEachIndexed { stringIndex, row ->
                Regex("\\d+").findAll(row).forEach { match ->
                    notes += match.range.first to Note(stringIndex + 1, match.value.toInt())
                }
            }
            val beats = notes.groupBy { it.first }.toSortedMap().values.map { group ->
                Beat(null, group.map { it.second })
            }
            Measure(index + 1, beats)
        }
        val tuning = if (rows.size == 6) listOf(64, 59, 55, 50, 45, 40) else listOf(43, 38, 33, 28)
        return ImportResult.Success(Score("Imported ASCII Tab", tracks = listOf(Track("Tab", tuning, measures))))
    }
}

class ImportRegistry(private val importers: List<ScoreImporter> = listOf(AsciiTabImporter(), MusicXmlImporter(), GuitarProImporter())) {
    fun open(filename: String, bytes: ByteArray): ImportResult {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return importers.firstOrNull { extension in it.extensions }?.import(bytes)
            ?: ImportResult.Failure("Unsupported format: .$extension")
    }
}

