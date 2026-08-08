package com.ziggfreed.rpgstations.api.impl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.StationFactorProvider;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The {@code "hytale:stat"} factor id (design section 4.1, gate decisions 37/19-cluster): reads the
 * acting player's EFFECTIVE value for a native stat channel named by {@code Param} directly off
 * the engine's own {@link EntityStatMap} (folded {@code MAX}: base + every live modifier, never
 * the CURRENT value). Namespaced {@code hytale:} rather than {@code rpgstations:} because the
 * vocabulary it addresses is the ENGINE's, not this mod's: the number is a native
 * {@code EntityStatType}'s value, identical at every station and meaningful with no station
 * involved, so the namespace names the data's owner rather than whoever happened to register the
 * provider (see {@link FactorRegistryImpl#registerBuiltins()} for the full namespacing rule and why
 * {@code hytale:tool_power} stays on the other side of it). Addressing lives entirely in
 * {@code Param}, per gate decision 19's
 * {@code {"Factor":"hytale:stat","Param":"<EntityStatType id>"}} shape. Mod-agnostic
 * and RpgStations-CORE by design: ANY mod that writes a native stat channel (a bridged per-stack
 * enhancement, a native weapon {@code StatModifiers}, a progression mirror) participates in a
 * loot/gate/cap formula by authoring that shape with
 * zero extra bridge code - the whole point of the loot-formula middle path.
 *
 * <p>Fail-closed throughout, per {@link StationFactorProvider}'s own contract:
 * <ul>
 *   <li>a blank {@code Param} resolves {@code 0.0} (nothing to look up);</li>
 *   <li>a {@link FactorContext} with no live {@link Store}/{@link PlayerRef} together (the
 *       pre-session {@code Requires} gate check builds one with only {@code playerRef}, no
 *       {@code store}) resolves {@code 0.0} SILENTLY - a normal, expected evaluation shape, not
 *       an authoring mistake;</li>
 *   <li>an UNREGISTERED channel id resolves {@code 0.0} and warns ONCE per distinct id (a typo'd
 *       or never-registered {@code Param} IS an authoring mistake worth surfacing; a stat channel
 *       must be registered before anything referencing it loads).</li>
 * </ul>
 */
final class StatChannelFactorProvider implements StationFactorProvider {

    @Nonnull
    private static final Set<String> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();

    @Override
    public double resolve(@Nonnull FactorContext ctx, @Nullable String param) {
        if (param == null || param.isBlank()) {
            return 0.0;
        }
        Store<EntityStore> store = ctx.store();
        PlayerRef playerRef = ctx.playerRef();
        if (store == null || playerRef == null) {
            // No live world-thread context this evaluation (e.g. the pre-session Requires gate
            // check never populates store()/playerRef() together) - silent 0, not an authoring warn.
            return 0.0;
        }
        Ref<EntityStore> ref;
        try {
            ref = playerRef.getReference();
        } catch (Throwable t) {
            return 0.0;
        }
        if (ref == null || !ref.isValid()) {
            return 0.0;
        }

        int idx;
        try {
            idx = EntityStatType.getAssetMap().getIndex(param);
        } catch (Throwable t) {
            warnUnknownOnce(param);
            return 0.0;
        }
        if (idx < 0) {
            warnUnknownOnce(param);
            return 0.0;
        }

        try {
            EntityStatMap stats = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
            if (stats == null) {
                return 0.0;
            }
            EntityStatValue val = stats.get(idx);
            return val == null ? 0.0 : val.getMax();
        } catch (Throwable t) {
            Log.fine("stat factor provider: read of '" + param + "' failed: " + t.getMessage());
            return 0.0;
        }
    }

    private static void warnUnknownOnce(@Nonnull String statId) {
        if (WARNED_UNKNOWN.add(statId)) {
            Log.warn("stat factor provider: unregistered stat channel '" + statId
                    + "' - resolving 0.0 (register the channel before any pack item decodes)");
        }
    }

    /** Test-only reset so a unit test can re-observe the warn-once gate. */
    static void resetWarnedForTests() {
        WARNED_UNKNOWN.clear();
    }
}
