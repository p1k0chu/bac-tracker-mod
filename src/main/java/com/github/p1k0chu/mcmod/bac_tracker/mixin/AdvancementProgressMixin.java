package com.github.p1k0chu.mcmod.bac_tracker.mixin;

import com.github.p1k0chu.mcmod.bac_tracker.data.AdvancementData;
import com.github.p1k0chu.mcmod.bac_tracker.utils.AdvancementProgressGetter;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.CriterionProgress;

@Mixin(AdvancementProgress.class)
public abstract class AdvancementProgressMixin implements AdvancementProgressGetter {
    @Shadow
    protected abstract int countCompletedRequirements();

    @Shadow
    private AdvancementRequirements requirements;

    @Final
    @Shadow
    private Map<String, CriterionProgress> criteria;

    @Override
    public Instant bac_tracker_mod$getLatestProgressObtainDate() {
        return this.criteria.values().stream().map(CriterionProgress::getObtained).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public AdvancementData.@NotNull Progress bac_tracker_mod$getProgress() {
        return new AdvancementData.Progress(this.countCompletedRequirements(), this.requirements.size());
    }
}
