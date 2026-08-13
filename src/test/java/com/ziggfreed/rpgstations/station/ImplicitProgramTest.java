package com.ziggfreed.rpgstations.station;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationStep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
        StationStep.Consume consume = StationStep.Consume.ofOne("Wood_Oak_Trunk", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.ofOne("Wood_Hardwood_Planks", 2, "Inventory");
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, null, cyclePresentation);

        assertEquals(1, steps.size(), "scope-2 collapses the four-step program onto ONE step");
        StationStep step = steps.get(0);
        assertNull(step.getStamp(), "the implicit program never stamps");
        assertNull(step.getWalk(), "the implicit program never walks");
    }

    @Test
    void build_carriesTheCallersValueObjectsVerbatim() {
        StationStep.Consume consume = StationStep.Consume.ofOne("Wood_Oak_Trunk", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.ofOne("Wood_Hardwood_Planks", 2, "Inventory");
        Roll[] rolls = new Roll[]{Roll.of("Cycle", null, null, null, LootGrants.ofDropList("Fixture_Drops"), null)};
        LootRef bonus = LootRef.of(new String[]{"fixture_table"}, rolls);
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, bonus, cyclePresentation);
        StationStep step = steps.get(0);

        assertSame(consume, step.getConsume());
        assertSame(produce, step.getProduce());
        assertSame(bonus, step.getRoll(),
                "the whole Bonus ref rides the Roll phase, so a referenced table's pool reaches it too");
        assertSame(rolls, step.getRoll().getRolls());
        assertSame(cyclePresentation, step.getPresentation());
    }

    @Test
    void build_withNullCyclePresentation_carriesNull() {
        StationStep.Consume consume = StationStep.Consume.ofOne("X", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.ofOne("Y", 1, "Inventory");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, null, null);

        assertNull(steps.get(0).getPresentation(), "a station with no cycle Presentation authors no presentation phase");
    }

    @Test
    void build_stepIdIsStable() {
        StationStep.Consume consume = StationStep.Consume.ofOne("X", null, 1, "Inventory");
        StationStep.Produce produce = StationStep.Produce.ofOne("Y", 1, "Inventory");

        List<StationStep> steps = ImplicitProgram.build(consume, produce, null, null);

        assertEquals(ImplicitProgram.ID_WORK, steps.get(0).getId());
    }
}
