package com.github.p1k0chu.mcmod.bac_tracker

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.DefaultPermissions
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Colors
import java.net.URI

object TrackerCommand {
    @Suppress("UNUSED_PARAMETER")
    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: RegistrationEnvironment
    ) {
        dispatcher.register(CommandManager.literal("tracker")
            .then(CommandManager.literal("reload")
                .requires { source: ServerCommandSource -> source.permissions.hasPermission(DefaultPermissions.ADMINS) || source.server.isHost(source.player?.playerConfigEntry) }
                .executes(::reloadCommand))
            .then(CommandManager.literal("sheet")
                .executes(::sheetCommand))
        )
    }

    fun sheetCommand(context: CommandContext<ServerCommandSource?>): Int {
        if (Main.state != Main.State.INITIALIZED) {
            context.source?.sendError(Text.of("Tracker is not initialized"))
            return 1
        }
        val url = "https://docs.google.com/spreadsheets/d/${Main.settings?.sheetId ?: return 1}/edit"

        context.source?.sendFeedback({
            Text.literal(url).styled { style: Style? ->
                style?.withClickEvent(ClickEvent.OpenUrl(URI.create(url)))?.withHoverEvent(
                    HoverEvent.ShowText(Text.of("Click to open url"))
                )?.withItalic(true)?.withUnderline(true)?.withColor(Colors.LIGHT_GRAY)
            }
        }, false)
        return 0
    }

    fun reloadCommand(context: CommandContext<ServerCommandSource?>): Int {
        Main.submitTask {
            if (Main.reloadConfigAndData()) {
                context.source?.sendFeedback({ Text.of("Successful reload") }, true)
            } else {
                context.source?.sendFeedback({ Text.of("Errors happened when reloading, check logs") }, true)
            }
        }
        return 0
    }
}