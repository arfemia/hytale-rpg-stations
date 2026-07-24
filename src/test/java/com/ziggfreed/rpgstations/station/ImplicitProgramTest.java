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
 * The implicit-program equivalence test the scope-2 byte-stable-regression claim rests on (design
 * 2.1): the classic convert loop COLLAPSES onto ONE orthogonal-phase step composing {@code Consume}
 * + {@code Produce} + {@code Roll} + {@code Presentation}, with the caller's already-resolved value
 * objects carried verbatim onto the step's own phase groups - no dropped groups, the phases execute
 * in the composite handler's fixed order (Consume -> Produce -> Roll -> Presentation).
 */
public class ImplicitProgramTest {

    @Test
    void build_producesOneStepWithEveryClassicPhase() {
        StationStep.Consume consume = StationStep.Consume.of("Wood_Oak_Trunk", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Wood_Hardwood_Planks", 2, "Inventory");
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, new Roll[0], cyclePresentation);

        assertEquals(1, steps.size(), "scope-2 collapses the four-step program onto ONE step");
        StationStep step = steps.get(0);
        assertNull(step.getStamp(), "the implicit program never stamps");
        assertNull(step.getWalk(), "the implicit program never walks");
    }

    @Test
    void build_carriesTheCallersValueObjectsVerbatim() {
        StationStep.Consume consume = StationStep.Consume.of("Wood_Oak_Trunk", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Wood_Hardwood_Planks", 2, "Inventory");
        Roll[] rolls = new Roll[]{Roll.of("Cycle", null, null, null, Roll.Grants.of(1, null, null))};
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, rolls, cyclePresentation);
        StationStep step = steps.get(0);

        assertSame(consume, step.getConsume());
        assertSame(produce, step.getProduce());
        assertSame(rolls, step.getRoll().getRolls());
        assertNull(step.getRoll().getLootables(),
                "the implicit program pre-resolves lootable refs - no Lootables ref on the built step");
        assertSame(cyclePresentation, step.getPresentation());
    }

    @Test
    void build_withNullCyclePresentation_carriesNull() {
        StationStep.Consume consume = StationStep.Consume.of("X", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Y", 1, "Inventory");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, new Roll[0], null);

        assertNull(steps.get(0).getPresentation(), "a station with no cycle Presentation authors no presentation phase");
    }

    @Test
    void build_stepIdIsStable() {
        StationStep.Consume consume = StationStep.Consume.of("X", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.of("Y", 1, "Inventory");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, new Roll[0], null);

        assertEquals(ImplicitProgram.ID_WORK, steps.get(0).getId());
    }
}
