package com.tablaturestudio.core

import kotlin.test.*

class ScoreTest {
    @Test fun demoScoreIsValid() {
        assertTrue(ScoreValidator.validate(DemoScore.create()).isEmpty())
    }
    @Test fun durationsAreRational() {
        assertEquals(.25, Duration(1, 4).asWholeNotes())
    }
    @Test fun invalidFretIsRejected() {
        assertFailsWith<IllegalArgumentException> { Note(1, -1) }
    }
}
