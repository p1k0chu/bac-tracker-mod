package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.ScoreboardUpdatedCallback;
import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.UserCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ServerScoreboard.class)
public class ServerScoreboardMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(at = @At("HEAD"), method = "updateScore")
    void updateScore(ScoreHolder scoreHolder, ScoreboardObjective objective, ScoreboardScore score, CallbackInfo ci) {
        UserCache cache = server.getUserCache();
        if(cache == null) return;

        Optional<GameProfile> profile = cache.findByName(scoreHolder.getNameForScoreboard());

        profile.ifPresent(gameProfile -> ScoreboardUpdatedCallback.SCORE_UPDATED.invoker().interact(gameProfile.getId().toString(), objective, score));
    }
}