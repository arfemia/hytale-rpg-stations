package com.ziggfreed.rpgstations.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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
    void registerBuiltins_registersEveryBuiltinUnderItsOwnersNamespace() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        // rpgstations: = vocabulary this engine owns (it exists only because a session does).
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:session_seconds"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:cycle_count"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("rpgstations:socket_filled"));
        // hytale: = straight native reads, portable across mods.
        assertTrue(FactorRegistryImpl.getInstance().isKnown("hytale:tool_power"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("hytale:tool_durability_percent"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("hytale:tool_quality"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("hytale:tool_item_level"));
        assertTrue(FactorRegistryImpl.getInstance().isKnown("hytale:stat"));
    }

    @Test
    void toolPower_paramNamesTheGatherType_andOmittingItFallsBackToTheStationsOwn() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        FactorContext c = FactorContext.builder()
                .playerId(PLAYER)
                .stationId("test_station")
                .toolPower(0.5)
                .toolPowers(Map.of("Woods", 0.5, "Rocks", 0.05))
                .build();
        // Omitted Param = the station's own effective gather type (the pre-existing behavior).
        assertEquals(0.5, FactorRegistryImpl.getInstance().resolve("hytale:tool_power", null, c));
        // An explicit Param addresses any native GatherType, case-insensitively.
        assertEquals(0.05, FactorRegistryImpl.getInstance().resolve("hytale:tool_power", "Rocks", c));
        assertEquals(0.05, FactorRegistryImpl.getInstance().resolve("hytale:tool_power", "rocks", c));
        // A gather type the held tool has no spec for FAILS CLOSED: "cannot do this job at all" is
        // a different answer from "does it badly", so a bounds-less gate on it stays shut and a
        // formula term contributes nothing rather than a substituted zero.
        assertNull(FactorRegistryImpl.getInstance().resolve("hytale:tool_power", "OreMithril", c));
    }

    @Test
    void socketFilled_paramNamesTheSocket_caseInsensitively_andFailsClosedOnTheUnknown() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        FactorContext c = FactorContext.builder()
                .playerId(PLAYER)
                .stationId("test_station")
                .socketsFilled(Map.of("vessel", true, "ingredients", false))
                .build();
        assertEquals(1.0, FactorRegistryImpl.getInstance().resolve("rpgstations:socket_filled", "Vessel", c),
                "a satisfied socket answers 1; ids compare case-insensitively");
        assertEquals(0.0, FactorRegistryImpl.getInstance().resolve("rpgstations:socket_filled", "ingredients", c),
                "an unsatisfied socket answers 0 - a Max-bounded 'only while empty' gate can open on it");
        // A socket this context has no reading for FAILS CLOSED: "cannot tell" is a different
        // answer from "empty", so neither a Min- nor a Max-bounded condition opens on it.
        assertNull(FactorRegistryImpl.getInstance().resolve("rpgstations:socket_filled", "no_such_socket", c));
        assertNull(FactorRegistryImpl.getInstance().resolve("rpgstations:socket_filled", null, c));
        // A context built with no readings at all (a pattern gate, a consumer's own build site)
        // answers nothing either.
        assertNull(FactorRegistryImpl.getInstance().resolve("rpgstations:socket_filled", "vessel",
                ctx(0L, 0, 0.0, 100.0)));
    }

    @Test
    void registerBuiltins_reflectTheContextTheyName() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        FactorContext c = ctx(120L, 7, 0.35, 88.0);
        assertEquals(120.0, FactorRegistryImpl.getInstance().resolve("rpgstations:session_seconds", null, c));
        assertEquals(7.0, FactorRegistryImpl.getInstance().resolve("rpgstations:cycle_count", null, c));
        assertEquals(0.35, FactorRegistryImpl.getInstance().resolve("hytale:tool_power", null, c));
        assertEquals(88.0, FactorRegistryImpl.getInstance().resolve("hytale:tool_durability_percent", null, c));
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
