package com.github.p1k0chu.config

import com.google.gson.annotations.SerializedName

data class ItemsConfig(
        val name: String = "Items/Blocks",
        @SerializedName("id_range") val idRange: String = "G2:G",
        @SerializedName("status_range") val statusRange: String = "C2:C",
        @SerializedName("who_range") val whoRange: String = "A2:A"
)
