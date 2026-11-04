package com.tablaturestudio.importer

import kotlin.test.*

class AsciiTabTest {
    @Test fun parsesSixStringsWithoutInventingRhythm() {
        val tab = """e|--0--3--|
B|--------|
G|--------|
D|--------|
A|--------|
E|--------|"""
        val result = AsciiTabImporter().import(tab.toByteArray())
        assertIs<ImportResult.Success>(result)
        assertEquals(2, result.score.tracks[0].measures[0].beats.size)
        assertNull(result.score.tracks[0].measures[0].beats[0].duration)
    }
}
