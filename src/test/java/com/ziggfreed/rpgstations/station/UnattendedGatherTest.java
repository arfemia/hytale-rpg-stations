package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Decision 90's payout half, pinned pure: the gather plan (accrued cycles allocated per
 * conversion, capped by the SAME {@code MaxCycles} ceiling a settle burst wears), the
 * consume-the-record drain (accrual keys clear, the doneness namespace survives), and the
 * contribution math a gather forwards - the idle rate times the {@code ContributionScale} ladder
 * resolved against the GATHERER (a stubbed factor read stands in for them here) times the granted
 * cycles. Every fixture is authored by the test itself.
 */
class UnattendedGatherTest {

    // ==================== the gather plan ====================

    @Test
    void gatherPlan_allocatesInInsertionOrder_underTheCeiling() {
        Map<String, Integer> accrued = new LinkedHashMap<>();
        accrued.put(StationUnattended.accrualKey(0), 10);
        accrued.put(StationUnattended.accrualKey(2), 10);

        StationUnattended.GatherPlan plan = StationUnattended.gatherPlan(accrued, 15);

        assertEquals(15, plan.grantCycles(), "the ceiling caps the whole gather");
        assertEquals(10, plan.cyclesByConversionIndex().get(0));
        assertEquals(5, plan.cyclesByConversionIndex().get(2),
                "the second key gets what remains of the budget, insertion order");
    }

    @Test
    void gatherPlan_unparseableKeyStillPays_underTheAnonymousBucket() {
        Map<String, Integer> accrued = new LinkedHashMap<>();
        accrued.put("accrual:conversion:gone", 4);

        StationUnattended.GatherPlan plan = StationUnattended.gatherPlan(accrued, 24);

        assertEquals(4, plan.grantCycles(),
                "cycles whose conversion no longer resolves still pay rolls and contributions");
        assertEquals(4, plan.cyclesByConversionIndex().get(-1),
                "only the output-item identity is lost with the index");
    }

    @Test
    void gatherPlan_ignoresForeignKeysAndNonPositiveCounts() {
        Map<String, Integer> accrued = new LinkedHashMap<>();
        accrued.put(StationDoneness.BATCHES_KEY, 3);
        accrued.put(StationUnattended.accrualKey(0), 0);
        accrued.put(StationUnattended.accrualKey(1), -2);

        assertFalse(StationUnattended.gatherPlan(accrued, 24).anythingOwed());
    }

    // ==================== the consume-the-record drain ====================

    @Test
    void drainAccruedCycles_takesAccrualKeysOnly_leavesDonenessAlone() {
        StationCustodyClaim claim = new StationCustodyClaim(
                UUID.randomUUID(), "cookingpit", "stew", 0, 64, 0);
        claim.addTo("output", claim.ownerId, "Food_Stew", 3);
        claim.accruePendingCycles("output", StationUnattended.accrualKey(0), 3);
        claim.noteDonenessBatch("output", 5_000L);

        Map<String, Integer> accrued = claim.drainAccruedCycles(List.of("output"));

        assertEquals(Map.of(StationUnattended.accrualKey(0), 3), accrued);
        assertFalse(claim.carriesAccruedCycles(List.of("output")), "the record is consumed whole");
        assertEquals(1, claim.pendingCycles("output").get(StationDoneness.BATCHES_KEY),
                "the doneness record is a different namespace and survives the drain");
    }

    @Test
    void drainAccruedCycles_readsOnlyTheGivenSockets() {
        // A gather takes ONE pile; another pile's accrual stays owed to whoever gathers IT.
        StationCustodyClaim claim = new StationCustodyClaim(
                UUID.randomUUID(), "cookingpit", "stew", 0, 64, 0);
        claim.accruePendingCycles("output", StationUnattended.accrualKey(0), 2);
        claim.accruePendingCycles("sidecar", StationUnattended.accrualKey(1), 5);

        Map<String, Integer> accrued = claim.drainAccruedCycles(List.of("output"));

        assertEquals(Map.of(StationUnattended.accrualKey(0), 2), accrued);
        assertTrue(claim.carriesAccruedCycles(List.of("sidecar")),
                "the ungathered pile keeps its own accrual");
    }

    // ==================== the contribution math (idle rate x gatherer scale x cycles) ====================

    @Test
    void gatherContributions_idleScaledPerCycle_thenMultipliedByGrantedCycles() {
        // The exact composition grantAccruedAtGather runs: contributionsFrom at the idle rate
        // (the existing idle-cycle scaling path, no new knob) with the ladder multiplier, then
        // scaledByCycles for the whole batch.
        Contribution[] posts = {Contribution.of("yourmod:progress", "wood", 10.0)};
        List<StationContribution> perCycle = StationService.contributionsFrom(posts, true, 0.25, 1.0);
        assertEquals(2.5, perCycle.get(0).amount(), 1e-9, "one cycle pays the idle fraction");

        List<StationContribution> batch = StationUnattended.scaledByCycles(perCycle, 8);
        assertEquals(1, batch.size());
        assertEquals("yourmod:progress", batch.get(0).channel());
        assertEquals("wood", batch.get(0).param());
        assertEquals(20.0, batch.get(0).amount(), 1e-9, "8 accrued cycles pay 8 idle cycles' worth");
    }

