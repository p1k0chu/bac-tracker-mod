package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.StatUpdatedCallback;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatsCounter.class)
public abstract class StatHandlerMixin {
    @Inject(method = "setValue", at = @At("HEAD"))
    void setStat(Player player, Stat<?> stat, int value, CallbackInfo ci) {
        StatsCounter obj = (StatsCounter) (Object) this;

        StatUpdatedCallback.EVENT.invoker().interact(player, stat, obj.getValue(stat), value);
    }
}
