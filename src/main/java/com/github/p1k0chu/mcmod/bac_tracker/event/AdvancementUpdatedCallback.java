package com.github.p1k0chu.mcmod.bac_tracker.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

public interface AdvancementUpdatedCallback {
    Event<AdvancementUpdatedCallback> CRITERION_CHANGED = EventFactory.createArrayBacked(AdvancementUpdatedCallback.class,
            (listeners) -> (player, advancementEntry, criterionName, newValue) -> {
                for (AdvancementUpdatedCallback listener : listeners) {
                    listener.interact(player, advancementEntry, criterionName, newValue);
                }
            });

    void interact(ServerPlayer player, AdvancementHolder advancementEntry, String criteria, boolean newValue);
}
