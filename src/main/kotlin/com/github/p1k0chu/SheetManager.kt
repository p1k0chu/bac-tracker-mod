package com.github.p1k0chu

import com.github.p1k0chu.config.Settings
import com.github.p1k0chu.sheet.AdvancementSheet
import com.github.p1k0chu.sheet.ItemSheet
import com.github.p1k0chu.sheet.StatSheet
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonObject
import kotlinx.io.IOException
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.reader
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class SheetManager(
    private val settings: Settings,
    credPath: Path
) {
    companion object {
        private const val APP_NAME: String = "BACAP Google Sheet Tracker"
    }

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val service: Sheets = Sheets.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance(), HttpCredentialsAdapter(
            credPath.toFile().inputStream().use { stream -> GoogleCredentials.fromStream(stream) }
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS))
                .createDelegated(
                    credPath.reader().use { reader ->
                        Utils.GSON.fromJson(
                            reader,
                            JsonObject::class.java
                        )
                    }["client_email"].asString
                )
        )
    )
        .setApplicationName(APP_NAME)
        .build()


    // I just create these here, so I can reuse them everytime I need them
    private val advColumnCache: List<String> by lazy {
        service.spreadsheets().values()
            .get(settings.sheetId, "${settings.advSheet.name}!${settings.advSheet.idRange}")
            .execute()
            .getValues()
            .filter {
                it.isNotEmpty()
            }
            .map {
                it.first().toString()
            }
    }
    private val itemIdCache: List<String> by lazy {
        service.spreadsheets().values()
            .get(settings.sheetId, "${settings.itemSheet.name}!${settings.itemSheet.idRange}")
            .execute()
            .getValues()
            .filter {
                it.isNotEmpty()
            }
            .map {
                it.first().toString()
            }
    }
    private val statColumnCache: List<String> by lazy {
        service.spreadsheets().values()
            .get(settings.sheetId, "${settings.statSheet.name}!${settings.statSheet.idRange}")
            .execute()
            .getValues()
            .filter {
                it.isNotEmpty()
            }
            .map {
                it.first().toString()
            }
    }

    fun updateAll(
        server: MinecraftServer,
        onDone: (() -> (Unit)) = {}
    ) {
        executor.execute {
            Utils.logger.debug("Thread for updates started")

            val advSheet = AdvancementSheet(settings, advColumnCache)
            val itemSheet = ItemSheet(settings, itemIdCache)
            val statSheet = StatSheet(settings, statColumnCache)

            server.getSavePath(WorldSavePath.ADVANCEMENTS).let {
                if (it.exists()) {
                    it.listDirectoryEntries("*.json").forEach { path ->
                        Utils.parseJsonHandlingErrors<JsonObject>(path).getOrNull()?.let { json ->
                            advSheet.pushPlayer(path.nameWithoutExtension, json)
                            itemSheet.pushPlayer(path.nameWithoutExtension, json)
                        }
                    }
                }
            }

            server.getSavePath(WorldSavePath.STATS).let {
                if(it.exists()) {
                    it.listDirectoryEntries("*.json").forEach { path: Path ->
                        Utils.parseJsonHandlingErrors<JsonObject>(path).getOrNull()?.let { json ->
                            statSheet.pushPlayer(path.nameWithoutExtension, json)
                        }
                    }
                }
            }

            statSheet.updateScoreboard(server)


            val values: List<ValueRange> = listOf(
                statSheet.getValueRange(),
                advSheet.getValueRange(),
                itemSheet.getValueRange()
            ).flatten()
                .plusElement(
                    ValueRange()
                        .setRange(settings.timerCell)
                        .setValues(
                            listOf(
                                listOf(
                                    (server.overworld.time / 20).toDuration(DurationUnit.SECONDS).toString()
                                )
                            )
                        )
                )

            try {
                val body: BatchUpdateValuesRequest = BatchUpdateValuesRequest()
                    .setValueInputOption("USER_ENTERED")
                    .setIncludeValuesInResponse(false)
                    .setData(values)
                service.spreadsheets().values()
                    .batchUpdate(settings.sheetId, body)
                    .execute()
                    .also {
                        Utils.logger.info("Updated ${it.totalUpdatedCells} cells")
                    }
            } catch (e: Exception) {
                when (e) {
                    is GoogleJsonResponseException, is IOException -> {
                        // io exception might be network issue
                        Utils.logger.error(e.message, e)
                    }

                    else -> throw e
                }
            }

            onDone()
        }
    }
}