package com.tablaturestudio.importer

import kotlin.test.*

class MusicXmlImporterTest {
    @Test fun `reads fret and string from partwise MusicXML`() {
        val xml = """<?xml version="1.0"?><score-partwise version="4.0"><work><work-title>Example</work-title></work><part-list><score-part id="P1"><part-name>Guitar</part-name></score-part></part-list><part id="P1"><measure number="1"><attributes><divisions>480</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>480</duration><type>quarter</type><notations><technical><string>1</string><fret>0</fret></technical></notations></note></measure></part></score-partwise>"""
        val result = MusicXmlImporter().import(xml.toByteArray())
        assertIs<ImportResult.Success>(result)
        assertEquals("Example", result.score.title)
        assertEquals(0, result.score.tracks.single().measures.single().beats.single().notes.single().fret)
    }

    @Test fun `rejects XML external entities`() {
        val xml = """<!DOCTYPE score-partwise [<!ENTITY secret SYSTEM "file:///etc/passwd">]><score-partwise>&secret;</score-partwise>"""
        assertIs<ImportResult.Failure>(MusicXmlImporter().import(xml.toByteArray()))
    }
}
