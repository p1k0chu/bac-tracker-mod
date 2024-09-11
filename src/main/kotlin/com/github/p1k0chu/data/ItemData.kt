package com.github.p1k0chu.data

import org.apache.commons.csv.CSVRecord

/**
 * @param id Minecraft id of the item
 * @param adv Advancement that requires this item
 */
data class ItemData(
    val id: String,
    val adv: String
){
    constructor(record: CSVRecord) : this(
        id = record[0],
        adv = record[1]
    )

    var done: Boolean = false
}
