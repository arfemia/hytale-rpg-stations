package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.ziggfreed.common.factor.FactorCondition;

/**
 * The station start gate (design section 4.4.2): orthogonal nullable leaves, {@link #permission}
 * (a plain permission-node check) and {@link #conditions} (every entry must pass - the shared
 * {@link FactorCondition} factor gate). Absent group = ungated.
 *
 * <pre>{@code
 * "Requires": {
 *   "Permission": "myserver.stations.sawmill",
 *   "Conditions": [ { "Factor": "yourmod:reputation", "Param": "guild", "Min": 15 } ]
 * }
 * }</pre>
 *
 * <p>A failing gate denies with the single {@code ui.station.locked} toast, same as every other
 * engage denial.
 */
public final class Requires {

    @Nullable protected String permission;
    @Nullable protected FactorCondition[] conditions;

    public static final BuilderCodec<Requires> CODEC = BuilderCodec.builder(Requires.class, Requires::new)
            .appendInherited(new KeyedCodec<>("Permission", Codec.STRING, false),
                    (o, v) -> o.permission = v, o -> o.permission, (o, p) -> o.permission = p.permission)
            .documentation("A permission node the player must hold to start; null = no permission gate.").add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Conditions.CODEC, FactorCondition[]::new), false),
                    (o, v) -> o.conditions = v, o -> o.conditions, (o, p) -> o.conditions = p.conditions)
            .documentation("Factor gate: every Condition must pass to start; an unregistered factor fails CLOSED.").add()
            .build();

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static Requires of(@Nullable String permission, @Nullable FactorCondition[] conditions) {
        Requires r = new Requires();
        r.permission = permission;
        r.conditions = conditions;
        return r;
    }

    @Nullable
    public String getPermission() {
        return permission;
    }

    @Nullable
    public FactorCondition[] getConditions() {
        return conditions;
    }

    /** True when neither leaf is authored (an empty group behaves the same as an absent one). */
    public boolean isEmpty() {
        return (permission == null || permission.isBlank())
                && (conditions == null || conditions.length == 0);
    }
}
