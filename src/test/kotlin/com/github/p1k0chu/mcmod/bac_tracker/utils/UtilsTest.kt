package com.github.p1k0chu.mcmod.bac_tracker.utils

import com.google.api.services.sheets.v4.model.ValueRange
import kotlin.test.*

class UtilsTest {
    @Test
    fun testMoveRangeDownBy() {
        assertEquals("A70:B", Utils.moveRangeDownBy("A69:B", 1))
        assertEquals("B3:C", Utils.moveRangeDownBy("B:C", 2))
        assertEquals("A4:Z", Utils.moveRangeDownBy("A1:Z", 3))

        assertEquals(Utils.moveRangeDownBy("bla", 1), null)
    }

    @Test
    fun getIdOrUrlValidUrl() {
        val validUrl = "https://docs.google.com/spreadsheets/d/MY_ID_HERE/edit?flags=useless"
        assertEquals("MY_ID_HERE", Utils.getIdOrUrl(validUrl))
    }

    @Test
    fun getIdOrUrlNotUrl() {
        val input = "some id"
        assertEquals(input, Utils.getIdOrUrl(input))
    }

    @Test
    fun getProfilePictureByUuid() {
        assertIs<String>(Utils.getProfilePictureByUuid("some uuid"))
    }

    @Test
    fun getProfilePictureByUuidNull() {
        assertNull(Utils.getProfilePictureByUuid(null))
    }

    @Test
    fun singleColumnValueRange() {
        val someData: List<List<String>> = listOf(
            listOf("1"),
            listOf("2"),
            listOf("3")
        )

        val valueRange = ValueRange()
        valueRange.setValues(someData)

        assertEquals(listOf("1", "2", "3"), Utils.singleColumnValueRange(valueRange))
    }
}