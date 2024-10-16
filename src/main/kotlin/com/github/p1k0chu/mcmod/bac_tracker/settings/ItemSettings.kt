package com.github.p1k0chu.mcmod.bac_tracker.settings

import com.google.gson.annotations.SerializedName

data class ItemSettings (
    val name: String = "Items/Blocks",
    @SerializedName("id_range") val idRange: String = "F2:F",
    @SerializedName("status_range") val statusRange: String = "B2:B",
    @SerializedName("advancement_range") val advRange: String = "G2:G"
)