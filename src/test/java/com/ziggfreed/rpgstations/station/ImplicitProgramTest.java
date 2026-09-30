package com.ziggfreed.rpgstations.station;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationStep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The implicit-program equivalence test the byte-stable-regression claim rests on: the classic
 * convert loop is ONE orthogonal-phase step composing {@code Convert} + {@code Roll} +
 * {@code Presentation}, with the caller's already-resolved value objects carried verbatim onto the
 * step's own phase groups - no dropped groups, the phases execute in the composite handler's fixed
 * order (Convert -> Roll -> Presentation), and the conversion itself runs through the SAME
 * {@code Convert} phase an authored beat runs (the loop's pre-dispatch selection rides the dispatch
 * as that phase's preselected row).
 */
public class ImplicitProgramTest {

    @Test
    void build_producesOneStepWithEveryClassicPhase() {
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(null, cyclePresentation);

        assertEquals(1, steps.size(), "the classic loop is ONE step");
        StationStep step = steps.get(0);
        assertNotNull(step.getConvert(), "the conversion runs through the Convert phase");
        assertTrue(step.getConvert().effectiveEnabled(), "the implicit convert is always on");
        assertNull(step.getConsume(), "the implicit program authors no Consume of its own");
        assertNull(step.getProduce(), "the implicit program authors no Produce of its own");
        assertNull(step.getStamp(), "the implicit program never stamps");
        assertNull(step.getWalk(), "the implicit program never walks");
        assertFalse(step.isPureBeat(), "a convert is a phase, never a pure beat");
    }

    @Test
    void build_carriesTheCallersValueObjectsVerbatim() {
        Roll[] rolls = new Roll[]{Roll.of("Cycle", null, null, null, LootGrants.ofDropList("Fixture_Drops"), null)};
        LootRef bonus = LootRef.of(new String[]{"fixture_table"}, rolls);
        Presentation cyclePresentation = Presentation.ofSound("SFX_Wood_Break");

        List<StationStep> steps = ImplicitProgram.build(bonus, cyclePresentation);
        StationStep step = steps.get(0);

        assertSame(bonus, step.getRoll(),
                "the whole Bonus ref rides the Roll phase, so a referenced table's pool reaches it too");
        assertSame(rolls, step.getRoll().getRolls());
        assertSame(cyclePresentation, step.getPresentation());
    }

    @Test
    void build_withNullCyclePresentation_carriesNull() {
        List<StationStep> steps = ImplicitProgram.build(null, null);

        assertNull(steps.get(0).getPresentation(), "a station with no cycle Presentation authors no presentation phase");
    }

    @Test
    void build_stepIdIsStable() {
        List<StationStep> steps = ImplicitProgram.build(null, null);

        assertEquals(ImplicitProgram.ID_WORK, steps.get(0).getId());
    }

    @Test
    void build_convertStepIsWorkAndNeverPacedOrBonusAtBeat() {
        StationStep step = ImplicitProgram.build(null, null).get(0);

        assertTrue(step.effectiveIsWork(), "a convert lights its block, so the classic loop keeps its Working look");
        assertFalse(step.effectivePaced(), "the classic loop's cycle is never paced");
        assertFalse(step.effectiveRollBonus(), "the classic loop rolls its Bonus through its own Roll phase, never at a beat");
    }
}
