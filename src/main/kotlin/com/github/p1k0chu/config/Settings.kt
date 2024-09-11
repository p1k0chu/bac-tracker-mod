package com.github.p1k0chu.config

import com.google.gson.annotations.SerializedName

data class Settings(
    @SerializedName("spreadsheet-id") val sheetId: String = "",
    @SerializedName("timer-cell") val timerCell: String = "Home!W2",
    @SerializedName("stats-enabled") val statEnabled: Boolean = true,
    @SerializedName("refresh-delay-ticks") val refreshTicks: Int = 6000,
    @SerializedName("ADVANCEMENTS_SHEET") val advSheet: AdvancementConfig = AdvancementConfig(),
    @SerializedName("ITEMS_SHEET") val itemSheet: ItemsConfig = ItemsConfig(),
    @SerializedName("STATS_SHEET") val statSheet: StatsConfig = StatsConfig()
)