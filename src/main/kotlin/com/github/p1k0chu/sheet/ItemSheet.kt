package com.github.p1k0chu.sheet

import com.github.p1k0chu.Utils
import com.github.p1k0chu.config.ItemsConfig
import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.data.ItemData
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonObject

/**
 * Items sheet.
 *
 * Push every player's stat data
 *
 * And then get a list of [ValueRange] based off all stats
 *
 * Example:
 * ```
 * val itemSheet = ItemSheet(settings, itemIdCache)
 * itemSheet.pushPlayer("uuid", jsonObject) // repeat for however many players you have
 * val vr = itemSheet.getValueRange()
 * ```
 * @param itemIdCache Copy of remote sheet's id column, as-is
 */
class ItemSheet(
    settings: Settings,
    private val itemIdCache: List<String>
) {
    private val config: ItemsConfig = settings.itemSheet
    private val enabled: Boolean = settings.statEnabled

    private var bestItemsProgress: List<ItemData>? = null
    private var bestPlayer: String? = null

    /** to save time and not count same list twice */
    private var bestItemsCount: Int? = null

    /** Takes advancements for one player
     *  @param uuid uuid of player with stats
     *  @param json advancements file parsed as [JsonObject]
     */
    fun pushPlayer(uuid: String, json: JsonObject) {
        if (!enabled) return

        val items = Utils.getItemsData()
        var count = 0

        items.forEach {
            if (json.has(it.adv)) {
                it.done = json[it.adv]?.asJsonObject?.get("criteria")?.asJsonObject?.has(it.id) ?: false
                ++count
            }
        }

        if (count > (bestItemsCount ?: Int.MIN_VALUE)) {
            bestItemsProgress = items
            bestItemsCount = count
            bestPlayer = uuid
        }
    }

    fun getValueRange(): List<ValueRange> {
        val itemIdMap = bestItemsProgress?.associateBy { it.id }

        return listOf(
            ValueRange()
                .setRange("${config.name}!${config.statusRange}")
                .setValues(itemIdCache.map { listOf(itemIdMap?.get(it)?.done) }),
            ValueRange()
                .setRange("${config.name}!${config.whoRange}")
                .setValues(itemIdCache.map {
                    listOf(bestPlayer?.let { uuid ->
                        if (itemIdMap?.get(it)?.done == true)
                            "=IMAGE(\"https://crafatar.com/avatars/${uuid}?size=16&overlay\")"
                        else null
                    })
                }),
        )
    }
}