package com.github.p1k0chu.mcmod.bac_tracker.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;

public interface StatUpdatedCallback {
    Event<StatUpdatedCallback> EVENT = EventFactory.createArrayBacked(StatUpdatedCallback.class,
            (listeners) -> (player, stat, oldValue, newValue) -> {
                for (StatUpdatedCallback listener : listeners) {
                    listener.interact(player, stat, oldValue, newValue);
                }
            });

    void interact(Player player, Stat<?> stat, int oldValue, int newValue);
}
