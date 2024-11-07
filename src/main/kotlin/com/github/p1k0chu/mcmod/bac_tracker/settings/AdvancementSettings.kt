package com.github.p1k0chu.mcmod.bac_tracker.settings

import com.google.gson.annotations.SerializedName

data class AdvancementSettings(
    val name: String = "Advancements",
    @SerializedName("id_range") val idRange: String = "Q2:Q",
    @SerializedName("status_range") val statusRange: String = "G2:G",
    @SerializedName("who_range") val whoRange: String = "C2:C",
    @SerializedName("when_range") val whenRange: String = "R2:R",
    @SerializedName("progress_range") val progressRange: String = "D2:D",
    @SerializedName("incomplete_criteria_range") val incompleteCriteriaRange: String = "L2:L"
)