package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The ONE weighted factor reference (scope-2 design section 1.3/4.2, decisions 20/29c): a
 * {@code {Factor, Weight?, Param?}} leaf reused at EVERY site that composes numeric factor
 * channels - {@code Roll.Chance.AddFactors}, {@code Roll.Ladder.Values}, {@code StatRollEntry
 * .Points.AddFactors}, {@code StationStep.Stamp.Stats.Caps.Budgets[].Factors}, and
 * {@code StationStep.Repeat.AddFactors}. Composition at every consumer is a flat weighted sum,
 * {@code sum(resolve(Factor, Param) * Weight)} - no expression nesting (directive 3's boundary).
 *
 * <p>This is the ADD/scale sibling of {@link Condition}: {@link Condition} is a factor reference
 * PLUS gate bounds ({@code Min}/{@code Max}); {@code FactorRef} is a factor reference PLUS a
 * {@code Weight}. {@link #factor} is a namespaced id (e.g. {@code "stat"},
 * {@code "rpgstations:tool_power"}, {@code "mmoskilltree:station_luck"}); {@link #param} is an
 * optional argument the provider interprets (for {@code "stat"} it is the native stat channel id,
 * e.g. {@code "MMO_Luck"}). An unregistered factor id resolves to 0 (fail-closed), never a throw.
 */
public final class FactorRef {

    @Nullable protected String factor;
    @Nullable protected String param;
    @Nullable protected Double weight;

    public static final BuilderCodec<FactorRef> CODEC = BuilderCodec.builder(FactorRef.class, FactorRef::new)
            .appendInherited(new KeyedCodec<>("Factor", Codec.STRING, false),
                    (o, v) -> o.factor = v, o -> o.factor, (o, p) -> o.factor = p.factor)
            .documentation("The namespaced factor channel id (e.g. 'stat', 'rpgstations:tool_power'). Unregistered = resolves to 0 (fail-closed).").add()
            .appendInherited(new KeyedCodec<>("Param", Codec.STRING, false),
                    (o, v) -> o.param = v, o -> o.param, (o, p) -> o.param = p.param)
            .documentation("Optional provider-interpreted argument (for 'stat' the native stat channel id, e.g. 'MMO_Luck'; for a skill factor a skill id).").add()
            .appendInherited(new KeyedCodec<>("Weight", Codec.DOUBLE, false),
                    (o, v) -> o.weight = v, o -> o.weight, (o, p) -> o.weight = p.weight)
            .documentation("Multiplier on this factor's resolved value; reader-defaults to 1.0 when omitted. Composition is sum(resolve * Weight).").add()
            .build();

    public FactorRef() {
    }

    @Nonnull
    public static FactorRef of(@Nullable String factor, @Nullable String param, @Nullable Double weight) {
        FactorRef f = new FactorRef();
        f.factor = factor;
        f.param = param;
        f.weight = weight;
        return f;
    }

    /** Convenience: an unweighted (Weight 1.0) reference. */
    @Nonnull
    public static FactorRef of(@Nullable String factor, @Nullable String param) {
        return of(factor, param, null);
    }

    /** Convenience: a {@code stat}-channel reference ({@code {Factor:"stat", Param:<statId>}}). */
    @Nonnull
    public static FactorRef stat(@Nullable String statId) {
        return of("stat", statId, null);
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
    public Double getWeight() {
        return weight;
    }

    /** {@link #weight}, reader-defaulted to 1.0 when null. A 0 (or negative) weight is honored as authored. */
    public double effectiveWeight() {
        return weight != null ? weight : 1.0;
    }
}
