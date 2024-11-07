package com.github.p1k0chu.mcmod.bac_tracker.settings

import com.google.gson.annotations.SerializedName

data class StatsSettings (
    val name: String = "Stats",
    @SerializedName("id_range") val idRange: String = "A3:A",
    @SerializedName("value_range") val valueRange: String = "F3:F",
    @SerializedName("who_range") val whoRange: String = "E3:E",
    @SerializedName("type_range") val typeRange: String = "B3:B",
    @SerializedName("comparing_type_range") val comparingTypeRange: String = "C3:C"
)