    @Test
    void gatherContributions_contributionScaleResolvesAgainstTheGathererSnapshot() {
        // The gatherer is the factor subject (decision 90): the ladder's factor reads come from
        // THEIR snapshot - stubbed here - so a better-equipped gatherer multiplies the batch.
        ContributionScale ladder = ContributionScale.of(
                new FactorFormula.Term[] {
                        FactorFormula.Term.of("hytale:tool_quality", null, 1.0)},
                new ContributionScale.Floor[] {ContributionScale.Floor.of(3.0, 2.0)});

        double gathererScale = ContributionScaling.multiplier(ladder,
                (factorId, param) -> "hytale:tool_quality".equals(factorId) ? 4.0 : null);
        double bareHandScale = ContributionScaling.multiplier(ladder,
                (factorId, param) -> "hytale:tool_quality".equals(factorId) ? 0.0 : null);

        assertEquals(2.0, gathererScale, 1e-9, "the stubbed gatherer read reaches the 3.0 floor");
        assertEquals(1.0, bareHandScale, 1e-9, "a gatherer below every floor multiplies nothing");

        List<StationContribution> perCycle = StationService.contributionsFrom(
                new Contribution[] {Contribution.of("yourmod:progress", null, 10.0)},
                true, 0.1, gathererScale);
        assertEquals(4.0, StationUnattended.scaledByCycles(perCycle, 2).get(0).amount(), 1e-9,
                "10 x 0.1 idle x 2.0 gatherer scale x 2 cycles");
    }

    @Test
    void scaledByCycles_zeroCyclesOrEmptyList_paysNothing() {
        List<StationContribution> perCycle =
                List.of(new StationContribution("yourmod:progress", null, 1.0));
        assertTrue(StationUnattended.scaledByCycles(perCycle, 0).isEmpty());
        assertTrue(StationUnattended.scaledByCycles(List.of(), 5).isEmpty());
    }

    @Test
    void gatherCeiling_isTheSameKnobAsTheSettleBurstCeiling() {
        // One knob, both ends: MaxCycles caps a settle burst AND a gather's payout; the reader
        // default is the shared 24.
        assertEquals(24, StationAsset.Work.Unattended.of(null, null, null).effectiveMaxCycles());
        Map<String, Integer> accrued = new LinkedHashMap<>();
        accrued.put(StationUnattended.accrualKey(0), 100);
        assertEquals(24, StationUnattended.gatherPlan(accrued,
                StationAsset.Work.Unattended.of(null, null, null).effectiveMaxCycles()).grantCycles());
    }

    // ==================== the owner ceiling (Settings Limits.MaxUnattendedGatherCycles) ====================

    @Test
    void gatherCeiling_ownerCeilingClampsWhatOneGatherPays() {
        // The owner ceiling composes as min of caps over the action's own knob, and the clamped
        // value is exactly what the gather plan budgets.
        RpgStationsSettingsAsset.Limits limits =
                RpgStationsSettingsAsset.Limits.of(null, null, null, null, 5);
        int ceiling = limits.clampGatherCycles(
                StationAsset.Work.Unattended.of(null, null, null).effectiveMaxCycles());
        assertEquals(5, ceiling, "the owner ceiling tightens the action's 24");

        Map<String, Integer> accrued = new LinkedHashMap<>();
        accrued.put(StationUnattended.accrualKey(0), 100);
        assertEquals(5, StationUnattended.gatherPlan(accrued, ceiling).grantCycles());
    }

    @Test
    void gatherCeiling_noOwnerCeiling_theActionKnobAloneApplies() {
        RpgStationsSettingsAsset.Limits limits =
                RpgStationsSettingsAsset.Limits.of(null, null, null, null, null);
        assertEquals(24, limits.clampGatherCycles(24), "null = no owner ceiling");
    }

    @Test
    void gatherCeiling_minOfCapsWhenBothAuthored() {
        // Whichever cap is smaller wins, in both directions: the owner ceiling can only tighten
        // an action's knob, never raise it.
        RpgStationsSettingsAsset.Limits looseOwner =
                RpgStationsSettingsAsset.Limits.of(null, null, null, null, 50);
        assertEquals(24, looseOwner.clampGatherCycles(24), "a looser owner ceiling changes nothing");
        RpgStationsSettingsAsset.Limits tightOwner =
                RpgStationsSettingsAsset.Limits.of(null, null, null, null, 8);
        assertEquals(8, tightOwner.clampGatherCycles(24), "a tighter owner ceiling wins");
    }
}
