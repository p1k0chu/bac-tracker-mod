package com.github.p1k0chu.mcmod.bac_tracker.data

import com.github.p1k0chu.mcmod.bac_tracker.utils.ComparingType
import java.security.InvalidParameterException

class ScoreboardData(
    type: String,
    var value: Int = 0,
    var player: String?,
    val index: Int
) {
    val type: ComparingType

    init {
        when (type) {
            "max" -> this.type = ComparingType.MAX
            "sum" -> this.type = ComparingType.SUM
            else -> throw InvalidParameterException("value must be either \"sum\" or \"max\"")
        }
    }
}
