package com.github.p1k0chu.data

import org.apache.commons.csv.CSVRecord

/**
 * @param type Type: either 'sum' or 'max'
 */
data class ScoreboardData(
    val name: String,
    val type: String
){
    constructor(record: CSVRecord) : this(
        name = record[0],
        type = record[1]
    )

    var value: Int =  0
    var player: String? = null
}
