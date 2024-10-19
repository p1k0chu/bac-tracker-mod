package com.github.p1k0chu.mcmod.bac_tracker.utils

import java.util.regex.Pattern

object Utils {
    val rangeRegex = Pattern.compile("(?<sL>\\D+)(?<sN>\\d*):(?<eL>\\D+)", Pattern.CASE_INSENSITIVE)

    /**
     * @param cell  cell range like "A1:A" (regex for cell must be \D+\d*:\D+)
     * @param index index, like 2
     * @return cell range, like A3:A, or null if cell doesn't match regex
     */
    fun moveRangeDownBy(cell: String, index: Int): String? {
        val m = rangeRegex.matcher(cell)
        if (!m.find()) {
            return null
        }

        val startLetter: String = m.group("sL") ?: return null
        val endLetter: String = m.group("eL") ?: return startLetter
        var startNumber: Int = m.group("sN").toIntOrNull() ?: 1

        return "$startLetter${startNumber + index}:$endLetter"
    }

    /**
     * @param player uuid of the player
     * @return url of profile picture as excel =IMAGE(stuff)
     */
    fun getProfilePictureByUuid(player: String?): String? {
        if (player == null) return null

        return "=IMAGE(\"https://crafatar.com/avatars/$player?size=16&overlay\")"
    }
}