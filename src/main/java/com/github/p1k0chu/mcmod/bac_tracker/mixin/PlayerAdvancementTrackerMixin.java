package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.event.AdvancementUpdatedCallback;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow
    private ServerPlayerEntity owner;

    @Inject(at = @At("RETURN"), method = "revokeCriterion")
    private void revokeCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        AdvancementUpdatedCallback.CRITERION_CHANGED.invoker().interact(owner, advancement, criterionName, false);
    }

    @Inject(at = @At("RETURN"), method = "grantCriterion")
    private void grantCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        AdvancementUpdatedCallback.CRITERION_CHANGED.invoker().interact(owner, advancement, criterionName, true);
    }
}
