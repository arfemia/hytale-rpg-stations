package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The PURE builder for the "classic convert loop" implicit program (design section 9.3): an
 * action with no authored {@code Steps} runs {@code [Consume(inputs), Produce(outputs),
 * Roll(loot), Present(cycle)]} - EXACTLY these four steps, in this order, every time. This is the
 * regression anchor's byte-stable claim made concrete and testable: the shipped sawmill (no
 * {@code Actions} map authored at all) resolves to this SAME four-step shape via
 * {@code ActionResolver}'s implicit {@code "work"} action, so {@code StationService#runRealCycle}
 * runs through ONE engine (the {@code station.step} kernel) regardless of whether a station
 * authors a step program or not - "one engine, no dual path" (design 9.3).
 *
 * <p>Zero engine/store touch - takes only already-resolved value objects
 * ({@link StationStep.Consume}/{@link StationStep.Produce} built from a live
 * {@code ConversionCheck} pick, an already-{@code LootEngine.resolveRolls}-resolved {@link Roll}
 * array, and the resolved action's cycle {@link Presentation}) so it is unit-testable without a
 * live server.
 */
final class ImplicitProgram {

    static final String ID_CONSUME = "consume";
    static final String ID_PRODUCE = "produce";
    static final String ID_ROLL = "roll";
    static final String ID_PRESENT = "present";

    private ImplicitProgram() {
    }

    /**
     * Build the four-step program. {@code resolvedRolls} is the action's {@code Loot} group
     * ALREADY resolved through {@code loot.LootEngine#resolveRolls} (Tables + inline Rolls
     * concatenated) - the Roll step itself never re-resolves a {@code Loot.Tables} reference, it
     * only evaluates the array it is handed (mirroring how an AUTHORED {@code Roll} step's own
     * inline {@code Rolls} works, so one Roll-step handler serves both origins).
     */
    @Nonnull
    static List<StationStep> build(@Nonnull StationStep.Consume consume, @Nonnull StationStep.Produce produce,
            @Nonnull Roll[] resolvedRolls, @Nullable Presentation cyclePresentation) {
        List<StationStep> steps = new ArrayList<>(4);
        steps.add(StationStep.of(ID_CONSUME, StationStep.TYPE_CONSUME).withConsume(consume));
        steps.add(StationStep.of(ID_PRODUCE, StationStep.TYPE_PRODUCE).withProduce(produce));
        steps.add(StationStep.of(ID_ROLL, StationStep.TYPE_ROLL)
                .withRoll(StationStep.RollGroup.of(null, resolvedRolls)));
        steps.add(StationStep.of(ID_PRESENT, StationStep.TYPE_PRESENT).withPresentation(cyclePresentation));
        return steps;
    }
}
