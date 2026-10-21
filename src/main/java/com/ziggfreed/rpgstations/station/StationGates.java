package com.ziggfreed.rpgstations.station;

import javax.annotation.Nullable;

import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.progress.gate.GateQuery;
import com.ziggfreed.common.progress.gate.GateRuntime;
import com.ziggfreed.common.progress.gate.GateSpec;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The ONE read of a per-worker opt-in gate ({@code SkipWhen}, a fallback route's {@code PreferWhen}): the shared
 * {@code Requires} block asked of the moment a {@link FactorLookup} stands for, through the server's one requirement
 * evaluator, this mod's own readings answering first.
 *
 * <p><b>Opt-in by construction.</b> Such a gate turns a technique ON when it passes, so everything that is not a
 * clear pass reads as "not asked for": no block, an empty block, no lookup (an unattended settle, a press with no
 * worker), a lookup that can put no question, and a throw. A server where the factor a gate reads is unknown or
 * switched off therefore runs every action exactly as authored.
 */
final class StationGates {

    private StationGates() {
    }

    /** True only when {@code gate} asks something and passes for the moment {@code lookup} stands for. */
    static boolean passes(@Nullable GateSpec gate, @Nullable FactorLookup lookup) {
        if (gate == null || gate.isEmpty() || lookup == null) {
            return false;
        }
        try {
            GateQuery query = lookup.gateQuery();
            return query != null && GateRuntime.evaluator().check(gate, query).passed();
        } catch (Throwable t) {
            Log.fine("STATION opt-in gate could not be asked: " + t.getMessage());
            return false;
        }
    }
}
