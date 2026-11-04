package com.tablaturestudio.renderer

import com.tablaturestudio.core.DemoScore
import kotlin.test.*

class TabLayoutTest {
    @Test fun producesDeterministicGeometry() {
        val layout = TabLayoutEngine.layout(DemoScore.create().tracks.first())
        assertEquals(8 * TabLayoutEngine.MEASURE_WIDTH, layout.width)
        assertEquals(6, layout.stringCount)
        assertEquals(8, layout.measures.size)
    }
}
