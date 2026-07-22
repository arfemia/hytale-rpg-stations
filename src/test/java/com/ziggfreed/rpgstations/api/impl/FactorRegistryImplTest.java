package com.ziggfreed.rpgstations.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.api.FactorContext;

/**
 * {@link FactorRegistryImpl} guard tests, ported from the leg-3 stand-in {@code
 * loot.StationFactorRegistryTest} (deleted this leg): the four built-ins, unknown handling,
 * throw-safety, case-insensitivity - now against the real api-facing registry and {@link
 * FactorContext}.
 */
public class FactorRegistryImplTest {

    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static FactorContext ctx(long sessionSeconds, int cycleIndex, double toolPower,
            double toolDurabilityPercent) {
        return FactorContext.builder()
                .playerId(PLAYER)
                .stationId("test_station")
                .sessionSeconds(sessionSeconds)
                .cycleIndex(cycleIndex)
                .toolPower(toolPower)
                .toolDurabilityPercent(toolDurabilityPercent)
                .build();
    }

    @Test
    void registerBuiltins_registersTheFourRpgstationsFactors() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:session_seconds"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:cycle_count"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:tool_power"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:tool_durability_percent"));
    }

    @Test
    void registerBuiltins_reflectTheContextTheyName() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        FactorContext c = ctx(120L, 7, 0.35, 88.0);
        assertEquals(120.0, FactorRegistryImpl.getInstance().resolve("rpgstations:session_seconds", null, c));
        assertEquals(7.0, FactorRegistryImpl.getInstance().resolve("rpgstations:cycle_count", null, c));
        assertEquals(0.35, FactorRegistryImpl.getInstance().resolve("rpgstations:tool_power", null, c));
        assertEquals(88.0, FactorRegistryImpl.getInstance().resolve("rpgstations:tool_durability_percent", null, c));
    }

    @Test
    void isKnown_falseForBlankOrUnregistered() {
        assertFalse(FactorRegistryImpl.getInstance().isKnown(null));
        assertFalse(FactorRegistryImpl.getInstance().isKnown(""));
        assertFalse(FactorRegistryImpl.getInstance().isKnown("rpgstations:definitely_not_registered"));
    }

    @Test
    void resolve_unregistered_returnsNull() {
        FactorContext c = ctx(0L, 0, 0.0, 100.0);
        assertNull(FactorRegistryImpl.getInstance().resolve("rpgstations:definitely_not_registered", null, c));
    }

    @Test
    void resolve_throwingProvider_isCaughtAndReturnsNull() {
        FactorRegistryImpl.getInstance().register("rpgstations:_test_throwing", (ctx, param) -> {
            throw new IllegalStateException("boom");
        });
        FactorContext c = ctx(0L, 0, 0.0, 100.0);
        assertNull(FactorRegistryImpl.getInstance().resolve("rpgstations:_test_throwing", null, c));
    }

    @Test
    void register_isCaseInsensitiveOnFactorId() {
        FactorRegistryImpl.getInstance().register("rpgstations:_test_case", (ctx, param) -> 5.0);
        assertTrue(FactorRegistryImpl.getInstance().isKnown("RPGSTATIONS:_TEST_CASE"));
        FactorContext c = ctx(0L, 0, 0.0, 100.0);
        assertEquals(5.0, FactorRegistryImpl.getInstance().resolve("RPGSTATIONS:_TEST_CASE", null, c));
    }
}
