package com.github.p1k0chu.mcmod.bac_tracker.utils

import com.google.api.client.json.JsonString
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.time.Instant
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

    @Test
    fun findLatestCriteriaObtainedDate() {
        val someOldDate = "2000-01-01 00:00:00 +0000"
        val someNewDate = "2025-01-01 00:00:00 +0000"

        val criteria = JsonObject()
        criteria.add("old", JsonPrimitive(someOldDate))
        criteria.add("old2", JsonPrimitive(someOldDate))
        criteria.add("old3", JsonPrimitive(someOldDate))
        criteria.add("old4", JsonPrimitive(someOldDate))

        criteria.add("new", JsonPrimitive(someNewDate))

        val jsonObject = JsonObject()
        jsonObject.add("criteria", criteria)

        assertEquals(
            Instant.from(Utils.minecraftTimeFormatter.parse(someNewDate)),
            Utils.findLatestCriteriaObtainedDate(jsonObject)
        )
    }
}