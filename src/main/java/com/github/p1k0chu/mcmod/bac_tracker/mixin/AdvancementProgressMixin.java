package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.data.AdvancementData;
import com.github.p1k0chu.mcmod.bac_tracker.utils.AdvancementProgressGetter;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.criterion.CriterionProgress;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

@Mixin(AdvancementProgress.class)
public abstract class AdvancementProgressMixin implements AdvancementProgressGetter {
    @Shadow
    protected abstract int countObtainedRequirements();

    @Shadow
    private AdvancementRequirements requirements;

    @Final
    @Shadow
    private Map<String, CriterionProgress> criteriaProgresses;

    @Override
    public Instant bac_tracker_mod$getLatestProgressObtainDate() {
        return this.criteriaProgresses.values().stream().map(CriterionProgress::getObtainedTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public AdvancementData.@NotNull Progress bac_tracker_mod$getProgress() {
        return new AdvancementData.Progress(this.countObtainedRequirements(), this.requirements.getLength());
    }
}
