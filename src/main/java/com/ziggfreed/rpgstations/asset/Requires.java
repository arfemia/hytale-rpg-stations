package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * RpgStations-OWN start gate (design section 4.4.2), severing {@code StationAsset.Requires}
 * off the MMO's {@code content.gate.Requirements}: orthogonal nullable leaves, {@link
 * #permission} (a plain permission-node check) and {@link #conditions} (every entry must
 * pass - the shared {@link Condition} factor gate). Absent group = ungated.
 *
 * <pre>{@code
 * "Requires": {
 *   "Permission": "myserver.stations.sawmill",
 *   "Conditions": [ { "Factor": "mmoskilltree:skill_level", "Param": "WOODCUTTING", "Min": 15 } ]
 * }
 * }</pre>
 *
 * <p>The MMO's rich per-gate-type {@code ui.gate.locked_*} vocabulary is NOT reproduced (no
 * shipped station used it); a failing gate denies with the single {@code ui.station.locked}
 * toast, same as every other engage denial.
 */
public final class Requires {

    @Nullable protected String permission;
    @Nullable protected Condition[] conditions;

    public static final BuilderCodec<Requires> CODEC = BuilderCodec.builder(Requires.class, Requires::new)
            .appendInherited(new KeyedCodec<>("Permission", Codec.STRING, false),
                    (o, v) -> o.permission = v, o -> o.permission, (o, p) -> o.permission = p.permission).add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Condition.CODEC, Condition[]::new), false),
                    (o, v) -> o.conditions = v, o -> o.conditions, (o, p) -> o.conditions = p.conditions).add()
            .build();

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static Requires of(@Nullable String permission, @Nullable Condition[] conditions) {
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
    public Condition[] getConditions() {
        return conditions;
    }

    /** True when neither leaf is authored (an empty group behaves the same as an absent one). */
    public boolean isEmpty() {
        return (permission == null || permission.isBlank())
                && (conditions == null || conditions.length == 0);
    }
}
