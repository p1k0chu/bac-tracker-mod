package com.github.p1k0chu.sheet

import com.github.p1k0chu.Utils
import com.github.p1k0chu.config.ItemsConfig
import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.data.ItemData
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.reader

class ItemSheet(
    service: Sheets,
    settings: Settings
) : Sheet {
    private val sheetId: String = settings.sheetId
    private val config: ItemsConfig = settings.itemSheet
    private val enabled: Boolean = settings.statEnabled

    private val itemIdCache: List<String> by lazy {
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

    override fun update(server: MinecraftServer): List<ValueRange> {
        if(!enabled) return listOf()

        var bestItemsProgress: List<ItemData>? = null
        var bestPlayer: String? = null

        /** to save time and not count same list twice */
        var bestItemsCount: Int? = null

        server.getSavePath(WorldSavePath.ADVANCEMENTS).listDirectoryEntries("*.json").forEach { path ->
            val json = path.reader().use { reader ->
                Utils.GSON.fromJson(reader, JsonObject::class.java)
            }
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
                bestPlayer = path.nameWithoutExtension
            }
        }

        val itemIdMap = bestItemsProgress?.associateBy { it.id }



        return listOf(
            ValueRange()
                .setRange("${config.name}!${config.statusRange}")
                .setValues(itemIdCache.map { listOf(itemIdMap?.get(it)?.done) }),
            ValueRange()
                .setRange("${config.name}!${config.whoRange}")
                .setValues(itemIdCache.map {
                    listOf(bestPlayer?.let { uuid ->
                        if(itemIdMap?.get(it)?.done == true)
                            "=IMAGE(\"https://crafatar.com/avatars/${uuid}?size=16&overlay\")"
                        else null
                    })
                }),
        )
    }

}