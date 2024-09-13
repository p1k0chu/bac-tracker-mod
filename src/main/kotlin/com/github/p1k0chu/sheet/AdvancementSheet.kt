package com.github.p1k0chu.sheet

import com.github.p1k0chu.Utils
import com.github.p1k0chu.config.AdvancementConfig
import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.data.AdvancementData
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import org.apache.commons.lang3.ObjectUtils.max

/**
 * Advancements sheet.
 *
 * Push every player's advancement data
 *
 * And then get a list of [ValueRange] based off all data
 *
 * Example:
 * ```
 * val advSheet = AdvancementSheet(settings, advColumnCache)
 * advSheet.pushPlayer("uuid", jsonObject) // repeat for however many players you have
 * val vr = advSheet.getValueRange()
 * ```
 * @param advColumnCache Copy of remote sheet's id column
 */
class AdvancementSheet(
    settings: Settings,
    private val advColumnCache: List<String>
) {
    private val config: AdvancementConfig = settings.advSheet

    private val advancements: List<AdvancementData> = Utils.getAdvData()
    private val advByIdMap: Map<String, AdvancementData> = advancements.associateBy { it.id }

    /**
     * @see AdvancementSheet
     *  @param uuid uuid of player with stats
     *  @param json advancements file parsed as [JsonObject]
     */
    fun pushPlayer(uuid: String, json: JsonObject) {
        json.keySet().forEach {
            if (advByIdMap[it] != null && json[it]?.isJsonObject == true) {
                if (json[it]?.asJsonObject?.get("done")?.asBoolean == true) {
                    advByIdMap[it]?.apply {
                        done = true
                        criteriaProgress = null
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
                            player = uuid
                        }
                    }
                }
            }

        }
    }

    /**
     * @see AdvancementSheet
     */
    fun getValueRange() = listOf(
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