package com.github.p1k0chu.config

import com.google.gson.annotations.SerializedName

data class StatsConfig(
    val name: String = "Stats",
    @SerializedName("id_range") val idRange: String = "A2:A",
    @SerializedName("status_range") val statusRange: String = "D2:D",
    @SerializedName("who_range") val whoRange: String = "C2:C"
)
