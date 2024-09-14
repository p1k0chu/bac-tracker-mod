package com.github.p1k0chu.sheet

import com.github.p1k0chu.Utils
import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.config.StatsConfig
import com.github.p1k0chu.data.ScoreboardData
import com.github.p1k0chu.data.StatData
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer

/**
 * Items sheet.
 *
 * Push every player's stat data
 *
 * Update scoreboard using [MinecraftServer] object
 *
 * And then get a list of [ValueRange] based off all stats
 *
 * Example:
 * ```
 * val statSheet = StatSheet(settings, statColumnCache)
 * statSheet.pushPlayer("uuid", jsonObject) // repeat for however many players you have
 * statSheet.updateScoreboard(server)
 * val vr = statSheet.getValueRange()
 * ```
 * @param statColumnCache Copy of remote sheet's id column, as-is
 */
class StatSheet(
    settings: Settings,
    private val statColumnCache: List<String>
) {
    private val config: StatsConfig = settings.statSheet

    private val stats: Map<String, StatData> = Utils.getStatsData().associateBy { it.id }
    private val scoreboards: Map<String, ScoreboardData> = Utils.getScoreboardsData().associateBy { it.name }


    /** Takes stats for one player
     *  @param uuid uuid of player with stats
     *  @param json stats file parsed as [JsonObject]
     */
    fun pushPlayer(uuid: String, json: JsonObject) {
        stats.forEach { (_, statData) ->
            json["stats"].asJsonObject
                ?.get(statData.category)?.asJsonObject
                ?.get(statData.name)?.asInt
                ?.let { x ->
                    when (statData.type) {
                        "sum" -> statData.value += x
                        "max" -> if (x > statData.value) {
                            statData.value = x
                            statData.player = uuid
                        }
                    }
                }
        }
    }

    fun updateScoreboard(server: MinecraftServer) {
        server.scoreboard.objectives.forEach { scoreboardObjective ->
            if (scoreboards.containsKey(scoreboardObjective.name)) {
                server.scoreboard.getScoreboardEntries(scoreboardObjective).forEach { entry ->
                    when (scoreboards[scoreboardObjective.name]!!.type) {
                        "sum" -> scoreboards[scoreboardObjective.name]!!.value += entry.value
                        "max" -> if (entry.value > scoreboards[scoreboardObjective.name]!!.value) {
                            scoreboards[scoreboardObjective.name]?.value = entry.value
                            scoreboards[scoreboardObjective.name]?.player = entry.owner
                        }
                    }
                }
            }
        }
    }

    fun getValueRange() = listOf(
        ValueRange()
            .setRange("${config.name}!${config.statusRange}")
            .setValues(statColumnCache.map {
                listOf(
                    stats[it]?.value ?: scoreboards[it]?.value
                )
            }),
        ValueRange()
            .setRange("${config.name}!${config.whoRange}")
            .setValues(statColumnCache.map {
                listOf(
                    (stats[it]?.player ?: scoreboards[it]?.player)?.let { playerUUID ->
                        "=IMAGE(\"https://crafatar.com/avatars/${playerUUID}?size=16&overlay\")"
                    }
                )
            }),
    )
}