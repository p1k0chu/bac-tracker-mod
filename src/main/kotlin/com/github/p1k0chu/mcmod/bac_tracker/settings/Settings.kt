package com.github.p1k0chu.mcmod.bac_tracker.settings

import com.google.gson.annotations.SerializedName

data class Settings (
    @SerializedName("spreadsheet-id") val sheetId: String = "",
    @SerializedName("update-delay-seconds") val updateDelaySeconds: Long = 10,
    @SerializedName("stats-enabled") val statEnabled: Boolean = true,
    @SerializedName("scoreboard-enabled") val scoreboardEnabled: Boolean = true,
    @SerializedName("ADVANCEMENTS_SHEET") val advSheet: AdvancementSettings = AdvancementSettings(),
    @SerializedName("ITEMS_SHEET") val itemSheet: ItemSettings = ItemSettings(),
    @SerializedName("STATS_SHEET") val statSheet: StatsSettings = StatsSettings()
)
