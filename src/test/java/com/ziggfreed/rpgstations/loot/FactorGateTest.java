package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.factor.FactorCondition;

/**
 * The bound-gate contract every {@code Conditions} array in this schema is evaluated by - a
 * {@code Roll}'s gate, a station's or an action's {@code Requires}, and a step's own gate all go
 * through {@link FactorGate}, so these are the tests for all three at once.
 */
class FactorGateTest {

    private static final BiFunction<String, String, Double> UNRESOLVABLE = (factorId, param) -> null;

    @Test
    void nullOrEmptyArray_passesVacuously() {
        assertTrue(FactorGate.pass(null, UNRESOLVABLE));
        assertTrue(FactorGate.pass(new FactorCondition[0], UNRESOLVABLE));
        assertNull(FactorGate.firstFailure(null, UNRESOLVABLE));
    }

    @Test
    void blankFactor_passesVacuously() {
        assertTrue(FactorGate.passes(FactorCondition.of("", null, null, null), (factorId, param) -> 999.0));
        assertTrue(FactorGate.passes(null, UNRESOLVABLE));
    }

    @Test
    void unresolvableFactor_failsClosed() {
        assertFalse(FactorGate.passes(FactorCondition.of("rpgstations:unknown", null, null, null), UNRESOLVABLE));
    }

    @Test
    void boundlessCondition_isAPresenceCheck() {
        assertTrue(FactorGate.passes(FactorCondition.of("rpgstations:cycle_count", null, null, null),
                (factorId, param) -> 0.0));
    }

    @Test
    void belowMin_fails() {
        assertFalse(FactorGate.passes(FactorCondition.of("yourmod:reputation", "guild", 15.0, null),
                (factorId, param) -> 10.0));
    }

    @Test
    void atOrAboveMin_passes() {
        FactorCondition c = FactorCondition.of("yourmod:reputation", "guild", 15.0, null);
        assertTrue(FactorGate.passes(c, (factorId, param) -> 15.0));
        assertTrue(FactorGate.passes(c, (factorId, param) -> 20.0));
    }

    @Test
    void aboveMax_fails() {
        assertFalse(FactorGate.passes(FactorCondition.of("rpgstations:cycle_count", null, null, 10.0),
                (factorId, param) -> 11.0));
    }

    @Test
    void atOrBelowMax_passes() {
        FactorCondition c = FactorCondition.of("rpgstations:cycle_count", null, null, 10.0);
        assertTrue(FactorGate.passes(c, (factorId, param) -> 10.0));
        assertTrue(FactorGate.passes(c, (factorId, param) -> 5.0));
    }

    @Test
    void paramIsForwardedToTheLookup() {
        FactorCondition c = FactorCondition.of("yourmod:reputation", "guild", 5.0, null);
        assertTrue(FactorGate.passes(c, (factorId, param) -> {
            assertEquals("yourmod:reputation", factorId);
            assertEquals("guild", param);
            return 5.0;
        }));
    }

    @Test
    void firstFailure_namesTheFactorThatShutTheGate() {
        FactorCondition[] conditions = {
                FactorCondition.of("rpgstations:cycle_count", null, null, 10.0),
                FactorCondition.of("yourmod:reputation", null, 50.0, null),
        };
        assertEquals("yourmod:reputation", FactorGate.firstFailure(conditions,
                (factorId, param) -> "rpgstations:cycle_count".equals(factorId) ? 5.0 : 1.0));
        assertNull(FactorGate.firstFailure(conditions,
                (factorId, param) -> "rpgstations:cycle_count".equals(factorId) ? 5.0 : 60.0));
    }

    @Test
    void everyEntryMustPass() {
        FactorCondition[] conditions = {
                FactorCondition.of("rpgstations:cycle_count", null, 1.0, null),
                FactorCondition.of("rpgstations:session_seconds", null, 1.0, null),
        };
        assertTrue(FactorGate.pass(conditions, (factorId, param) -> 5.0));
        assertFalse(FactorGate.pass(conditions,
                (factorId, param) -> "rpgstations:cycle_count".equals(factorId) ? 5.0 : null));
    }
}
