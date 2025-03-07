package com.github.p1k0chu.mcmod.bac_tracker

import com.github.p1k0chu.mcmod.bac_tracker.data.AdvancementData
import com.github.p1k0chu.mcmod.bac_tracker.data.ItemData
import com.github.p1k0chu.mcmod.bac_tracker.data.ScoreboardData
import com.github.p1k0chu.mcmod.bac_tracker.data.StatData
import com.github.p1k0chu.mcmod.bac_tracker.event.AdvancementUpdatedCallback
import com.github.p1k0chu.mcmod.bac_tracker.event.ScoreboardUpdatedCallback
import com.github.p1k0chu.mcmod.bac_tracker.event.StatUpdatedCallback
import com.github.p1k0chu.mcmod.bac_tracker.settings.Settings
import com.github.p1k0chu.mcmod.bac_tracker.utils.AdvancementProgressGetter
import com.github.p1k0chu.mcmod.bac_tracker.utils.ComparingType
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.buildSheet
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.findLatestCriteriaObtainedDate
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.getProfilePictureByUuid
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.makeSureDirectoryExists
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.moveRangeDownBy
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.singleColumnValueRange
import com.github.p1k0chu.mcmod.bac_tracker.utils.Utils.getIdOrUrl
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.BatchUpdateValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.atomicfu.locks.synchronized
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.advancement.AdvancementEntry
import net.minecraft.advancement.AdvancementProgress
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stat
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.reader
import kotlin.jvm.optionals.getOrNull
import kotlin.math.min

object Main : ModInitializer {
    const val MOD_ID: String = "bac-tracker-mod"
    const val APP_NAME: String = "BACAP Tracker"
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    val GSON: Gson = Gson().newBuilder().setPrettyPrinting().create()

    val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm:ss a dd-MM-yyyy z Z").withLocale(Locale.of("en-US"))
            .withZone(ZoneId.systemDefault())

    // this regex is commonly used and Pattern.compile takes some milliseconds to perform,
    // so it's a static member now
    private val getStatRegex: Pattern = Pattern.compile("minecraft\\.(?<type>.+):minecraft\\.(?<name>.+)")

    private var advMap: MutableMap<String, AdvancementData>? = null
    private var statMap: MutableMap<String, StatData>? = null
    private var scoreboardMap: MutableMap<String, ScoreboardData>? = null
    private var itemMap: MutableMap<String, MutableMap<String, ItemData>>? = null

    private val updatePool: MutableMap<String, ValueRange> = mutableMapOf()

    private var server: MinecraftServer? = null
    private var sheetApi: Sheets? = null
    var settings: Settings? = null
        private set
    var state: State = State.NOT_INITIALIZED
        private set

    // single thread executor for API calls
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // this bad boy is for loop of 10 seconds, to just stash all updates and send at once, every 10 seconds
    private var scheduledExecutor: ScheduledExecutorService? = null

    // exposing executor to public ONLY for submitting tasks
    fun <T>submitTask(task: Callable<T>): Future<T> {
        return executor.submit(task)
    }

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { server: MinecraftServer ->
            this.server = server
            submitTask(::reloadConfigAndData)
        }

        ScoreboardUpdatedCallback.SCORE_UPDATED.register { ownerUUID: String, objective: ScoreboardObjective, oldScore: Int, newScore: Int ->
            // action on scoreboard update
            if (this.settings?.scoreboardEnabled != true) {
                return@register
            }

            val scoreboardData: ScoreboardData = this.scoreboardMap?.get(objective.name) ?: return@register

            when(scoreboardData.type) {
                ComparingType.SUM -> {
                    val change = newScore - oldScore
                    val i: Int = min(Int.MAX_VALUE.toLong(), scoreboardData.value.toLong() + change).toInt()
                    scoreboardData.value = i
                }
                ComparingType.MAX -> {
                    if(scoreboardData.value > newScore) {
                        return@register
                    } else {
                        scoreboardData.value = newScore

                        if (ownerUUID != scoreboardData.player) {
                            scoreboardData.player = ownerUUID

                            putUpdateInPool(
                                this.settings!!.statSheet.name,
                                this.settings!!.statSheet.whoRange,
                                scoreboardData.index,
                                getProfilePictureByUuid(scoreboardData.player)
                            )
                        }
                    }
                }
            }

            putUpdateInPool(
                this.settings!!.statSheet.name,
                this.settings!!.statSheet.valueRange,
                scoreboardData.index,
                scoreboardData.value
            )
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server: MinecraftServer ->
            // "free" memory of unused objects
            advMap = null
            statMap = null
            scoreboardMap = null
            itemMap = null

            // keeping settings and sheetApi for 'executePendingUpdates'

            this.server = null

            scheduledExecutor?.shutdown()
            scheduledExecutor = null

            if(server.isDedicated){
                executor.shutdown()
            }

            this.state = State.NOT_INITIALIZED
        }

