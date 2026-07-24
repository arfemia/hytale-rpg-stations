package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.api.StatRoll;

/**
 * {@link DefaultEnhanceStamper}'s PURE decision cores ({@link DefaultEnhanceStamper#roundEntries}
 * / {@link DefaultEnhanceStamper#sumRolls}), unit-tested without a live {@code ItemStack} - the
 * same discipline {@code com.ziggfreed.common.stats.StackStatsTest} follows for the underlying
 * record ("never through ItemStack... engine state a unit JVM cannot stand up"). {@code inspect}/
 * {@code apply} themselves are thin wrappers over these cores plus {@code StackStats}' own
 * ItemStack-metadata read/write, already covered by {@code StackStats}'s own codec round-trip
 * tests in {@code ziggfreed-common}.
 */
class DefaultEnhanceStamperTest {

    @Test
    void roundEntries_nullOrEmpty_returnsEmptyMap() {
        assertTrue(DefaultEnhanceStamper.roundEntries(null).isEmpty());
        assertTrue(DefaultEnhanceStamper.roundEntries(Map.of()).isEmpty());
    }

    @Test
    void roundEntries_roundsEachEntryToWholePoints() {
        Map<String, Double> entries = new LinkedHashMap<>();
        entries.put("MMO_Luck", 5.6);
        entries.put("MMO_Defense", 2.4);

        Map<String, Integer> rounded = DefaultEnhanceStamper.roundEntries(entries);

        assertEquals(6, rounded.get("MMO_Luck"));
        assertEquals(2, rounded.get("MMO_Defense"));
    }

    @Test
    void roundEntries_skipsNullKeysAndValues() {
        Map<String, Double> entries = new LinkedHashMap<>();
        entries.put("Valid", 3.0);
        entries.put(null, 9.0);

        Map<String, Integer> rounded = DefaultEnhanceStamper.roundEntries(entries);

        assertEquals(1, rounded.size());
        assertEquals(3, rounded.get("Valid"));
    }

    @Test
    void sumRolls_emptyList_returnsEmptyMap() {
        assertTrue(DefaultEnhanceStamper.sumRolls(List.of()).isEmpty());
    }

    @Test
    void sumRolls_sumsSameStatIdAcrossMultipleRolls() {
        List<StatRoll> rolls = List.of(
                new StatRoll("MMO_Luck", 3),
                new StatRoll("MMO_Luck", 4),
                new StatRoll("MMO_Defense", 2));

        Map<String, Double> summed = DefaultEnhanceStamper.sumRolls(rolls);

        assertEquals(7.0, summed.get("MMO_Luck"));
        assertEquals(2.0, summed.get("MMO_Defense"));
    }

    @Test
    void sumRolls_skipsNullOrBlankStatIds() {
        List<StatRoll> rolls = new java.util.ArrayList<>();
        rolls.add(new StatRoll("Valid", 5));
        rolls.add(null);
        rolls.add(new StatRoll("", 3));
        rolls.add(new StatRoll("  ", 3));

        Map<String, Double> summed = DefaultEnhanceStamper.sumRolls(rolls);

        assertEquals(1, summed.size());
        assertEquals(5.0, summed.get("Valid"));
    }
}
