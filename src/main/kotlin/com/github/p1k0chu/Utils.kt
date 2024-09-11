package com.github.p1k0chu

import com.github.p1k0chu.data.AdvancementData
import com.github.p1k0chu.data.ItemData
import com.github.p1k0chu.data.ScoreboardData
import com.github.p1k0chu.data.StatData
import com.google.gson.Gson
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.apache.commons.csv.CSVFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Path


object Utils {
    const val MOD_ID = "bac-tracker-mod"
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    private val csvFormat: CSVFormat by lazy {
        CSVFormat.Builder.create(CSVFormat.DEFAULT).apply {
            setIgnoreSurroundingSpaces(true)
            setDelimiter(',')
        }.build()
    }

    //lazy, because it might be never used
    val GSON: Gson by lazy { Gson().newBuilder().setPrettyPrinting().create() }

    val CONFIG_DIR: Path = FabricLoader.getInstance().configDir.resolve(MOD_ID)
    val CREDENTIALS_FILE: Path = CONFIG_DIR.resolve("credentials.json")

    /**
     * @return Returns a stream of [ScoreboardData] or null if file is missing or error happened
     */
    fun getScoreboardsData(): List<ScoreboardData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/scoreboards.csv")
        return csvFormat.parse(res?.reader())
            .map(::ScoreboardData)
    }

    /**
     * @return Returns a stream of [StatData] or null if file is missing or error happened
     */
    fun getStatsData(): List<StatData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/stats.csv")
        return csvFormat.parse(res?.reader())
            .map(::StatData)
    }

    /**
     * @return Returns a stream of [ItemData] or null if file is missing or error happened
     */
    fun getItemsData(): List<ItemData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/item_adv_stacksize.csv")
        return csvFormat.parse(res?.reader())
            .map(::ItemData)
    }

    /**
     * @return Returns a stream of [AdvancementData] or null if file is missing or error happened
     */
    fun getAdvData(): List<AdvancementData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/adv_col.csv")
        return csvFormat.parse(res?.reader())
            .map(::AdvancementData)
    }
}

/** Force autosave and update autosave interval appropriately.
 * Same code for autosave as minecraft uses as of 1.21.1
 */
fun MinecraftServer.forceAutosave() {
    // quick look at mc sources: thread is blocked until files are saved
    ticksUntilAutosave = autosaveInterval
    Utils.logger.debug("Autosave started")
    profiler.push("save")
    saveAll(true, false, false)
    profiler.pop()
    Utils.logger.debug("Autosave finished")
}