package com.github.p1k0chu.sheet

import com.google.api.services.sheets.v4.model.ValueRange
import net.minecraft.server.MinecraftServer


interface Sheet {
    fun update(server: MinecraftServer): List<ValueRange>
}