/* Written by p1k0chu
 * Github: https://github.com/p1k0chu
 *
 * Feel free to open issues on issue tracker (just don't spam with bs)
 */

package com.github.p1k0chu

import com.github.p1k0chu.config.Settings
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.command.CommandManager
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import java.nio.file.Path
import kotlin.io.path.*


/**
 * Entry point of bac-tracker mod
 */
fun init() {
    var advRefreshDelay: Int = Int.MAX_VALUE
    var settings: Settings? = null
    var sheetManager: SheetManager? = null


    if (!Utils.CONFIG_DIR.exists())
        Utils.CONFIG_DIR.createDirectory()

    if (!Utils.CREDENTIALS_FILE.isRegularFile()) {
        Utils.logger.error("No credentials found! Put them at ${Utils.CREDENTIALS_FILE.absolute().normalize()}")
        Utils.logger.error("You can run `/tracker reload` to reload credentials")
    }


    ServerLifecycleEvents.SERVER_STARTED.register { server ->
        val cf: Path = server.getSavePath(WorldSavePath.ROOT)
            .resolve("tracker").also {
                if (!it.exists()) it.createDirectory()
            }.resolve("settings.json")

        settings = if (cf.exists()) Utils.parseJsonHandlingErrors<Settings>(cf).getOrNull() ?: Settings()
        else
            Settings()

        require(settings!!.refreshTicks >= 600) { "Refresh time must be longer than 30s" }

        if (settings!!.sheetId.isBlank()) {
            Utils.logger.error(
                "Spread sheet link is missing from settings.json! " +
                        "Please edit ${cf.absolute().normalize()}"
            )
            cf.writer().use { writer ->
                Utils.GSON.toJson(settings, writer)
            }
            Utils.logger.info("You can run `/tracker reload` to reload settings")

            settings = null
            sheetManager = null
        } else {
            sheetManager = if(Utils.CREDENTIALS_FILE.exists()) settings?.let {
                SheetManager(it, Utils.CREDENTIALS_FILE)
            } else null

            advRefreshDelay = if (sheetManager != null) {
                20
            } else {
                Int.MAX_VALUE
            }
        }
    }
    ServerLifecycleEvents.SERVER_STOPPING.register {
        sheetManager?.job?.join()
        // just QoL, if user has old config this will add any missing entries
        it.getSavePath(WorldSavePath.ROOT)
            .resolve("tracker")
            .resolve("settings.json")
            .writer().use { writer ->
                Utils.GSON.toJson(settings, writer)
            }

    }
    ServerTickEvents.END_SERVER_TICK.register { server ->
        sheetManager?.let {
            if(--advRefreshDelay <= 0) {
                it.update(server)
                advRefreshDelay = settings?.refreshTicks ?: 6000
            }
        }
    }

    CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
        dispatcher.register(
            CommandManager.literal("tracker")
                .requires { source -> source.hasPermissionLevel(4) || source.server.isSingleplayer }
                .then(CommandManager.literal("update") /* sub command to update your sheets */
                    .executes { context ->
                        if (sheetManager != null) {
                            context.source.sendFeedback({ Text.literal("Updating sheets...") }, true)

                            sheetManager?.let {
                                it.update(context.source.server) {
                                    context.source.sendFeedback({ Text.literal("Sheets updated") }, true)
                                }
                                advRefreshDelay = settings?.refreshTicks ?: 6000
                            }

                            1
                        } else {
                            context.source.sendFeedback({ Text.literal("SheetManager is not initialized") }, false)
                            0
                        }
                    }).then(CommandManager.literal("reload") /* sub command to reload settings and credentials */
                    .executes { context ->
                        val p = context.source.server.getSavePath(WorldSavePath.ROOT)
                            .resolve("tracker").resolve("settings.json")

                        settings = if (p.exists()) {
                            Utils.parseJsonHandlingErrors<Settings>(p).getOrNull() ?: Settings()
                        } else {
                            Settings()
                        }

                        sheetManager =
                            if (Utils.CREDENTIALS_FILE.exists() && settings?.sheetId?.isNotBlank() == true)
                                SheetManager(settings!!, Utils.CREDENTIALS_FILE)
                            else null

                        if (sheetManager != null) {
                            context.source.sendFeedback({
                                Text.literal("Settings and credentials reloaded")
                            }, true)

                            sheetManager?.let {
                                it.update(context.source.server) {
                                    context.source.sendFeedback({ Text.literal("Sheets updated") }, true)
                                }
                                sheetManager?.job?.join()
                                advRefreshDelay = settings?.refreshTicks ?: 6000
                            }

                            1
                        } else {
                            context.source.sendFeedback({
                                Text.literal("Failed to reload. Credentials OR sheet id are missing")
                            }, true)
                            0
                        }
                    })
        )
    }

    Utils.logger.info("${Utils.MOD_ID} is initialized. Remember not to share your mc folder with anyone if it has your api credentials!")
}