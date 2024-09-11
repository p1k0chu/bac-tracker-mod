package com.github.p1k0chu.data

import org.apache.commons.csv.CSVRecord

/**
 * @param category E.g. minecraft:custom
 * @param name E.g. minecraft:death
 * @param type `max` or `sum`
 */
data class StatData(
    val category: String,
    val name: String,
    val type: String
) {
    constructor(record: CSVRecord): this(
        category = record[0],
        name = record[1],
        type = record[2]
    )

    /** id as it is in the sheet */
    val id: String = "${category.split(":", limit = 2)[1]}.${name.split(":", limit = 2)[1]}"
    var value: Int = 0
    var player: String? = null
}
