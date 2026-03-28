package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.ScoreboardUpdatedCallback;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.scores.Scoreboard$1")
public abstract class ScoreboardMixin {
    @Shadow
    @Final
    ScoreHolder val$scoreHolder;

    @Shadow
    @Final
    Objective val$objective;

    @Shadow
    public abstract int get();

    @Shadow(aliases = "field_47548")
    @Final
    Scoreboard this$0;

    @Inject(method = "set(I)V", at = @At("HEAD"))
    void setScore(int score, CallbackInfo ci) {
        if (this$0 instanceof ServerScoreboard) {
            ScoreboardUpdatedCallback.SCORE_UPDATED.invoker().interact(val$scoreHolder.getScoreboardName(), val$objective, get(), score);
        }
    }
}
