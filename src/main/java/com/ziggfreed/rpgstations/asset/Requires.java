package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.progress.gate.GateSpec;

/**
 * The station start gate is the SHARED {@code Requires} block (ziggfreed-common's {@link GateSpec}: Factors,
 * Permission, Quests, Custom, AllOf, AnyOf, Not), so a station, an action and a structure pattern gate exactly as
 * a quest does, and a refused player is told what is missing in the same words. This class adds no field and no
 * rule of its own: it is the shared block under a type of its own, so the schema the engine exports shows this
 * mod's {@code Requires} (with {@code Conditions}) apart from the shared one every other store embeds.
 *
 * <p>{@code Conditions}, the factor list stations wrote before they joined the shared block, is read for good as
 * {@code Factors} ({@link GateSpec#codecReadingAsFactors}), through this mod's own factor pick list, so every
 * shipped file keeps meaning what it meant.
 *
 * <pre>{@code
 * "Requires": {
 *   "Permission": "myserver.stations.sawmill",
 *   "Conditions": [ { "Factor": "yourmod:reputation", "Param": "guild", "Min": 15 } ]
 * }
 * }</pre>
 */
public final class Requires extends GateSpec {

    /** The gate leaf every station, action and pattern embeds. */
    @Nonnull
    public static final BuilderCodec<Requires> CODEC = GateSpec.codecReadingAsFactors(Requires.class, Requires::new,
            "Conditions", Conditions.CODEC,
            "Factor bounds on the factor vocabulary, every one of which must pass: the same as Factors, kept under "
                    + "this name so files written before stations used the shared Requires keep working. Read on a "
                    + "station, an action and a structure pattern only; everywhere else, write Factors. An "
                    + "unregistered factor fails closed.");

    public Requires() {
    }

    /** Java-side factory: a block asking for {@code permission} and every one of {@code conditions}. */
    @Nonnull
    public static Requires of(@Nullable String permission, @Nullable FactorCondition[] conditions) {
        Requires requires = new Requires();
        requires.factors = conditions == null ? null : conditions.clone();
        requires.permission = permission;
        return requires;
    }
}
