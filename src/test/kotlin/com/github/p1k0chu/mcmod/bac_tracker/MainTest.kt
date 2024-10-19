package com.github.p1k0chu.mcmod.bac_tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun testMoveRangeDownBy() {
        assertEquals("A70:B", Main.moveRangeDownBy("A69:B", 1))
        assertEquals("B3:C", Main.moveRangeDownBy("B:C", 2))
        assertEquals("A4:Z", Main.moveRangeDownBy("A1:Z", 3))

        assertEquals(Main.moveRangeDownBy("bla", 1), null)
    }
}