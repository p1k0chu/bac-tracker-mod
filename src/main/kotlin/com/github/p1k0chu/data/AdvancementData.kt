package com.github.p1k0chu.data

import com.github.p1k0chu.Utils
import com.google.gson.JsonObject
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import org.apache.commons.csv.CSVRecord

/**
 * @param id Internal id of advancement
 */
data class AdvancementData(
    val id: String,
    val index: Int
) {
    constructor(record: CSVRecord) : this(
        id = record[0],
        index = record.recordNumber.toInt()
    )

    var done: Boolean = false
    var player: String? = null
    var completionTime: Instant? = null
    var criteriaProgress: Int? = null
    var missing: Set<String>? = null

    companion object {
        val allCriteria: JsonObject by lazy {
            this::class.java.getResourceAsStream("/tracker_data/adv_criteria_requirements.json")?.reader()
                .use { reader ->
                    Utils.GSON.fromJson(reader, JsonObject::class.java)
                }
        }
        val customFormat = DateTimeComponents.Format {
            date(LocalDate.Formats.ISO)
            char(' ')
            time(LocalTime.Formats.ISO)
            char(' ')
            offset(UtcOffset.Formats.FOUR_DIGITS)
        }
        val prettyFormat = DateTimeComponents.Format  {
            date(LocalDate.Formats.ISO)
            char(' ')
            time(LocalTime.Formats.ISO)
            chars(" UTC")
        }
    }
}
