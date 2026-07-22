package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * ONE shared numeric-factor gate leaf (design section 4.4.2/4.5.1): {@code {Factor, Param?,
 * Min?, Max?}}, evaluated against the extensible factor vocabulary a request/response
 * registry resolves (the api {@code FactorRegistry}, wired in a later leg). Used by both
 * {@link Requires#getConditions()} (station start gate) and the future loot {@code Roll}
 * codec (leg 3) - ONE codec, DRY, per the root convention against a redundant second
 * "condition" shape.
 *
 * <p>{@link #factor} is a namespaced id (e.g. {@code "rpgstations:tool_power"},
 * {@code "mmoskilltree:skill_level"}); {@link #param} is an optional argument the factor
 * provider interprets (e.g. a skill id). {@link #min}/{@link #max} bound the resolved value
 * (either or both may be authored; a factor that resolves outside the bound fails the
 * condition). An unregistered factor id fails CLOSED at runtime (a gate never silently opens
 * because a provider has not registered yet).
 */
public final class Condition {

    @Nullable protected String factor;
    @Nullable protected String param;
    @Nullable protected Double min;
    @Nullable protected Double max;

    public static final BuilderCodec<Condition> CODEC = BuilderCodec.builder(Condition.class, Condition::new)
            .appendInherited(new KeyedCodec<>("Factor", Codec.STRING, false),
                    (o, v) -> o.factor = v, o -> o.factor, (o, p) -> o.factor = p.factor).add()
            .appendInherited(new KeyedCodec<>("Param", Codec.STRING, false),
                    (o, v) -> o.param = v, o -> o.param, (o, p) -> o.param = p.param).add()
            .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                    (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min).add()
            .appendInherited(new KeyedCodec<>("Max", Codec.DOUBLE, false),
                    (o, v) -> o.max = v, o -> o.max, (o, p) -> o.max = p.max).add()
            .build();

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static Condition of(@Nullable String factor, @Nullable String param,
            @Nullable Double min, @Nullable Double max) {
        Condition c = new Condition();
        c.factor = factor;
        c.param = param;
        c.min = min;
        c.max = max;
        return c;
    }

    @Nullable
    public String getFactor() {
        return factor;
    }

    @Nullable
    public String getParam() {
        return param;
    }

    @Nullable
    public Double getMin() {
        return min;
    }

    @Nullable
    public Double getMax() {
        return max;
    }
}
