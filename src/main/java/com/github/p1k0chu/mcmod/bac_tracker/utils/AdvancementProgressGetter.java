package com.github.p1k0chu.mcmod.bac_tracker.utils;

import com.github.p1k0chu.mcmod.bac_tracker.data.AdvancementData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Instant;

public interface AdvancementProgressGetter {
    default @Nullable Instant bac_tracker_mod$getLatestProgressObtainDate() {
        return null;
    }

    @NotNull AdvancementData.Progress bac_tracker_mod$getProgress();
}
