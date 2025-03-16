package com.github.p1k0chu.mcmod.bac_tracker.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun testMoveRangeDownBy() {
        assertEquals("A70:B", Utils.moveRangeDownBy("A69:B", 1))
        assertEquals("B3:C", Utils.moveRangeDownBy("B:C", 2))
        assertEquals("A4:Z", Utils.moveRangeDownBy("A1:Z", 3))

        assertEquals(Utils.moveRangeDownBy("bla", 1), null)
    }
}