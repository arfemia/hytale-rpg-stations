package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The implicit-program equivalence test the design's byte-stable-regression claim rests on
 * (design section 9.3/10 leg B): "the classic convert loop re-expressed as an IMPLICIT program"
 * means EXACTLY {@code [Consume, Produce, Roll, Present]}, in this order, every time, with the
 * caller's already-resolved value objects carried verbatim onto each step's own group - no
 * silent reordering, no dropped groups.
 */
public class ImplicitProgramTest {

    @Test
    void build_producesExactlyFourStepsInTheClassicOrder() {
        StationStep.Consume consume = StationStep.Consume.of("Wood_Oak_Trunk", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Wood_Hardwood_Planks", 2, "Inventory");
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, new Roll[0], cyclePresentation);

        assertEquals(4, steps.size());
        assertEquals(StationStep.TYPE_CONSUME, steps.get(0).getType());
        assertEquals(StationStep.TYPE_PRODUCE, steps.get(1).getType());
        assertEquals(StationStep.TYPE_ROLL, steps.get(2).getType());
        assertEquals(StationStep.TYPE_PRESENT, steps.get(3).getType());
    }

    @Test
    void build_carriesTheCallersValueObjectsVerbatim() {
        StationStep.Consume consume = StationStep.Consume.of("Wood_Oak_Trunk", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Wood_Hardwood_Planks", 2, "Inventory");
        Roll[] rolls = new Roll[]{Roll.of("Cycle", null, null, null, Roll.Grants.of(1, null, null))};
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, rolls, cyclePresentation);

        assertSame(consume, steps.get(0).getConsume());
        assertSame(produce, steps.get(1).getProduce());
        assertSame(rolls, steps.get(2).getRoll().getRolls());
        assertNull(steps.get(2).getRoll().getLootable(), "the implicit program pre-resolves Loot.Tables - no Lootable ref on the built step");
        assertSame(cyclePresentation, steps.get(3).getPresentation());
    }

    @Test
    void build_withNullCyclePresentation_presentStepCarriesNull() {
        StationStep.Consume consume = StationStep.Consume.of("X", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Y", 1, "Inventory");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, new Roll[0], null);

        assertNull(steps.get(3).getPresentation(), "a station with no cycle Presentation authors a no-op Present step");
    }

    @Test
    void build_stepIdsAreStableAndUnique() {
        StationStep.Consume consume = StationStep.Consume.of("X", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Y", 1, "Inventory");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, new Roll[0], null);

        assertEquals(ImplicitProgram.ID_CONSUME, steps.get(0).getId());
        assertEquals(ImplicitProgram.ID_PRODUCE, steps.get(1).getId());
        assertEquals(ImplicitProgram.ID_ROLL, steps.get(2).getId());
        assertEquals(ImplicitProgram.ID_PRESENT, steps.get(3).getId());
    }
}
