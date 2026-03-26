package com.github.p1k0chu.mcmod.bac_tracker

import com.github.p1k0chu.mcmod.bac_tracker.Main.logger
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.permissions.Permissions
import net.minecraft.util.CommonColors
import java.net.URI

object TrackerCommand {
    @Suppress("UNUSED_PARAMETER")
    fun register(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        buildContext: CommandBuildContext,
        selection: Commands.CommandSelection
    ) {
        dispatcher.register(
            Commands.literal("tracker")
                .then(
                    Commands.literal("reload")
                        .requires { source: CommandSourceStack ->
                            source.permissions().hasPermission(Permissions.COMMANDS_ADMIN)
                                    || source.player?.let { source.server.isSingleplayerOwner(it.nameAndId()) } == true
                        }
                        .executes(::reloadCommand))
                .then(
                    Commands.literal("sheet")
                        .executes(::sheetCommand)
                )
        )
    }

    fun sheetCommand(context: CommandContext<CommandSourceStack>): Int {
        if (Main.state != Main.State.INITIALIZED) {
            context.source.sendFailure(Component.literal("Tracker is not initialized"))
            return 1
        }
        val url = "https://docs.google.com/spreadsheets/d/${Main.settings?.sheetId ?: return 1}/edit"

        context.source.sendSuccess({
            Component.literal(url).withStyle { style: Style ->
                style.withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Click to open url")))
                    .withItalic(true)
                    .withUnderlined(true)
                    .withColor(CommonColors.LIGHT_GRAY)
            }
        }, false)
        return 0
    }

    fun reloadCommand(context: CommandContext<CommandSourceStack>): Int {
        context.source.sendSuccess({ Component.literal("Reloading...") }, true)

        Main.submitTask {
            try {
                Main.reloadConfigAndData()
                context.source?.sendSuccess({ Component.literal("Successful reload") }, true)
            } catch (e: Throwable) {
                context.source.sendSuccess(
                    { Component.literal("Failed to reload: ${e::class.simpleName}: ${e.message}") },
                    true
                )
                logger.error("Failed to reload", e)
            }
        }
        return 0
    }
}
