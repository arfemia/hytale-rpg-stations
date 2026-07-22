package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link StationFactorRegistry} guard tests: the four built-ins, unknown handling, throw-safety. */
public class StationFactorRegistryTest {

    @Test
    void registerBuiltins_registersTheFourRpgstationsFactors() {
        StationFactorRegistry.registerBuiltins();
        assertTrue(StationFactorRegistry.isKnown("rpgstations:session_seconds"));
        assertTrue(StationFactorRegistry.isKnown("rpgstations:cycle_count"));
        assertTrue(StationFactorRegistry.isKnown("rpgstations:tool_power"));
        assertTrue(StationFactorRegistry.isKnown("rpgstations:tool_durability_percent"));
    }

    @Test
    void registerBuiltins_reflectTheContextTheyName() {
        StationFactorRegistry.registerBuiltins();
        FactorContext ctx = FactorContext.of(120L, 7, 0.35, 88.0);
        assertEquals(120.0, StationFactorRegistry.resolve("rpgstations:session_seconds", null, ctx));
        assertEquals(7.0, StationFactorRegistry.resolve("rpgstations:cycle_count", null, ctx));
        assertEquals(0.35, StationFactorRegistry.resolve("rpgstations:tool_power", null, ctx));
        assertEquals(88.0, StationFactorRegistry.resolve("rpgstations:tool_durability_percent", null, ctx));
    }

    @Test
    void isKnown_falseForBlankOrUnregistered() {
        assertFalse(StationFactorRegistry.isKnown(null));
        assertFalse(StationFactorRegistry.isKnown(""));
        assertFalse(StationFactorRegistry.isKnown("rpgstations:definitely_not_registered"));
    }

    @Test
    void resolve_unregistered_returnsNull() {
        FactorContext ctx = FactorContext.of(0L, 0, 0.0, 100.0);
        assertNull(StationFactorRegistry.resolve("rpgstations:definitely_not_registered", null, ctx));
    }

    @Test
    void resolve_throwingProvider_isCaughtAndReturnsNull() {
        StationFactorRegistry.register("rpgstations:_test_throwing", (ctx, param) -> {
            throw new IllegalStateException("boom");
        });
        FactorContext ctx = FactorContext.of(0L, 0, 0.0, 100.0);
        assertNull(StationFactorRegistry.resolve("rpgstations:_test_throwing", null, ctx));
    }

    @Test
    void register_isCaseInsensitiveOnFactorId() {
        StationFactorRegistry.register("rpgstations:_test_case", (ctx, param) -> 5.0);
        assertTrue(StationFactorRegistry.isKnown("RPGSTATIONS:_TEST_CASE"));
        FactorContext ctx = FactorContext.of(0L, 0, 0.0, 100.0);
        assertEquals(5.0, StationFactorRegistry.resolve("RPGSTATIONS:_TEST_CASE", null, ctx));
    }
}
