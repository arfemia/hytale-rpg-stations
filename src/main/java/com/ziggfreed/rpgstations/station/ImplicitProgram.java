package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
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
 * pick, the action's effective {@link LootRef}, and the resolved action's {@code Moments.Cycle}
 * {@link Presentation}) so it is unit-testable without a live server.
 */
final class ImplicitProgram {

    static final String ID_WORK = "work";

    private ImplicitProgram() {
    }

    /**
     * Build the single-step implicit program (a one-element list, so the dispatch choke point sees
     * the SAME {@code List<StationStep>} shape an authored program yields). {@code bonus} is the
     * action's effective {@code Bonus} group - the ref itself, referenced tables and inline rolls
     * alike - handed to the {@code Roll} phase exactly as an AUTHORED step's own {@code Roll} ref
     * is, so ONE resolution serves both origins and a referenced table's pool reaches this route
     * too.
     */
    @Nonnull
    static List<StationStep> build(@Nonnull StationStep.Consume consume, @Nonnull StationStep.Produce produce,
            @Nullable LootRef bonus, @Nullable Presentation cyclePresentation) {
        StationStep step = StationStep.of(ID_WORK)
                .withConsume(consume)
                .withProduce(produce)
                .withRoll(bonus)
                .withPresentation(cyclePresentation);
        return List.of(step);
    }
}
