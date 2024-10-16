package com.github.p1k0chu.mcmod.bac_tracker

import com.github.p1k0chu.mcmod.bac_tracker.data.AdvancementData
import com.github.p1k0chu.mcmod.bac_tracker.data.ItemData
import com.github.p1k0chu.mcmod.bac_tracker.data.ScoreboardData
import com.github.p1k0chu.mcmod.bac_tracker.data.StatData
import com.github.p1k0chu.mcmod.bac_tracker.event.AdvancementUpdatedCallback
import com.github.p1k0chu.mcmod.bac_tracker.event.StatUpdatedCallback
import com.github.p1k0chu.mcmod.bac_tracker.settings.GlobalSettings
import com.github.p1k0chu.mcmod.bac_tracker.settings.Settings
import com.github.p1k0chu.mcmod.bac_tracker.utils.AdvancementProgressGetter
import com.github.p1k0chu.mcmod.bac_tracker.utils.ComparingType
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import kotlinx.io.IOException
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.advancement.AdvancementEntry
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stat
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.min


class Main : ModInitializer {
    private var advancementsMap: MutableMap<String, AdvancementData>? = null
    private var statMap: MutableMap<String, StatData>? = null
    private var scoreboardMap: MutableMap<String, ScoreboardData>? = null
    private var itemMap: MutableMap<String, MutableMap<String, ItemData>>? = null

    private val updatePool: MutableMap<String, ValueRange> = mutableMapOf()

    private var sheetApi: Sheets? = null
    private var settings: Settings? = null
    private var server: MinecraftServer? = null
    private var state: State = State.NOT_INITIALIZED

