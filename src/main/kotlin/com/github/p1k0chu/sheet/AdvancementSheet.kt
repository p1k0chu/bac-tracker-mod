package com.github.p1k0chu.sheet

import com.github.p1k0chu.Utils
import com.github.p1k0chu.config.AdvancementConfig
import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.data.AdvancementData
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.apache.commons.lang3.ObjectUtils.max
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.reader

class AdvancementSheet(
    service: Sheets,
    settings: Settings
) : Sheet {
    private val sheetId: String = settings.sheetId
    private val config: AdvancementConfig = settings.advSheet

    private val advColumnCache: List<String> by lazy {
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

    /** Reads advancement files and returns the cells to update
     *  @param server [MinecraftServer] object, needed to find world save folder
     *  @return cells to update on the sheet, for batch update
     */
    override fun update(server: MinecraftServer): List<ValueRange> {

        val advancements: List<AdvancementData> = Utils.getAdvData()
        val advByIdMap: Map<String, AdvancementData> = advancements.associateBy { it.id }


        server.getSavePath(WorldSavePath.ADVANCEMENTS).listDirectoryEntries("*.json").forEach { path ->
            val json: JsonObject = path.reader().use { reader ->
                Utils.GSON.fromJson(reader, JsonElement::class.java).asJsonObject
            }


            json.keySet().forEach {
                if (advByIdMap[it] != null && json[it]?.isJsonObject == true) {
                    if (json[it]?.asJsonObject?.get("done")?.asBoolean == true) {
                        advByIdMap[it]?.apply {
                            done = true
                            //criteriaProgress = AdvancementData.allCriteria[it].asJsonArray.size()
                            criteriaProgress = null // optimization, null are not updated on the sheet
                            missing = null
                        }


                        json[it]?.asJsonObject?.get("criteria")?.asJsonObject?.asMap()
                            ?.forEach { (_, t: JsonElement) ->
                                advByIdMap[it]?.completionTime = max(
                                    Instant.parse(t.asString, AdvancementData.customFormat),
                                    advByIdMap[it]?.completionTime ?: Instant.DISTANT_PAST
                                )
                            }
                    } else {
                        var count = 0
                        val missingCriteria = mutableSetOf<String>()

                        AdvancementData.allCriteria[it].asJsonArray.forEach { i ->
                            if (i.isJsonArray) {
                                if (i.asJsonArray.any { j ->
                                        json[it]?.asJsonObject?.get("criteria")?.asJsonObject?.has(j.asString) == true
                                    }) {
                                    count += 1
                                } else {
                                    missingCriteria.add("[" + i.asJsonArray.toList().joinToString(", ") + "]")
                                }
                            } else {
                                if (json[it]?.asJsonObject?.get("criteria")?.asJsonObject?.has(i.asString) == true) {
                                    count += 1
                                } else {
                                    missingCriteria.add(i.asString)
                                }
                            }
                        }


                        if (count > (advByIdMap[it]?.criteriaProgress ?: 0)) {
                            advByIdMap[it]?.run {
                                criteriaProgress = count
                                missing = missingCriteria
                                player = path.nameWithoutExtension
                            }

                        }
                    }
                }
            }

        }

        return listOf(
            ValueRange()
                .setRange("${config.name}!${config.statusRange}")
                .setValues(advColumnCache.map { listOf(advByIdMap[it]?.done) }),
            ValueRange()
                .setRange("${config.name}!${config.progressRange}")
                .setValues(advColumnCache.map {
                    listOf(advByIdMap[it]?.run {
                        "${criteriaProgress ?: return@run null}/${AdvancementData.allCriteria[id]?.asJsonArray?.size() ?: Int.MAX_VALUE}"
                    })
                }),
            ValueRange()
                .setRange("${config.name}!${config.incompleteRange}")
                .setValues(advColumnCache.map {
                    listOf(advByIdMap[it]?.run { missing?.joinToString(", ") })
                }),
            ValueRange()
                .setRange("${config.name}!${config.whoRange}")
                .setValues(advColumnCache.map {
                    listOf(advByIdMap[it]?.player?.let { playerUUID ->
                        "=IMAGE(\"https://crafatar.com/avatars/${playerUUID}?size=16&overlay\")"
                    })
                }),
            ValueRange()
                .setRange("${config.name}!${config.whenRange}")
                .setValues(advColumnCache.map {
                    listOf(advByIdMap[it]?.completionTime?.format(AdvancementData.prettyFormat))
                })
        )
    }
}