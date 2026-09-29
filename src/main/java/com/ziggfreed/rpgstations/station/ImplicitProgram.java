package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The PURE builder for the "classic convert loop" implicit program: an action with no authored
 * {@code Steps} runs ONE orthogonal-phase step composing {@code Convert} + {@code Roll} +
 * {@code Presentation}. The phases execute in the composite handler's fixed order ({@code Convert}
 * -&gt; {@code Roll} -&gt; {@code Presentation}), and the {@code Convert} phase is the SAME phase an
 * authored program's {@code Convert} beat runs - selection, yield, the yield breakdown, the cycle
 * output, the consume, the produce and the input hook all live in that one phase, so a conversion
 * has one code path whichever program shape ran it ("one engine, no dual path"). What the classic
 * loop still does BEFORE dispatch is choose the row: its pre-dispatch selection drives idle
 * practice, the out-of-inputs and inventory-full stops, the per-conversion pace and the
 * feedable-cycles count, and the chosen row rides the dispatch as the phase's preselected check.
 *
 * <p>Zero engine/store touch - takes only already-resolved value objects (the action's effective
 * {@link LootRef} and the resolved action's {@code Moments.Cycle} {@link Presentation}), so it is
 * unit-testable without a live server.
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
    static List<StationStep> build(@Nullable LootRef bonus, @Nullable Presentation cyclePresentation) {
        StationStep step = StationStep.of(ID_WORK)
                .withConvert(StationStep.Convert.on())
                .withRoll(bonus)
                .withPresentation(cyclePresentation);
        return List.of(step);
    }
}