    // single thread executor for API calls
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // this bad boy is for loop of 10 seconds, to just stash all updates and send at once, every 10 seconds
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun onInitialize() {
        val settingsFile = FabricLoader.getInstance().configDir.resolve(MOD_ID).resolve("settings.json")

        val s: GlobalSettings = try {
            FileReader(settingsFile.toFile()).use {
                GSON.fromJson(it, GlobalSettings::class.java)
            }
        } catch (_: IOException) {
            val x = GlobalSettings()

            try {
                FileWriter(settingsFile.toFile()).use {
                    GSON.toJson(x, it)
                }
            } catch (e: IOException) {
                logger.error("Failed to save settings file!", e)
                throw e
            }
            x
        }

        scheduledExecutor.scheduleWithFixedDelay(this::executePendingUpdates, s.refreshDelay, s.refreshDelay, TimeUnit.SECONDS)

        ServerLifecycleEvents.SERVER_STARTED.register { server: MinecraftServer ->
            this.server = server
            executor.execute { this.reloadConfig(server) }

            val sb = server.scoreboard

            sb.addUpdateListener {
                // action on scoreboard update
                if (this.settings?.scoreboardEnabled != true) {
                    return@addUpdateListener
                }

                // iterate over every scoreboard, since we don't know which one changed
                for (objective in sb.objectives) {
                    val sData: ScoreboardData = this.scoreboardMap?.get(objective.name) ?: continue

                    var bestValue = 0
                    var bestPlayer: String? = null

                    for (entry in sb.getScoreboardEntries(objective)) {
                        val value = entry.value()

                        when (sData.type) {
                            ComparingType.SUM ->
                                if (value > bestValue) {
                                    bestValue = value
                                    bestPlayer = getUuid(entry.owner())
                                }

                            ComparingType.MAX -> bestValue += value
                        }
                    }

                    // value
                    if (sData.value != bestValue) {
                        sData.value = bestValue

                        val cell: String? = getCellByIndex(this.settings!!.statSheet.valueRange, sData.index)

                        if (cell != null) {
                            val value = ValueRange().setValues(listOf(listOf(bestValue)))
                                .setRange("${this.settings!!.itemSheet.name}!$cell")

                            synchronized(this.updatePool) {
                                this.updatePool.put("scv: ${objective.name}", value)
                            }
                        }
                    }

                    // player
                    if (bestPlayer != null && bestPlayer != sData.player) {
                        sData.player = bestPlayer

                        val cell: String? = getCellByIndex(this.settings!!.statSheet.whoRange, sData.index)

                        if (cell != null) {
                            val value = ValueRange().setValues(listOf(listOf((bestPlayer))))
                                .setRange("${this.settings!!.itemSheet.name}!$cell")

                            synchronized(this.updatePool) {
                                // scp is scoreboard player, trust me
                                this.updatePool.put("scp: ${objective.name}", value)
                            }
                        }
                    }
                }
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server: MinecraftServer ->
            this.server = null

            this.state = State.NOT_INITIALIZED
        }

        AdvancementUpdatedCallback.CRITERION_CHANGED.register { player: ServerPlayerEntity, advancementEntry: AdvancementEntry, criteriaName: String, value: Boolean ->
            // action on advancement update
            if (this.state != State.INITIALIZED || this.settings == null) {
                return@register
            }

            val advId: String = advancementEntry.id().toString()

            this.itemMap?.get(advId)?.get(criteriaName)?.let { itemData ->
                if (itemData.done != value) {
                    itemData.done = value

                    val cell: String =
                        getCellByIndex(this.settings!!.itemSheet.statusRange, itemData.index) ?: return@let
                    val value = ValueRange().setValues(listOf(listOf(value)))
                        .setRange("${this.settings!!.itemSheet.name}!$cell")

                    synchronized(this.updatePool) {
                        this.updatePool.put("i: $criteriaName", value)
                    }
                }
            }

            // continue with normal advancement update
            onAdvancementUpdate(player, advancementEntry)
        }

        StatUpdatedCallback.EVENT.register { player: PlayerEntity, stat: Stat<*>, oldValue: Int, newValue: Int ->
            if (this.state != State.INITIALIZED || this.statMap == null) {
                return@register
            }

            if (this.settings?.statEnabled != true) {
                return@register
            }

            val statRegex = getStatRegex
            val m = statRegex.matcher(stat.name)
            if (!m.find()) return@register

            val statData: StatData = this.statMap?.get("${m.group("type")}.${m.group("name")}") ?: return@register

            if (statData.type == ComparingType.SUM) {
                val change = newValue - oldValue
                val i: Int = min(statData.value.toLong() + change, Int.MAX_VALUE.toLong()).toInt()
                statData.value = i
            } else if (statData.type == ComparingType.MAX) {
                if (newValue > statData.value) {
                    statData.value = newValue
                    statData.player = player.uuidAsString
                } else {
                    return@register
                }
            }

            getCellByIndex(this.settings!!.statSheet.valueRange, statData.index)?.let { cell: String ->
                synchronized(this.updatePool) {
                    this.updatePool.put(
                        "s: ${stat.name}",
                        ValueRange().setValues(listOf(listOf(newValue)))
                            .setRange("${this.settings!!.statSheet.name}!$cell")
                    )
                }
            }
            getCellByIndex(this.settings!!.statSheet.whoRange, statData.index)?.let { cell: String ->
                synchronized(this.updatePool) {
                    this.updatePool.put(
                        "sp: ${stat.name}",
                        ValueRange().setValues(listOf(listOf(getProfilePictureByUuid(player.uuidAsString))))
                            .setRange("${this.settings!!.statSheet.name}!$cell")
                    )
                }
            }
        }

        CommandRegistrationCallback.EVENT.register { dispatcher: CommandDispatcher<ServerCommandSource?>, commandRegistryAccess: CommandRegistryAccess, environment: RegistrationEnvironment ->
            dispatcher.register(CommandManager.literal("tracker").then(CommandManager.literal("reload")
                .requires { source: ServerCommandSource -> source.hasPermissionLevel(3) || source.player?.isMainPlayer == true }
                .executes { context: CommandContext<ServerCommandSource?> ->
                    this.executor.execute {
                        if (reloadConfig(context.source!!.server)) {
                            context.getSource()!!.sendFeedback({ Text.of("Successful reload") }, true)
                        } else {
                            context.getSource()!!
                                .sendFeedback({ Text.of("Errors happened when reloading, check logs") }, true)
                        }
                    }
                    0
                }).then(CommandManager.literal("sheet").executes { context: CommandContext<ServerCommandSource?>? ->
                if (this.state != State.INITIALIZED) {
                    context!!.getSource()!!.sendError(Text.of("Tracker is not initialized"))
                    return@executes 1
                }
                val url: String =
                    "https://docs.google.com/spreadsheets/d/${this.settings?.sheetId ?: return@executes 1}/edit"

                val response: Text? = Text.literal(url).styled { style: Style? ->
                    style!!.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url)).withHoverEvent(
                        HoverEvent(
                            HoverEvent.Action.SHOW_TEXT, Text.of("Click to open url")
                        )
                    ).withItalic(true).withUnderline(true)
                }

                context!!.getSource()!!.sendFeedback({ response }, false)
                0
            })
            )
        }
    }

    private fun onAdvancementUpdate(player: ServerPlayerEntity, advancementEntry: AdvancementEntry) {
        if (this.settings == null) return

        val advId: String = advancementEntry.id().toString()

        val advancement: AdvancementData = this.advancementsMap?.get(advId) ?: return
        val advancementProgress = player.advancementTracker.getProgress(advancementEntry)
        val apGetter = advancementProgress as AdvancementProgressGetter

        val newProgress: AdvancementData.Progress = apGetter.`bac_tracker_mod$getProgress`()
        val newInstant: Instant? = apGetter.`bac_tracker_mod$getLatestProgressObtainDate`()

        // huge if statement to find out if new progression state is better than old one
        if (!advancement.done && advancementProgress.isDone || (advancement.progress?.compareTo(newProgress)
                ?: 0) < 0 || advancement.doneTime != null && newInstant?.isBefore(advancement.doneTime!!) == true
        ) {
            // construct api call body
            // check for every field that is changed, and update these on the sheet
            // status
            if (advancement.done != advancementProgress.isDone) {
                advancement.done = advancementProgress.isDone

                getCellByIndex(this.settings!!.advSheet.statusRange, advancement.index)?.let { cell: String ->
                    val value = ValueRange().setValues(listOf(listOf((advancementProgress.isDone))))
                        .setRange("${this.settings!!.advSheet.name}!$cell")

                    synchronized(this.updatePool) {
                        this.updatePool.put("as: $advId", value)
                    }
                }
            }
            // progress
            if (advancement.progress != newProgress) {
                advancement.progress = newProgress

                getCellByIndex(this.settings!!.advSheet.progressRange, advancement.index)?.let { cell: String ->
                    val value = ValueRange().setValues(listOf(listOf(newProgress.toString())))
                        .setRange("${this.settings!!.advSheet.name}!$cell")

                    synchronized(this.updatePool) {
                        this.updatePool.put("ap: $advId", value)
                    }
                }
            }
            // done time
            if (advancement.doneTime != newInstant) {
                advancement.doneTime = newInstant

                getCellByIndex(this.settings!!.advSheet.whenRange, advancement.index)?.let { cell: String ->
                    newInstant?.let { ins: Instant ->
                        val value = ValueRange().setRange("${this.settings!!.advSheet.name}!$cell")
                            .setValues(listOf(listOf(timeFormatter.format(ins))))

                        synchronized(this.updatePool) {
                            this.updatePool.put("at: $advId", value)
                        }
                    }
                }
            }
            // player
            if (advancement.player != player.uuidAsString) {
                advancement.player = player.uuidAsString

                getCellByIndex(this.settings!!.advSheet.whoRange, advancement.index)?.let { cell: String ->
                    val value = ValueRange().setRange("${this.settings!!.advSheet.name}!$cell")
                        .setValues(listOf(listOf(getProfilePictureByUuid(player.uuidAsString))))

                    synchronized(this.updatePool) {
                        this.updatePool.put("apl: $advId", value)
                    }
                }
            }
        }
    }

    private fun executePendingUpdates() {
        if (this.state != State.INITIALIZED) {
            return
        }

        synchronized(this.updatePool) {
            if (this.updatePool.isEmpty()) {
                return
            }
        }

        // execute api call in a different thread
        this.executor.execute {
            val updates: MutableList<ValueRange> = mutableListOf()

            // transfer updates into local variable
            synchronized(this.updatePool) {
                val it = this.updatePool.values.iterator()
                while (it.hasNext()) {
                    updates.add(it.next())
                    it.remove()
                }
            }

            val body = BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setIncludeValuesInResponse(false)
                .setData(updates)
            try {
                this.sheetApi!!.spreadsheets().values().batchUpdate(this.settings!!.sheetId, body)
                        .execute()
            } catch (e: Exception) {
                if (e is GoogleJsonResponseException) {
                    val error = e.details

                    when (error.code) {
                        404 -> logger.error("Spreadsheet not found")
                        403 -> logger.error("I don't have permission for this sheet")
                    }
                }
                logger.error(e.message, e)
                server?.sendMessage(Text.of("Caught exception while making api request, mod automatically shuts down... run `/tracker reload` when you think you fixed your stuff"))
                this.state = State.NOT_INITIALIZED
            }
        }
    }

    /**
     * Reloads config. if returned true it loaded perfectly. if its false some errors happened
     */
    private fun reloadConfig(server: MinecraftServer): Boolean {
        try {
            val configFolder: Path = FabricLoader.getInstance().configDir.resolve(MOD_ID)
            val credPath = configFolder.resolve("credentials.json")

            if (!configFolder.toFile().isDirectory && !configFolder.toFile().mkdir()) {
                throw IOException(
                    "failed to create new directory: ${
                        configFolder.toAbsolutePath().normalize()
                    }"
                )
            }

            val settingsFolder = server.getSavePath(WorldSavePath.ROOT).resolve("tracker")
            val settingsFile = settingsFolder.resolve("settings.json")

            // read the email from json file
            var email: String? = credPath.toFile().reader().use { r ->
                val j: JsonObject = GSON.fromJson<JsonObject>(r, JsonObject::class.java)

                j.get("client_email").asString
            }

            this.sheetApi = Sheets.Builder(NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credPath.toFile().inputStream()
                    .use { stream -> GoogleCredentials.fromStream(stream) }
                    .createScoped(mutableSetOf<String?>(SheetsScopes.SPREADSHEETS)).createDelegated(email)))
                .setApplicationName(APP_NAME).build()


            if (!settingsFolder.toFile().isDirectory && !settingsFolder.toFile().mkdir()) {
                throw IOException(
                    "failed to create new directory: ${
                        settingsFolder.toAbsolutePath().normalize()
                    }"
                )
            }

            if (settingsFile.toFile().exists()) {
                this.settings = settingsFile.toFile().reader().use { r ->
                    GSON.fromJson(r, Settings::class.java)
                }
            } else {
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

            // load items
            val newItem: MutableMap<String, MutableMap<String, ItemData>> = mutableMapOf()

            var idRange: String = "${settings!!.itemSheet.name}!${settings!!.itemSheet.idRange}"
            val advIdRange: String = "${settings!!.itemSheet.name}!${settings!!.itemSheet.advRange}"

            val itemResult = this.sheetApi!!.spreadsheets().values()
                .batchGet(this.settings!!.sheetId).setRanges(listOf(idRange, advIdRange))
                .execute()

            var l: List<ValueRange> = itemResult.valueRanges

            val itemIds: List<String?> = l[0].getValues().map { it.firstOrNull()?.toString() }
            val itemAdvIds: List<String?> = l[1].getValues().map { it.firstOrNull()?.toString() }

            for (i in itemIds.indices) {
                if (itemIds[i] == null || itemAdvIds[i] == null) {
                    continue
                }

                // put new hash map for advancement id if it doesn't exist yet
                if (!newItem.containsKey(itemAdvIds[i]!!)) {
                    newItem.put(itemAdvIds[i]!!, mutableMapOf())
                }

                // put item in the map of advancement
                newItem[itemAdvIds[i]!!]!!.put(itemIds[i]!!, ItemData(index = i))
            }

            // loading advancements
            val newAdv: MutableMap<String, AdvancementData> = mutableMapOf()

            // download all tracked advancement ids
            idRange = "${settings!!.advSheet.name}!${settings!!.advSheet.idRange}"
            val advResult: List<String> =
                this.sheetApi!!.spreadsheets().values().get(this.settings!!.sheetId, idRange)
                    .execute()
                    .getValues()
                    .filter { it.isNotEmpty() }
                    .map { it.first().toString() }

            for (i in advResult.indices) {
                val advancementEntry = server.advancementLoader.get(Identifier.of(advResult[i]))

                if (advancementEntry == null) {
                    logger.warn("Couldn't find loaded advancement with name {}", advResult[i])
                    continue
                }

                newAdv.put(
                    advResult[i],
                    AdvancementData(
                        false,
                        null,
                        null,
                        i,
                        AdvancementData.Progress(0, advancementEntry.value().criteria().size)
                    )
                )
            }

            val advancementFolder = server.getSavePath(WorldSavePath.ADVANCEMENTS)

            advancementFolder.toFile().listFiles { file: File -> file.name.endsWith(".json") }?.forEach { advFile ->
                var j: JsonObject = advFile.reader().use { r ->
                    GSON.fromJson<JsonObject>(r, JsonObject::class.java)
                }

                for (advId in j.keySet()) {
                    if (!j[advId]!!.isJsonObject) continue

                    val advJ: JsonObject = j[advId].asJsonObject
                    val adv: AdvancementData = newAdv[advId] ?: continue

                    newItem[advId]?.let { itemAdvancement ->
                        advJ["criteria"].asJsonObject.keySet().forEach { criteria ->
                            itemAdvancement[criteria]?.done = true
                        }
                    }

                    if (!adv.done) {
                        if (advJ["done"]?.asBoolean == true) {
                            // find latest criterion obtained
                            var max: Instant? = null

                            advJ["criteria"].getAsJsonObject().asMap().forEach { criteria, time ->
                                val x =
                                    minecraftTimeFormatter.parse<Instant>(time.asString) { temporal: TemporalAccessor? ->
                                        Instant.from(temporal)
                                    }

                                if (max?.isBefore(x) != false) {
                                    max = x
                                }
                            }

                            var uuid: String = advFile.name
                            uuid = uuid.substring(0, uuid.length - 5) // remove last 5 letters ".json"

                            adv.done = true
                            adv.player = uuid
                            adv.doneTime = max

                            // set progress to 100%
                            adv.progress?.nom = adv.progress!!.den
                        } else {
                            adv.progress?.nom = advJ["criteria"].getAsJsonObject().size()
                        }
                    }
                }
            }

            this.advancementsMap = newAdv
            this.itemMap = newItem

            val newStat: MutableMap<String, StatData> = mutableMapOf()
            val newSb: MutableMap<String, ScoreboardData> = mutableMapOf()

            // making api request to the sheet to get all tracked stats and scoreboards, and their types
            idRange = "${settings!!.statSheet.name}!${settings!!.statSheet.idRange}"
            val typeRange: String = "${settings!!.statSheet.name}!${settings!!.statSheet.typeRange}"
            val comparingTypeRange: String =
                "${settings!!.statSheet.name}!${settings!!.statSheet.comparingTypeRange}"

            val result2 = this.sheetApi!!.spreadsheets().values()
                .batchGet(this.settings!!.sheetId)
                .setRanges(listOf(idRange, typeRange, comparingTypeRange)).execute()

            l = result2.valueRanges

            val statIds: List<String?> = l[0]!!.getValues().map { it.firstOrNull()?.toString() }
            val statTypes: List<String?> = l[1]!!.getValues().map { it.firstOrNull()?.toString() }
            val comps: List<String?> = l[2]!!.getValues().map { it.firstOrNull()?.toString() }

            for (i in statIds.indices) {
                val name: String = statIds[i] ?: continue
                val type: String = statTypes[i] ?: continue // "scoreboard" or "stat"
                val comp: String = comps[i] ?: continue

                if (type == "stat") {
                    val x: List<String> = name.split(".", limit = 2)
                        .dropWhile { it.isEmpty() }
                        .dropLastWhile { it.isEmpty() }

                    if (x.size < 2) {
                        logger.error(
                            "wrong format for stat ({}) in the sheet: must be category.name; e.g. \"custom.deaths\"; skipping",
                            name
                        )
                        continue
                    }

                    val statType = x[0]
                    val statObj = x[1]

                    var bestValue = 0
                    var bestPlayer: String? = null

                    val files: Array<File> = server.getSavePath(WorldSavePath.STATS).toFile()
                        .listFiles { f: File -> f.name.endsWith(".json") }

                    for (f: File in files) {
                        var j: JsonObject = f.reader().use { r ->
                            GSON.fromJson<JsonObject?>(r, JsonObject::class.java).get("stats")
                                .getAsJsonObject()
                        }
                        val value: Int =
                            j.get("minecraft:$statType")?.getAsJsonObject()?.get("minecraft:$statObj")?.asInt
                                ?: continue

                        if (comp == "max") {
                            if (value > bestValue) {
                                var uuid = f.name
                                uuid = uuid.substring(0, uuid.length - 5) // remove last 5 letters ".json"

                                bestValue = value
                                bestPlayer = uuid
                            }
                        } else if (comp == "sum") {
                            bestValue += value
                        } else {
                            logger.warn("Incorrect comparison type for stat \"{}\": \"{}\"", name, comp)
                        }
                    }

                    newStat.put(
                        name, StatData(bestPlayer, comp, bestValue, i)
                    )
                } else if (type == "scoreboard") {
                    val sb = server.scoreboard

                    val objective = sb.getNullableObjective(name)

                    if (objective == null) {
                        logger.warn("non-existent scoreboard objective: {}", name)
                        continue
                    }

                    var bestValue = 0
                    var bestPlayer: String? = null

                    for (entry in sb.getScoreboardEntries(objective)) {
                        val value = entry.value()

                        if (comp == "max") {
                            if (value > bestValue) {
                                bestValue = value
                                bestPlayer = getUuid(entry.owner())
                            }
                        } else if (comp == "sum") {
                            bestValue += value
                        } else {
                            logger.warn("Incorrect comparison type for scoreboard \"{}\": \"{}\"", name, comp)
                        }
                    }

                    newSb.put(name, ScoreboardData(comp, bestValue, bestPlayer, i))
                } else {
                    // what are we doing here???
                    logger.warn("Incorrect type \"{}\" for stat \"{}\"", type, name)
                }

            }
            this.scoreboardMap = newSb
            this.statMap = newStat

            // api call to update everything
            val valueRanges: MutableList<ValueRange> = mutableListOf(
                // advancement completion
                ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.statusRange}")
                    .setValues(advResult.map { i -> listOf(this.advancementsMap?.get(i)?.done) }),
                // advancement progress
                ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.progressRange}")
                    .setValues(advResult.map { i -> listOf(this.advancementsMap?.get(i)?.progress?.toString()) }),
                // advancement completion time
                ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.whenRange}")
                    .setValues(advResult.map { i -> listOf(this.advancementsMap?.get(i)?.doneTime?.let(timeFormatter::format)) }),
                // player with best advancement
                ValueRange().setRange("${settings!!.advSheet.name}!${settings!!.advSheet.whoRange}")
                    .setValues(advResult.map { i ->
                        listOf(
                            getProfilePictureByUuid(
                                this.advancementsMap?.get(
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
                    .setValues(statIds.map { i -> listOf(getProfilePictureByUuid(this.statMap?.get(i)?.player)) }),

                // items
                // status
                ValueRange().setRange("${settings!!.itemSheet.name}!${settings!!.itemSheet.statusRange}")
                    .setValues(itemIds.mapIndexed { i, itemId ->
                        listOf(
                            this.itemMap?.get(itemAdvIds[i])?.get(itemId)?.done
                        )
                    })
            )

            val body = BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setIncludeValuesInResponse(false)
                .setData(valueRanges)
            this.sheetApi!!.spreadsheets().values()
                .batchUpdate(this.settings!!.sheetId, body).execute()

            this.state = State.INITIALIZED
            return true
        } catch (e: Exception) {
            logger.error(e.message, e)

            this.state = State.NOT_INITIALIZED
            return false
        }
    }


    private enum class State {
        NOT_INITIALIZED, INITIALIZED /* when tracker caught an exception and can't continue */
    }

    companion object {
        const val MOD_ID: String = "bac-tracker-mod"
        const val APP_NAME: String = "BACAP Tracker"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        val GSON: Gson = Gson().newBuilder().setPrettyPrinting().create()

        val minecraftTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)
                .withZone(ZoneId.systemDefault())

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a dd-MM-yyyy z Z").withLocale(Locale.of("en-US"))
            .withZone(ZoneId.systemDefault())

        val getStatRegex = Pattern.compile("minecraft\\.(?<type>.+):minecraft.(?<name>.+)")

        /**
         * @param player uuid of the player
         * @return url of profile picture as excel =IMAGE(stuff)
         */
        fun getProfilePictureByUuid(player: String?): String? {
            if (player == null) return null

            return "=IMAGE(\"https://crafatar.com/avatars/$player?size=16&overlay\")"
        }

        /**
         * @param cell  cell range like "A1:A" (regex for cell must be \D+\d*:\D+)
         * @param index index, like 2
         * @return cell range, like A3:A, or empty optional if cell doesn't match regex
         */
        fun getCellByIndex(cell: String, index: Int): String? {
            val r = Pattern.compile("(?<sL>\\D+)(?<sN>\\d*):(?<eL>\\D+)", Pattern.CASE_INSENSITIVE)

            val m = r.matcher(cell)
            if (!m.find()) {
                return null
            }

            val startLetter: String = m.group("sL") ?: return null
            val endLetter: String = m.group("eL") ?: return null
            var startNumber: Int = m.group("sN").toIntOrNull() ?: return null

            return "$startLetter${startNumber + index}:$endLetter"
        }

        fun getUuid(playerIgn: String?): String? {
            try {
                val req =
                    HttpRequest.newBuilder().uri(URI("https://api.mojang.com/users/profiles/minecraft/$playerIgn"))
                        .GET().build()
                val client = HttpClient.newBuilder().build()
                val response: HttpResponse<InputStream> =
                    client.send(req, HttpResponse.BodyHandlers.ofInputStream())

                when (response.statusCode()) {
                    200 -> {
                        response.body().reader().use { r ->
                            val j: JsonObject = GSON.fromJson(r, JsonObject::class.java) ?: return null
                            return j["id"].asString
                        }
                    }

                    404 -> logger.error("Player $playerIgn does not exist")
                }
            } catch (e: IOException) {
                logger.error("Error trying to get uuid of player $playerIgn", e)
            }
            return null
        }
    }
}