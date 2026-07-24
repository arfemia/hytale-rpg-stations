package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * ONE shared numeric-factor GATE leaf (design section 4.4.2/4.5.1, documented as a
 * {@link FactorRef} plus gate bounds per scope-2 1.3): {@code {Factor, Param?, Min?, Max?}},
 * evaluated against the extensible factor vocabulary a request/response registry resolves (the
 * api {@code FactorRegistry}). Used wherever a factor value must PASS a bound (a {@code Requires}
 * gate, a {@code Roll.Conditions} entry, a {@code StationStep.Conditions} entry) - the
 * scale/add sibling {@link FactorRef} ({@code Factor}/{@code Param}/{@code Weight}) is used
 * wherever a factor value is SUMMED instead. ONE codec each, DRY, per the root convention against
 * a redundant second shape.
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
                    (o, v) -> o.factor = v, o -> o.factor, (o, p) -> o.factor = p.factor)
            .documentation("The namespaced factor channel id to gate on. Unregistered = fails CLOSED (the gate never silently opens).").add()
            .appendInherited(new KeyedCodec<>("Param", Codec.STRING, false),
                    (o, v) -> o.param = v, o -> o.param, (o, p) -> o.param = p.param)
            .documentation("Optional provider-interpreted argument (e.g. a skill id, or a 'stat' channel id).").add()
            .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                    (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
            .documentation("Lower bound (inclusive); the resolved factor value must be >= Min. Omit for no lower bound.").add()
            .appendInherited(new KeyedCodec<>("Max", Codec.DOUBLE, false),
                    (o, v) -> o.max = v, o -> o.max, (o, p) -> o.max = p.max)
            .documentation("Upper bound (inclusive); the resolved factor value must be <= Max. Omit for no upper bound.").add()
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