        AdvancementUpdatedCallback.CRITERION_CHANGED.register { player: ServerPlayerEntity, advancementEntry: AdvancementEntry, criteriaName: String, value: Boolean ->
            if (this.state != State.INITIALIZED || this.settings == null) {
                return@register
            }

            onAdvancementUpdate(player, advancementEntry)
            updateItem(advancementEntry, criteriaName, value)
        }

        StatUpdatedCallback.EVENT.register { player: PlayerEntity, stat: Stat<*>, oldValue: Int, newValue: Int ->
            if (this.state != State.INITIALIZED) {
                return@register
            }

            if (this.settings?.statEnabled != true) {
                return@register
            }

            val statData: StatData = getStatFromPath(stat.name) ?: return@register

            when (statData.type) {
                ComparingType.SUM -> {
                    val change = newValue - oldValue
                    val i: Int = min(statData.value.toLong() + change, Int.MAX_VALUE.toLong()).toInt()
                    statData.value = i
                }
                ComparingType.MAX -> {
                    if (newValue > statData.value) {
                        statData.value = newValue

                        if(statData.player != player.uuidAsString) {
                            statData.player = player.uuidAsString

                            putUpdateInPool(
                                this.settings!!.statSheet.name,
                                this.settings!!.statSheet.whoRange,
                                statData.index,
                                getProfilePictureByUuid(player.uuidAsString)
                            )
                        }
                    } else {
                        return@register
                    }
                }
            }

            putUpdateInPool(
                this.settings!!.statSheet.name,
                this.settings!!.statSheet.valueRange,
                statData.index,
                newValue
            )
        }

