package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.StationYield;

/**
 * {@code Roll.Grants.OutputItems} - ADDITIVE items of the cycle's own primary output, authored as a
 * FRACTIONAL amount. {@link LootEngine#applyGrants} only TALLIES it (the caller owns the item id,
 * the grant, and the single whole-item resolution), and only for a {@code Cycle} trigger, since a
 * Completion roll has no cycle output to add to.
 *
 * <p>The tally stays a raw SUM on purpose: rounding is {@link OutputItemResolver}'s job, once per
 * cycle over everything the cycle granted. {@link OutputItemResolverTest} owns that half.
 *
 * <p>An OutputItems-only {@code Grants} touches no player or store, so this drives it with null
 * engine handles (the same stand-in pattern {@code LootEngineEffectGrantTest} uses).
 */
class LootEngineOutputItemsTest {

    private static final Player NULL_PLAYER = null;

    private static LootEngine.GrantResult tally(Roll.Grants grants, boolean cycleTrigger) {
        LootEngine.GrantResult result = new LootEngine.GrantResult();
        LootEngine.applyGrants(grants, NULL_PLAYER, null, result, null, 0, 0, 0, cycleTrigger);
        return result;
    }

    @Test
    void applyGrants_talliesTheAuthoredAmount() {
        LootEngine.GrantResult result = tally(Roll.Grants.ofOutputItems(3.0), true);
        assertEquals(3.0, result.getOutputItems());
        assertTrue(result.anyGranted(), "an OutputItems-only grant genuinely granted something");
    }

    @Test
    void applyGrants_talliesAFractionalAmountVerbatim() {
        // Never rounded at the tally: a fraction-only grant is a real, reportable amount that the
        // caller's ONE resolution turns into items (or not).
        LootEngine.GrantResult result = tally(Roll.Grants.ofOutputItems(0.5), true);
        assertEquals(0.5, result.getOutputItems());
        assertTrue(result.anyGranted());
    }

    @Test
    void applyGrants_sumsAcrossSeveralGrantsGroups() {
        // A roll's top-level Grants and its reached floor's Grants BOTH apply, so both tally - and
        // two fractional halves sum to a whole item rather than being rounded separately.
        LootEngine.GrantResult result = new LootEngine.GrantResult();
        LootEngine.applyGrants(Roll.Grants.ofOutputItems(0.5), NULL_PLAYER, null, result, null, 0, 0, 0, true);
        LootEngine.applyGrants(Roll.Grants.ofOutputItems(1.5), NULL_PLAYER, null, result, null, 0, 0, 0, true);
        assertEquals(2.0, result.getOutputItems(), 1e-9);
    }

    @Test
    void applyGrants_nonPositiveOrAbsentAmount_talliesNothing() {
        assertEquals(0.0, tally(Roll.Grants.ofOutputItems(0.0), true).getOutputItems());
        assertEquals(0.0, tally(Roll.Grants.ofOutputItems(-4.0), true).getOutputItems());
        assertEquals(0.0, tally(Roll.Grants.of(null, null), true).getOutputItems());
    }

    @Test
    void applyGrants_outsideACycleTrigger_talliesNothing() {
        LootEngine.GrantResult result = tally(Roll.Grants.ofOutputItems(5.0), false);
        assertEquals(0.0, result.getOutputItems(),
                "a Completion roll has no cycle output to add to, so the grant is dropped");
        assertFalse(result.anyGranted());
    }

    /**
     * The load-bearing property of the ADDITIVE form: an OutputItems amount is a NUMBER OF ITEMS, in
     * the same unit as the deterministic {@code Yield} quantity - never a multiplier over it. The two
     * are therefore directly comparable even though they are authored in different groups, which is
     * exactly what the retired stack-copying leaf got wrong.
     */
    @Test
    void outputItems_addToTheDeterministicYieldRatherThanMultiplyingIt() {
        StationAsset.Yield yield = StationAsset.Yield.of(4, null, null, null);
        int deterministic = StationYield.resolveQuantity(yield, 1);
        double bonus = tally(Roll.Grants.ofOutputItems(3.0), true).getOutputItems();

        assertEquals(4, deterministic);
        assertEquals(3.0, bonus);
        assertEquals(7.0, deterministic + bonus, 1e-9, "4 + 3, never 4 x 3");
    }
}
