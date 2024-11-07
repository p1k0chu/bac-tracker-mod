package com.github.p1k0chu.mcmod.bac_tracker.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public interface AdvancementUpdatedCallback {
    Event<AdvancementUpdatedCallback> CRITERION_CHANGED = EventFactory.createArrayBacked(AdvancementUpdatedCallback.class,
            (listeners) -> (player, advancementEntry, criterionName, newValue) -> {
                for (AdvancementUpdatedCallback listener : listeners) {
                    listener.interact(player, advancementEntry, criterionName, newValue);
                }
            });

    void interact(ServerPlayerEntity player, AdvancementEntry advancementEntry, String criteria, boolean newValue);
}