        CommandRegistrationCallback.EVENT.register(TrackerCommand::register)
    }

    /** parse path of the stat like "custom.death" and return StatData object corresponding to this stat */
    private fun getStatFromPath(stat: String): StatData? {
        val m = getStatRegex.matcher(stat)
        if (!m.find()) return null

        val type = m.group("type")
        val name = m.group("name")

        return statMap?.get("$type.$name")
    }

    /** attempt to update the item. if it's not an item advancement it's fine
     * @param advancementEntry advancement that got updated
     * @param criteriaName criteria name that got updated, aka. item id
     * @param value bool value indicating if criteria is achieved or not
     */
    private fun updateItem(advancementEntry: AdvancementEntry, criteriaName: String, value: Boolean) {
        val advId: String = advancementEntry.id().toString()

        this.itemMap?.get(advId)?.get(criteriaName)?.let { itemData ->
            if (itemData.done != value) {
                itemData.done = value

                putUpdateInPool(
                    this.settings!!.itemSheet.name,
                    this.settings!!.itemSheet.statusRange,
                    itemData.index,
                    value
                )
            }
        }
    }

    /** function ran when advancement is updated.
     * compares new and old progress and decides to update it on sheets or not
     * @param player Player whom progress was updated
     * @param advancementEntry the advancement that got updated
     */
    private fun onAdvancementUpdate(player: ServerPlayerEntity, advancementEntry: AdvancementEntry) {
        if (this.settings == null || this.sheetApi == null) return

        val advId: String = advancementEntry.id().toString()

        val advancement: AdvancementData = this.advMap?.get(advId) ?: return
        val advancementProgress = player.advancementTracker.getProgress(advancementEntry)
        val progressGetter = advancementProgress as AdvancementProgressGetter

        val newCriteriaProgress: AdvancementData.Progress = progressGetter.`bac_tracker_mod$getProgress`()
        val newInstant: Instant? = progressGetter.`bac_tracker_mod$getLatestProgressObtainDate`()

        // statement to find out if new progression state is better than old one
        if (!advancement.done && advancementProgress.isDone ||
            (advancement.progress?.compareTo(newCriteriaProgress) ?: 0) < 0
        ) {
            applyAdvancementChangesInAPI(
                oldAdvData = advancement,
                newAdvProgress = advancementProgress,
                newCriteriaProgress = newCriteriaProgress,
                player = player,
                newInstant = newInstant
            )

            advancement.done = advancementProgress.isDone
            advancement.progress = newCriteriaProgress
            advancement.doneTime = newInstant
            advancement.player = player.uuidAsString
        }
    }

    /** used to update values on the sheet.
     * puts all updates in a pool, which is a Map<String, ValueRange>
     *
     * key in this pool is the cell being updated, so newer updates for the same cell will override old ones
     * @param sheetName name of the tab on the sheet
     * @param range the range where this value is placed
     * @param index the index of your value in the [range]
     * @param value the value
     */
    private fun putUpdateInPool(sheetName: String, range: String, index: Int, value: Any?) {
        moveRangeDownBy(range, index)?.let { cell: String ->
            val valueRange = ValueRange()
                .setValues(listOf(listOf((value))))
                .setRange("${sheetName}!$cell")

            synchronized(this.updatePool) {
                this.updatePool.put("${sheetName}!${cell}", valueRange)
            }
        }
    }

    /** [AdvancementData] has a lot of fields, so updating advancement data is done in this function
     * @param oldAdvData old data of the advancement updated
     * @param newAdvProgress new advancement progress
     * @param newCriteriaProgress new progress of this advancement A / B, where A is obtained criteria and B is total amount of criteria
     * @param player the player who gets credit for this advancement
     * @param newInstant this advancement's obtain date
     */
    private fun applyAdvancementChangesInAPI(
        oldAdvData: AdvancementData,
        newAdvProgress: AdvancementProgress,
        newCriteriaProgress: AdvancementData.Progress,
        player: ServerPlayerEntity,
        newInstant: Instant?
    ) {
        // status
        if (oldAdvData.done != newAdvProgress.isDone) {
            putUpdateInPool(
                this.settings!!.advSheet.name,
                this.settings!!.advSheet.statusRange,
                oldAdvData.index,
                newAdvProgress.isDone
            )
        }
        // progress
        if (oldAdvData.progress != newCriteriaProgress) {
            putUpdateInPool(
                this.settings!!.advSheet.name,
                this.settings!!.advSheet.progressRange,
                oldAdvData.index,
                newCriteriaProgress.toString()
            )
        }
        // done time
        if (oldAdvData.doneTime != newInstant) {
            putUpdateInPool(
                this.settings!!.advSheet.name,
                this.settings!!.advSheet.whenRange,
                oldAdvData.index,
                timeFormatter.format(newInstant)
            )
        }
        // player
        if (oldAdvData.player != player.uuidAsString) {
            putUpdateInPool(
                this.settings!!.advSheet.name,
                this.settings!!.advSheet.whoRange,
                oldAdvData.index,
                getProfilePictureByUuid(player.uuidAsString)
            )
        }

        // missing criteria
        // its very likely to change, so it is, probably, better to just update it
        // without storing incomplete criteria list to know if it is changed or not
        putUpdateInPool(
            this.settings!!.advSheet.name,
            this.settings!!.advSheet.incompleteCriteriaRange,
            oldAdvData.index,
            newAdvProgress.unobtainedCriteria.joinToString(separator = ", ")
        )
    }

    /** sends updates from [updatePool] to google sheet and clears the pool */
    private fun executePendingUpdates() {
        if (this.state != State.INITIALIZED) {
            return
        }

        val updates: MutableList<ValueRange> = mutableListOf()

        // transfer updates into local variable
        synchronized(this.updatePool) {
            val it = this.updatePool.values.iterator()
            while (it.hasNext()) {
                updates.add(it.next())
                it.remove()
            }
        }

        if (updates.isEmpty()) return

        submitTask {
            try {
                batchUpdate(updates)
            } catch (e: Exception) {
                logger.error("Caught exception while making api request", e)
                server?.sendMessage(Text.of("Caught exception while making api request, mod automatically shuts down... run `/tracker reload` when you think you fixed your stuff"))
                state = State.NOT_INITIALIZED
            }
        }
    }

    /** Reloads config and advancements, items and stats data.
     * if returned true it loaded perfectly. if its false some errors happened
     */
    fun reloadConfigAndData(): Boolean {
        try {
            val settingsGlobalFolder: Path = FabricLoader.getInstance().configDir.resolve(MOD_ID)
            val settingsPerWorldFolder = server?.getSavePath(WorldSavePath.ROOT)?.resolve("tracker") ?: return false
            val advancementFolder = server?.getSavePath(WorldSavePath.ADVANCEMENTS) ?: return false

            val credPath = settingsGlobalFolder.resolve("credentials.json")
            val settingsFile = settingsPerWorldFolder.resolve("settings.json")

            makeSureDirectoryExists(settingsGlobalFolder)
            makeSureDirectoryExists(settingsPerWorldFolder)

            if (!settingsFile.toFile().exists()) {
                // write a default config file for easy editing
                settingsFile.toFile().writer().use { w ->
                    GSON.toJson(Settings(), w)
                }
                throw FileNotFoundException(
                    "settings file is missing. created new file for you, edit at: ${
                        settingsFile.toAbsolutePath().normalize()
                    }"
                )
            }

            this.sheetApi = buildSheet(credPath)

            this.settings = settingsFile.toFile().reader().use { r ->
                GSON.fromJson(r, Settings::class.java)
            }

            // if url, convert to id
            this.settings!!.sheetId = getIdOrUrl(this.settings!!.sheetId)

            // load items
            val (itemIds, itemAdvIds) = batchGet(
                listOf(
                    "${settings!!.itemSheet.name}!${settings!!.itemSheet.idRange}",
                    "${settings!!.itemSheet.name}!${settings!!.itemSheet.advRange}"
                )
            )!!

            initializeItems(itemIds, itemAdvIds)

            // loading advancements
            // download all tracked advancement ids
            val advIds: List<String> = singleColumnValueRange(
                getValueRange("${settings!!.advSheet.name}!${settings!!.advSheet.idRange}")!!
            )

            initializeAdv(advIds)

            advancementFolder.listDirectoryEntries("*.json").forEach(::updateAdvancementsFromFile)

            // making api request to the sheet to get all tracked stats and scoreboards, and their types
            val (statIds, statTypes, comps) = batchGet(
                listOf(
                    "${settings!!.statSheet.name}!${settings!!.statSheet.idRange}",
                    "${settings!!.statSheet.name}!${settings!!.statSheet.typeRange}",
                    "${settings!!.statSheet.name}!${settings!!.statSheet.comparingTypeRange}"
                )
            )!!

            initializeStatsAndScoreboard(statIds, statTypes, comps)

            // api call to update everything
            batchUpdate(getValueRangesToUpdateEverything(advIds, statIds, itemIds, itemAdvIds))

            this.state = State.INITIALIZED

            // stop old schedule and
            // start new schedule to execute updates every 10 seconds
            scheduledExecutor?.shutdown()
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
            scheduledExecutor!!.scheduleWithFixedDelay(
                this::executePendingUpdates,
                settings!!.updateDelaySeconds,
                settings!!.updateDelaySeconds,
                TimeUnit.SECONDS
            )

            return true
        } catch (e: Exception) {
            logger.error(e.message, e)

            this.state = State.NOT_INITIALIZED
            return false
        }
    }

    private fun getValueRangesToUpdateEverything(
        advIds: List<String>,
        statIds: List<String>,
        itemIds: List<String>,
        itemAdvIds: List<String>
    ) = listOf(
        // advancement completion
        ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.statusRange}")
            .setValues(advIds.map { i -> listOf(this.advMap?.get(i)?.done) }),
        // advancement progress
        ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.progressRange}")
            .setValues(advIds.map { i -> listOf(this.advMap?.get(i)?.progress?.toString()) }),
        // advancement completion time
        ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.whenRange}")
            .setValues(advIds.map { i -> listOf(this.advMap?.get(i)?.doneTime?.let(timeFormatter::format)) }),
        // player with best advancement
        ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.whoRange}")
            .setValues(advIds.map { i ->
                listOf(
                    getProfilePictureByUuid(
                        this.advMap?.get(
                            i
                        )?.player
                    )
                )
            }),

        // stats and scoreboards
        // stat value
        ValueRange().setRange("${settings!!.statSheet.name}!${settings!!.statSheet.valueRange}")
            .setValues(statIds.map { i ->
                listOf(this.statMap?.get(i)?.value ?: this.scoreboardMap?.get(i)?.value)
            }),
        // player with the best stat (if present)
        ValueRange().setRange("${settings!!.statSheet.name}!${settings!!.statSheet.whoRange}")
            .setValues(statIds.map { i ->
                listOf(
                    getProfilePictureByUuid(
                        this.statMap?.get(i)?.player ?: server?.userCache?.findByName(
                            this.scoreboardMap?.get(i)?.player ?: return@map emptyList()
                        )?.getOrNull()?.id?.toString()
                    )
                )
            }),

        // items
        // status
        ValueRange().setRange("${settings!!.itemSheet.name}!${settings!!.itemSheet.statusRange}")
            .setValues(itemIds.mapIndexed { i, itemId ->
                listOf(
                    this.itemMap?.get(itemAdvIds[i])?.get(itemId)?.done
                )
            })
    )

    private fun initializeStatsAndScoreboard(statIds: List<String?>, statTypes: List<String?>, comps: List<String?>) {
        scoreboardMap = mutableMapOf()
        statMap = mutableMapOf()

        for (i in statIds.indices) {
            val name: String = statIds[i] ?: continue
            val type: String = statTypes[i] ?: continue // "scoreboard" or "stat"
            val comp: String = comps[i] ?: continue

            when (type) {
                "stat" -> {
                    initStat(i, name, comp)
                }
                "scoreboard" -> {
                    initScoreboard(i, name, comp)
                }
                else -> {
                    logger.warn("Incorrect type \"{}\" for stat \"{}\" at row {}", type, name, i + 1)
                }
            }
        }
    }

    private fun initScoreboard(index: Int, name: String, comp: String) {
        val objective = server!!.scoreboard.getNullableObjective(name)

        if (objective == null) {
            logger.warn("non-existent scoreboard objective: {}", name)
            return
        }

        var (maxValue, maxValuePlayer) = maxScoreboardValue(objective, comp)

        if (maxValuePlayer != null) {
            maxValuePlayer = server!!.userCache?.findByName(maxValuePlayer)?.getOrNull()?.id?.toString()
        }

        scoreboardMap!![name] = ScoreboardData(comp, maxValue, maxValuePlayer, index)
    }

    /** finds max scoreboard value and the player holding this value
     * @param comp Comparison type for the scoreboard. e.g. "max" and "sum"
     * @return a pair of scoreboard value and player IGN
     */
    private fun maxScoreboardValue(objective: ScoreboardObjective, comp: String): Pair<Int, String?> {
        var maxValue = 0
        var maxValuePlayer: String? = null // as ign

        for (entry in server!!.scoreboard.getScoreboardEntries(objective)) {
            val value = entry.value()

            if (comp == "max") {
                if (value > maxValue) {
                    maxValue = value
                    maxValuePlayer = entry.owner()
                }
            } else if (comp == "sum") {
                maxValue += value
            } else {
                logger.warn("Incorrect comparison type for scoreboard \"{}\": \"{}\"", objective.name, comp)
            }
        }

        return maxValue to maxValuePlayer
    }

    private fun initStat(index: Int, name: String, comp: String) {
        val (bestValue, bestPlayer) = findMaxStatValueFromFiles(name, comp) ?: return

        statMap!![name] = StatData(bestPlayer, comp, bestValue, index)
    }

    /**
     * @return pair of the best value and player who holds this value
     */
    private fun findMaxStatValueFromFiles(name: String, comp: String): Pair<Int, String?>? {
        val x: List<String> = name
            .split(".", limit = 2)

        if (x.size < 2) {
            logger.error(
                "wrong format for stat ({}) in the sheet: must be category.name; e.g. \"custom.deaths\"; skipping",
                name
            )
            return null
        }

        val (statType, statObj) = x

        var bestValue = 0
        var bestPlayer: String? = null

        val files: Array<File> = server!!.getSavePath(WorldSavePath.STATS).toFile()
            .listFiles { f: File -> f.name.endsWith(".json") }!!

        for (f: File in files) {
            val j: JsonObject = f.reader().use { r ->
                GSON.fromJson(r, JsonObject::class.java).get("stats").getAsJsonObject()
            }
            val value: Int =
                j.get("minecraft:$statType")?.getAsJsonObject()?.get("minecraft:$statObj")?.asInt
                    ?: continue

            if (comp == "max") {
                if (value > bestValue) {
                    bestValue = value
                    bestPlayer = f.nameWithoutExtension
                }
            } else if (comp == "sum") {
                bestValue += value
            } else {
                logger.warn("Incorrect comparison type for stat \"{}\": \"{}\"", name, comp)
            }
        }

        return bestValue to bestPlayer
    }

    private fun updateAdvancementsFromFile(advFile: Path) {
        val advFileJson: JsonObject = advFile.reader().use { r ->
            GSON.fromJson(r, JsonObject::class.java)
        }

        // advFileJson.keySet() is a set of advancement ids **and** "DataVersion"
        for (advId in advFileJson.keySet()) {
            if (!advFileJson[advId]!!.isJsonObject) continue

            val advJson: JsonObject = advFileJson[advId].asJsonObject

            updateAdvancement(advId, advJson, advFile.nameWithoutExtension)
            updateItemsFromAdvancement(advId, advJson)
        }
    }

    private fun updateAdvancement(advId: String, advJson: JsonObject, playerUUID: String) {
        val adv: AdvancementData = this.advMap?.get(advId) ?: return

        if (!adv.done) {
            if (advJson["done"]?.asBoolean == true) {
                adv.done = true
                adv.player = playerUUID
                adv.doneTime = findLatestCriteriaObtainedDate(advJson)

                // set progress to 100%
                adv.progress?.nom = adv.progress!!.den
            } else {
                adv.progress?.nom = advJson["criteria"].getAsJsonObject().size()
            }
        }
    }

    private fun updateItemsFromAdvancement(advId: String, advJson: JsonObject) {
        this.itemMap?.get(advId)?.let { itemAdvancement ->
            advJson["criteria"].asJsonObject.keySet().forEach { criteria ->
                itemAdvancement[criteria]?.done = true
            }
        }
    }

    private fun initializeAdv(advIds: List<String>) {
        advMap = mutableMapOf()

        for (i in advIds.indices) {
            val advancementEntry: AdvancementEntry? = server!!.advancementLoader.get(Identifier.of(advIds[i]))

            if (advancementEntry == null) {
                logger.warn("Couldn't find loaded advancement with name {}", advIds[i])
                continue
            }

            advMap!![advIds[i]] = AdvancementData(
                false, null, null, i, AdvancementData.Progress(0, advancementEntry.value().criteria().size)
            )
        }
    }

    private fun initializeItems(itemIds: List<String>, itemAdvIds: List<String>) {
        itemMap = mutableMapOf()

        for (i in itemIds.indices) {
            // put new hash map for advancement id if it doesn't exist yet
            if (!itemMap!!.containsKey(itemAdvIds[i])) {
                itemMap!![itemAdvIds[i]] = mutableMapOf()
            }

            // put item in the map of advancement
            itemMap!![itemAdvIds[i]]!![itemIds[i]] = ItemData(index = i)
        }
    }

    private fun batchUpdate(valueRanges: List<ValueRange>): BatchUpdateValuesResponse? {
        val body = BatchUpdateValuesRequest().setValueInputOption("USER_ENTERED").setIncludeValuesInResponse(false)
            .setData(valueRanges)
        return this.sheetApi?.spreadsheets()?.values()?.batchUpdate(this.settings?.sheetId ?: return null, body)?.execute()
    }

    private fun batchGet(cellRanges: List<String>): List<List<String>>? {
        return this.sheetApi
            ?.spreadsheets()?.values()
            ?.batchGet(this.settings?.sheetId ?: return null)
            ?.setRanges(cellRanges)
            ?.execute()
            ?.valueRanges
            ?.map(::singleColumnValueRange)
    }

    private fun getValueRange(cellRange: String): ValueRange? {
        return this.sheetApi
            ?.spreadsheets()?.values()
            ?.get(this.settings?.sheetId ?: return null, cellRange)
            ?.execute()
    }

    enum class State {
        NOT_INITIALIZED, INITIALIZED
    }
}
