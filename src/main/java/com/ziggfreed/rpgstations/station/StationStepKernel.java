package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;

import com.ziggfreed.common.cast.step.CastKernel;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The ONE production {@code CastKernel} instance every station step program - implicit or
 * authored - walks through (design section 9.3's "one engine, no dual path"). Thin static holder
 * over {@link StationStepRegistry#production()} + {@link StationStepSemantics#INSTANCE}.
 */
final class StationStepKernel {

    private static final CastKernel<StationStepContext, StationStep, String, StationStepResult> KERNEL =
            new CastKernel<>(StationStepRegistry.production(), StationStepSemantics.INSTANCE);

    private StationStepKernel() {
    }

    /**
     * Walk {@code ctx}'s program starting at {@code startIndex} (0 for a fresh cycle attempt, or
     * {@code session.programIndex} to resume a suspended one - see
     * {@code CastKernel#runResumable}'s binding resume contract).
     */
    @Nonnull
    static CastKernel.Walk<StationStepResult> runResumable(@Nonnull StationStepContext ctx, int startIndex) {
        return KERNEL.runResumable(ctx, startIndex);
    }
}
