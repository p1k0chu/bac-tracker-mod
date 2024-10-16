package com.github.p1k0chu.mcmod.bac_tracker.settings

import com.google.gson.annotations.SerializedName

/**
 * Settings for every world, to reload one must restart the game,
 * @param refreshDelay in seconds
 */
data class GlobalSettings(
    @SerializedName("refresh-delay-seconds") val refreshDelay: Long = 10L
)
