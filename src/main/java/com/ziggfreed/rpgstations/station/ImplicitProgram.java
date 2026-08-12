package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The PURE builder for the "classic convert loop" implicit program (scope-2 design 2.1): an action
 * with no authored {@code Steps} runs ONE orthogonal-phase step composing {@code Consume} +
 * {@code Produce} + {@code Roll} + {@code Presentation}. This COLLAPSES the pre-scope-2 four-step
 * {@code [Consume, Produce, Roll, Present]} array onto a single step - byte-equivalent behavior
 * (the phases execute in the SAME order the composite handler walks: {@code Consume} -&gt;
 * {@code Produce} -&gt; {@code Roll} -&gt; {@code Presentation}), a simpler anchor for the phase
 * model. An action that authors no {@code Steps} resolves to this SAME shape from its own
 * {@code Recipe}, so {@code StationService#runRealCycle} runs through ONE engine (the
 * {@code station.step} kernel) whether an action authors a step program or not - "one engine, no
 * dual path".
 *
 * <p>Zero engine/store touch - takes only already-resolved value objects
 * ({@link StationStep.Consume}/{@link StationStep.Produce} built from a live {@code ConversionCheck}
 * pick, an already-{@code LootEngine.resolveRolls}-resolved {@link Roll} array wrapped into a
 * {@link LootRef}, and the resolved action's {@code Moments.Cycle} {@link Presentation}) so it is
 * unit-testable without a live server.
 */
final class ImplicitProgram {

    static final String ID_WORK = "work";

    private ImplicitProgram() {
    }

    /**
     * Build the single-step implicit program (a one-element list, so the dispatch choke point sees
     * the SAME {@code List<StationStep>} shape an authored program yields). {@code resolvedRolls}
     * is the action's {@code Bonus} group ALREADY resolved through {@code loot.LootEngine#resolveRolls}
     * (Lootables + inline Rolls concatenated) - the {@code Roll} phase never re-resolves a
     * {@code LootRef.Lootables} reference, it only evaluates the inline array it is handed (mirroring
     * how an AUTHORED step's own inline {@code Rolls} works, so one Roll phase serves both origins).
     */
    @Nonnull
    static List<StationStep> build(@Nonnull StationStep.Consume consume, @Nonnull StationStep.Produce produce,
            @Nonnull Roll[] resolvedRolls, @Nullable Presentation cyclePresentation) {
        StationStep step = StationStep.of(ID_WORK)
                .withConsume(consume)
                .withProduce(produce)
                .withRoll(LootRef.of(null, resolvedRolls))
                .withPresentation(cyclePresentation);
        return List.of(step);
    }
}
