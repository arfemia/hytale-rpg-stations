package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The ONE native-EntityEffect reference leaf (seam wave, decision 51d): an ID-REF-ONLY
 * {@code {Id, DurationMs?}} pointing at a native Hytale {@code EntityEffect} asset by id, never
 * inlining its body (decision 53's repo-wide id-ref-only ratification). Reused at BOTH altitudes
 * an effect payload can be authored:
 *
 * <ul>
 *   <li>a station/step/flair {@link Presentation#getEffect()} - the per-moment effect (a single
 *   nullable {@code Effect} group);
 *   <li>a loot an {@code rpgstations:effect} reward - a reward-time effect array ({@code Effects[]}).
 * </ul>
 *
 * <p>{@link #id} is the native {@code EntityEffect} asset id (e.g. a vanilla buff/haze effect);
 * {@link #durationMs} is an OPTIONAL authored duration override in milliseconds - null defers to the
 * referenced effect asset's own duration (the native effect carries its own TTL). The engine applies
 * every session-scoped effect through a tracked list so {@code stop()} removes them all (decision
 * 51d, engine-side scope). An unresolvable effect id is a validator INFO (typo detection, decision
 * 53) and a no-op at apply, never a throw.
 */
public final class EffectRef {

    @Nullable protected String id;
    @Nullable protected Long durationMs;

    public static final BuilderCodec<EffectRef> CODEC = BuilderCodec.builder(EffectRef.class, EffectRef::new)
            .appendInherited(new KeyedCodec<>("Id", Codec.STRING, false),
                    (o, v) -> o.id = v, o -> o.id, (o, p) -> o.id = p.id)
            .documentation("The native EntityEffect asset id to apply (id-ref-only; never inlines the effect body).").add()
            .appendInherited(new KeyedCodec<>("DurationMs", Codec.LONG, false),
                    (o, v) -> o.durationMs = v, o -> o.durationMs, (o, p) -> o.durationMs = p.durationMs)
            .documentation("Optional duration override in milliseconds; null defers to the referenced effect asset's own TTL.").add()
            .build();

    public EffectRef() {
    }

    @Nonnull
    public static EffectRef of(@Nullable String id, @Nullable Long durationMs) {
        EffectRef e = new EffectRef();
        e.id = id;
        e.durationMs = durationMs;
        return e;
    }

    /** Convenience: an effect ref with no duration override (uses the effect asset's own TTL). */
    @Nonnull
    public static EffectRef of(@Nullable String id) {
        return of(id, null);
    }

    @Nullable
    public String getId() {
        return id;
    }

    /** The authored duration override in milliseconds; null = defer to the effect asset's own TTL. */
    @Nullable
    public Long getDurationMs() {
        return durationMs;
    }

    /** True when {@link #id} is authored (a non-blank effect id). */
    public boolean hasId() {
        return id != null && !id.isBlank();
    }
}
