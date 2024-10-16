package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.StatUpdatedCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatHandler.class)
public abstract class StatHandlerMixin {
    @Inject(method = "setStat", at = @At("HEAD"))
    void setStat(PlayerEntity player, Stat<?> stat, int value, CallbackInfo ci) {
        StatHandler obj = (StatHandler) (Object) this;

        StatUpdatedCallback.EVENT.invoker().interact(player, stat, obj.getStat(stat), value);
    }
}
