package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.AdvancementUpdatedCallback;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(at = @At("RETURN"), method = "revoke")
    private void revokeCriterion(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        AdvancementUpdatedCallback.CRITERION_CHANGED.invoker().interact(player, advancement, criterionName, false);
    }

    @Inject(at = @At("RETURN"), method = "award")
    private void grantCriterion(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        AdvancementUpdatedCallback.CRITERION_CHANGED.invoker().interact(player, advancement, criterionName, true);
    }
}
