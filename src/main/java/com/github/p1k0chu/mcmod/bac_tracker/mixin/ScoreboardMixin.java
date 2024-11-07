package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.ScoreboardUpdatedCallback;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.scoreboard.Scoreboard$1")
public abstract class ScoreboardMixin {
    @Shadow(aliases = "field_47547") @Final
    ScoreHolder scoreHolder;

    @Shadow(aliases = "field_47546") @Final
    ScoreboardObjective objective;

    @Shadow public abstract int getScore();

    @Inject(method = "setScore(I)V", at = @At("HEAD"))
    void setScore(int score, CallbackInfo ci) {
        ScoreboardUpdatedCallback.SCORE_UPDATED.invoker().interact(scoreHolder.getNameForScoreboard(), objective, getScore(), score);
    }
}
// mixin into anonymous classes is so awful
// this is a cry for help