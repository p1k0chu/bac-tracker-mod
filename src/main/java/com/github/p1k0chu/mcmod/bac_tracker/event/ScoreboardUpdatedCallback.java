package com.github.p1k0chu.mcmod.bac_tracker.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;

public interface ScoreboardUpdatedCallback {
    Event<ScoreboardUpdatedCallback> SCORE_UPDATED = EventFactory.createArrayBacked(ScoreboardUpdatedCallback.class,
            (listeners) -> (owner, objective, score) -> {
                for (ScoreboardUpdatedCallback listener : listeners) {
                    listener.interact(owner, objective, score);
                }
            });

    /**
     * @param owner must be uuid of a player in string form or null
     */
    void interact(String owner, ScoreboardObjective objective, ScoreboardScore score);
}
