package com.github.p1k0chu.sheet

import com.github.p1k0chu.Utils
import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.config.StatsConfig
import com.github.p1k0chu.data.ScoreboardData
import com.github.p1k0chu.data.StatData
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.reader

class StatSheet(
    service: Sheets,
    settings: Settings
) : Sheet {
    private val sheetId: String = settings.sheetId
    private val config: StatsConfig = settings.statSheet

    /** Column with stat ids AND scoreboards */
    private val statColumnCache: List<String> by lazy {
        service.spreadsheets().values()
            .get(sheetId, "${config.name}!${config.idRange}")
            .execute()
            .getValues()
            .filter {
                it.isNotEmpty()
            }
            .map {
                it.first().toString()
            }
    }

    /** Reads stats and scoreboard files and returns the cells to update
     *  @param server [MinecraftServer] object, needed to find world save folder
     *  @return cells to update on the sheet, for batch update
     */
    override fun update(server: MinecraftServer): List<ValueRange> {
        val stats: Map<String, StatData> = Utils.getStatsData().associateBy { it.id }

        server.getSavePath(WorldSavePath.STATS).listDirectoryEntries("*.json").forEach { path: Path ->
            val json: JsonObject = path.reader().use { reader ->
                Utils.GSON.fromJson(reader, JsonObject::class.java)["stats"].asJsonObject
            }
            stats.forEach { (_, statData) ->
                json[statData.category]
                    ?.asJsonObject
                    ?.get(statData.name)
                    ?.asInt
                    ?.let { x ->
                        when (statData.type) {
                            "sum" -> statData.value += x
                            "max" -> if (x > statData.value) {
                                statData.value = x
                                statData.player = path.nameWithoutExtension
                            }
                        }
                    }
            }
        }

        val scoreboards: Map<String, ScoreboardData> = Utils.getScoreboardsData().associateBy { it.name }

        server.scoreboard.objectives.forEach { scoreboardObjective ->
            if(scoreboards.containsKey(scoreboardObjective.name)) {
                server.scoreboard.getScoreboardEntries(scoreboardObjective).forEach { entry ->
                    when(scoreboards[scoreboardObjective.name]!!.type) {
                        "sum" -> scoreboards[scoreboardObjective.name]!!.value += entry.value
                        "max" -> if(entry.value > scoreboards[scoreboardObjective.name]!!.value) {
                            scoreboards[scoreboardObjective.name]?.value = entry.value
                            scoreboards[scoreboardObjective.name]?.player = entry.owner
                        }
                    }
                }
            }
        }


        return listOf(
            ValueRange()
                .setRange("${config.name}!${config.statusRange}")
                .setValues(statColumnCache.map { listOf(
                    stats[it]?.value ?: scoreboards[it]?.value
                ) }),
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
}