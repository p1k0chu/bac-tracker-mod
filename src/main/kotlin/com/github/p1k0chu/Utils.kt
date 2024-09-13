package com.github.p1k0chu

import com.github.p1k0chu.data.AdvancementData
import com.github.p1k0chu.data.ItemData
import com.github.p1k0chu.data.ScoreboardData
import com.github.p1k0chu.data.StatData
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.apache.commons.csv.CSVFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.reader


object Utils {
    const val MOD_ID = "bac-tracker-mod"
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    private val csvFormat: CSVFormat =
        CSVFormat.Builder.create(CSVFormat.DEFAULT).apply {
            setIgnoreSurroundingSpaces(true)
            setDelimiter(',')
        }.build()

    val GSON: Gson = Gson().newBuilder().setPrettyPrinting().create()

    val CONFIG_DIR: Path = FabricLoader.getInstance().configDir.resolve(MOD_ID)
    val CREDENTIALS_FILE: Path = CONFIG_DIR.resolve("credentials.json")


    /**
     * Reads scoreboard data and returns it all as a list
     */
    fun getScoreboardsData(): List<ScoreboardData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/scoreboards.csv")
        return csvFormat.parse(res?.reader())
            .map(::ScoreboardData)
    }

    /**
     * Reads stats data and returns it all as a list
     */
    fun getStatsData(): List<StatData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/stats.csv")
        return csvFormat.parse(res?.reader())
            .map(::StatData)
    }

    /**
     * Reads item data and returns it all as a list
     */
    fun getItemsData(): List<ItemData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/item_adv_stacksize.csv")
        return csvFormat.parse(res?.reader())
            .map(::ItemData)
    }

    /**
     * Reads advancement data and returns it all as a list
     */
    fun getAdvData(): List<AdvancementData> {
        val res: InputStream? = this::class.java.getResourceAsStream("/tracker_data/adv_col.csv")
        return csvFormat.parse(res?.reader())
            .map(::AdvancementData)
    }

    /**
     * For when you use Gson to parse json files to [T]
     *
     * Used for advancement and stats files
     *
     * Catches [JsonIOException],[JsonSyntaxException], and [NullPointerException] when [Gson.fromJson] returns null
     *
     * Would throw any other exception
     */
    inline fun <reified T> parseJsonHandlingErrors(path: Path): Result<T> {
        try {
            return Result.success(path.reader().use { reader ->
                GSON.fromJson(reader, T::class.java)
            })
        } catch (e: Exception) {
            when (e) {
                is JsonIOException -> {
                    logger.error("Failed to read the file ${path.fileName}", e)
                }
                is JsonSyntaxException -> {
                    logger.error("Json is not a valid representation in file: ${path.fileName}")
                }
                is NullPointerException -> {
                    // fromJson returns null if file is at EOF
                    logger.warn("EOF file reading file: ${path.fileName}")
                }

                else -> throw e
            }
            return Result.failure(e)
        }
    }
}

/** Auto save and update autosave interval appropriately.
 *
 * Same code for autosave as minecraft uses as of 1.21
 */
fun MinecraftServer.autosave() {
    // quick look at mc sources: thread is blocked until files are saved
    ticksUntilAutosave = autosaveInterval
    Utils.logger.debug("Autosave started")
    profiler.push("save")
    saveAll(true, false, false)
    profiler.pop()
    Utils.logger.debug("Autosave finished")
}