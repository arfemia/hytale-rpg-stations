package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.asset.EditorSchema;

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

    /** The authored word for the worker's own body, the default target. */
    public static final String TARGET_PLAYER = "Player";

    /** The authored word for the worker's double. */
    public static final String TARGET_PUPPET = "Puppet";

    @Nullable protected String id;
    @Nullable protected Long durationMs;
    @Nullable protected String target;

    public static final BuilderCodec<EffectRef> CODEC = BuilderCodec.builder(EffectRef.class, EffectRef::new)
            .appendInherited(new KeyedCodec<>("Id", Codec.STRING, false),
                    (o, v) -> o.id = v, o -> o.id, (o, p) -> o.id = p.id)
            .documentation("The native EntityEffect asset id to apply (id-ref-only; never inlines the effect body).").add()
            .appendInherited(new KeyedCodec<>("DurationMs", Codec.LONG, false),
                    (o, v) -> o.durationMs = v, o -> o.durationMs, (o, p) -> o.durationMs = p.durationMs)
            .documentation("Optional duration override in milliseconds; null defers to the referenced effect asset's own TTL. On the Puppet target the engine keeps this clock itself, since the double carries no stat map for the engine's effect timer: the effect is put on with no expiry and taken off when the time is up, or at the session's end, whichever comes first.").add()
            .appendInherited(new KeyedCodec<>("Target", Codec.STRING, false),
                    (o, v) -> o.target = v, o -> o.target, (o, p) -> o.target = p.target)
            .metadata(EditorSchema.oneOfDocumented(
                    TARGET_PLAYER, "The worker's own body, the default: what a 2D sound sting or a screen effect needs",
                    TARGET_PUPPET, "The worker's double: what a visible aura or a ModelVFX on the performer needs"))
            .metadata(EditorSchema.defaultValue(TARGET_PLAYER))
            .documentation("Who wears the effect: Player (the default, the worker's own body, which is what a LocalSoundEventId sting or a screen effect needs) or Puppet (the worker's double, for an aura or a ModelVFX the onlookers should see on the performer; the worker's own body when no double stands). An effect on the double never expires by itself, so a DurationMs there is kept by the engine's own clock, and every effect it put on comes off at the session's end.").add()
            .build();

    public EffectRef() {
    }

    @Nonnull
    public static EffectRef of(@Nullable String id, @Nullable Long durationMs) {
        return of(id, durationMs, null);
    }

    /** Java-side factory carrying the {@code Target} too. */
    @Nonnull
    public static EffectRef of(@Nullable String id, @Nullable Long durationMs, @Nullable String target) {
        EffectRef e = new EffectRef();
        e.id = id;
        e.durationMs = durationMs;
        e.target = target;
        return e;
    }

    /** The authored target word, unparsed; {@link #targetsPuppet()} is the read. */
    @Nullable
    public String getTarget() {
        return target;
    }

    /** True when the effect goes on the worker's double ({@code Target: "Puppet"}); false for the worker's own body, the default. */
    public boolean targetsPuppet() {
        return TARGET_PUPPET.equalsIgnoreCase(target);
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
