package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.common.codec.Rotation;

/**
 * One presentation moment: sound / particle-burst / shake / native-composition asset references
 * (design section 4.1/4.4.3). Every leaf names a NATIVE asset id this engine plays directly, with no
 * feedback-service indirection in between, and every leaf here is genuinely PLAYED - this type
 * carries no reserved slots.
 *
 * <p>Every leaf is {@code appendInherited} so a station whose {@code Parent} sibling
 * partially overrides a nested {@code Presentation} object still inherits the leaves it did
 * not mention.
 */
public final class Presentation {

    @Nullable protected String[] sounds;
    @Nullable protected ModelParticle[] particles;
    @Nullable protected Shake shake;
    @Nullable protected Interaction interaction;
    @Nullable protected EffectRef effect;

    public static final BuilderCodec<Presentation> CODEC = BuilderCodec.builder(Presentation.class, Presentation::new)
            .appendInherited(new KeyedCodec<>("Sounds", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                    (o, v) -> o.sounds = v, o -> o.sounds, (o, p) -> o.sounds = p.sounds)
            .documentation("Native one-shot SoundEvent ids played at the moment's target position, in authored order (a thud plus a chime is two entries). Never author a LOOPING event here - nothing can stop it once fired.").add()
            .appendInherited(new KeyedCodec<>("Particles",
                            new ArrayCodec<>(ModelParticle.CODEC, ModelParticle[]::new), false),
                    (o, v) -> o.particles = v, o -> o.particles, (o, p) -> o.particles = p.particles)
            .documentation("The particle bursts played at this moment, in authored order (native InteractionEffects.Particles is an array too). Each entry is one ModelParticle-shaped burst; layering two is the author's call.").add()
            .appendInherited(new KeyedCodec<>("Shake", Shake.CODEC, false),
                    (o, v) -> o.shake = v, o -> o.shake, (o, p) -> o.shake = p.shake)
            .documentation("A one-shot camera shake: a CameraEffect asset id plus a contextual intensity.").add()
            .appendInherited(new KeyedCodec<>("Interaction", Interaction.CODEC, false),
                    (o, v) -> o.interaction = v, o -> o.interaction, (o, p) -> o.interaction = p.interaction)
            .documentation("A native RootInteraction chain fired at this moment (id-ref-only); null = none.").add()
            .appendInherited(new KeyedCodec<>("Effect", EffectRef.CODEC, false),
                    (o, v) -> o.effect = v, o -> o.effect, (o, p) -> o.effect = p.effect)
            .documentation("A native EntityEffect applied at this moment (id-ref-only, with an optional DurationMs); null = none.").add()
            .build();

    public Presentation() {
    }

    /** Java-side factory carrying a single {@code Sounds} entry. */
    @Nonnull
    public static Presentation ofSound(@Nullable String sound) {
        Presentation p = new Presentation();
        p.sounds = sound == null || sound.isBlank() ? null : new String[] {sound};
        return p;
    }

    /**
     * Java-side factory carrying one sound and/or a single default-tuned particle burst (the
     * one-id convenience shape - {@link ModelParticle#of(String)}).
     */
    @Nonnull
    public static Presentation of(@Nullable String sound, @Nullable String particleSystemId) {
        Presentation p = ofSound(sound);
        p.particles = particleSystemId == null || particleSystemId.isBlank()
                ? null : new ModelParticle[] {ModelParticle.of(particleSystemId)};
        return p;
    }

    /** Fully-populated Java-side factory; does NOT touch the codec or JSON keys. */
    @Nonnull
    public static Presentation of(@Nullable String[] sounds, @Nullable ModelParticle[] particles,
            @Nullable Shake shake, @Nullable Interaction interaction, @Nullable EffectRef effect) {
        Presentation p = new Presentation();
        p.sounds = sounds;
        p.particles = particles;
        p.shake = shake;
        p.interaction = interaction;
        p.effect = effect;
        return p;
    }

    /** The one-shot sound ids played at this moment, in authored order; null/empty = silent. */
    @Nullable
    public String[] getSounds() {
        return sounds;
    }

    /** The particle bursts played at this moment, in authored order; null/empty = none. */
    @Nullable
    public ModelParticle[] getParticles() {
        return particles;
    }

    /** One-shot camera shake (nullable); null = no shake. */
    @Nullable
    public Shake getShake() {
        return shake;
    }

    /** The native RootInteraction chain fired at this moment (id-ref-only); null = none. */
    @Nullable
    public Interaction getInteraction() {
        return interaction;
    }

    /** The native EntityEffect applied at this moment (id-ref-only); null = none. */
    @Nullable
    public EffectRef getEffect() {
        return effect;
    }

    /**
     * ONE particle burst played at a moment, shaped after the native {@code ModelParticle} leaf set
     * ({@code SystemId}/{@code Scale}/{@code RotationOffset}/{@code PositionOffset}) so a station
     * author reads the same vocabulary here as in a native {@code InteractionEffects.Particles}
     * entry, plus {@link #durationSeconds} (the per-burst client-playback cap this engine has always
     * applied, promoted from a Java constant to an authored knob).
     *
     * <p>Every leaf is nullable and its reader default reproduces the pre-array behavior exactly, so
     * an entry authoring only {@code SystemId} plays byte-identically to the old bare-string leaf:
     * {@code Scale} 1.0, {@code DurationSeconds} {@value #DEFAULT_DURATION_SECONDS}, a zero
     * {@code RotationOffset}, and no {@code PositionOffset}.
     *
     * <p><b>{@link #durationSeconds} is a leak guard, not decoration.</b> At least one shipped
     * particle asset ({@code Block_Gem_Sparks}) authors an UNBOUNDED spawner
     * ({@code TotalParticles < 0}) which, fired without a cap, never stops spawning. Authoring
     * {@code 0} or a negative value means UNCAPPED - only do that for a system whose own spawner
     * budget is known to terminate.
     *
     * <p><b>{@link #positionOffset} is FACING-RELATIVE</b> to the placed station block's own yaw,
     * exactly like {@code Custody.Display.Offset} and {@code Puppet.Offset}: authored {@code +Z} is
     * the block's FRONT, {@code +X} its right, {@code Y} stays vertical, and the composition runs
     * through the one shared {@code station.StationBlockFacing} reader. It is IDENTITY at a
     * default-orientation placement. {@link #rotationOffset} is the burst's own emission rotation in
     * DEGREES, passed straight to the engine's yaw/pitch/roll particle arguments (it is NOT composed
     * with the block facing - a rotated burst is authored relative to the burst itself).
     *
     * <p><b>No {@code Color} leaf.</b> The only colour-capable engine overload takes no playback cap,
     * so a tinted burst could not carry {@link #durationSeconds} - and the unbounded-spawner leak
     * that cap closes must never be reintroduced. Vary the tint by referencing a different particle
     * system id.
     */
    public static final class ModelParticle {

        /** The per-burst client-playback cap applied when {@link #durationSeconds} is not authored. */
        public static final float DEFAULT_DURATION_SECONDS = 4.0f;

        /** The uniform burst scale applied when {@link #scale} is not authored. */
        public static final double DEFAULT_SCALE = 1.0;

        @Nullable protected String systemId;
        @Nullable protected Double scale;
        @Nullable protected Double durationSeconds;
        @Nullable protected Rotation rotationOffset;
        @Nullable protected Vec3 positionOffset;

        public static final BuilderCodec<ModelParticle> CODEC =
                BuilderCodec.builder(ModelParticle.class, ModelParticle::new)
                        .appendInherited(new KeyedCodec<>("SystemId", Codec.STRING, false),
                                (o, v) -> o.systemId = v, o -> o.systemId, (o, p) -> o.systemId = p.systemId)
                        .documentation("The native particle-system asset id to spawn (required; a blank entry is skipped).").add()
                        .appendInherited(new KeyedCodec<>("Scale", Codec.DOUBLE, false),
                                (o, v) -> o.scale = v, o -> o.scale, (o, p) -> o.scale = p.scale)
                        .documentation("Uniform burst scale; defaults to 1.0 when absent or non-positive.").add()
                        .appendInherited(new KeyedCodec<>("DurationSeconds", Codec.DOUBLE, false),
                                (o, v) -> o.durationSeconds = v, o -> o.durationSeconds,
                                (o, p) -> o.durationSeconds = p.durationSeconds)
                        .documentation("Client-playback cap in seconds; defaults to 4. Author 0 or less for UNCAPPED, which an unbounded-spawner system will never stop.").add()
                        .appendInherited(new KeyedCodec<>("RotationOffset", Rotation.CODEC, false),
                                (o, v) -> o.rotationOffset = v, o -> o.rotationOffset,
                                (o, p) -> o.rotationOffset = p.rotationOffset)
                        .documentation("Burst emission rotation in degrees (Yaw/Pitch/Roll); each unauthored axis is 0. Not composed with the block facing.").add()
                        .appendInherited(new KeyedCodec<>("PositionOffset", Vec3.CODEC, false),
                                (o, v) -> o.positionOffset = v, o -> o.positionOffset,
                                (o, p) -> o.positionOffset = p.positionOffset)
                        .documentation("Facing-relative shift off the moment's target position: X/Z are in the placed block's own horizontal frame (+Z = its front), Y is vertical.").add()
                        .build();

        public ModelParticle() {
        }

        /** Convenience: a default-tuned burst of {@code systemId} (the one-id authoring shape). */
        @Nonnull
        public static ModelParticle of(@Nullable String systemId) {
            return of(systemId, null, null, null, null);
        }

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static ModelParticle of(@Nullable String systemId, @Nullable Double scale,
                @Nullable Double durationSeconds, @Nullable Rotation rotationOffset,
                @Nullable Vec3 positionOffset) {
            ModelParticle m = new ModelParticle();
            m.systemId = systemId;
            m.scale = scale;
            m.durationSeconds = durationSeconds;
            m.rotationOffset = rotationOffset;
            m.positionOffset = positionOffset;
            return m;
        }

        @Nullable
        public String getSystemId() {
            return systemId;
        }

        /** True when {@link #systemId} is authored (a blank-id burst is skipped at play time). */
        public boolean hasSystemId() {
            return systemId != null && !systemId.isBlank();
        }

        @Nullable
        public Double getScale() {
            return scale;
        }

        @Nullable
        public Double getDurationSeconds() {
            return durationSeconds;
        }

        @Nullable
        public Rotation getRotationOffset() {
            return rotationOffset;
        }

        @Nullable
        public Vec3 getPositionOffset() {
            return positionOffset;
        }

        /** {@link #scale}, reader-defaulted to {@link #DEFAULT_SCALE} when null/non-positive. */
        public double effectiveScale() {
            return scale != null && scale > 0 ? scale : DEFAULT_SCALE;
        }

        /**
         * {@link #durationSeconds}, reader-defaulted to {@link #DEFAULT_DURATION_SECONDS} when null.
         * An AUTHORED zero/negative passes through verbatim as the engine's "uncapped" sentinel.
         */
        public float effectiveDurationSeconds() {
            return durationSeconds != null ? durationSeconds.floatValue() : DEFAULT_DURATION_SECONDS;
        }
    }

    /**
     * One-shot camera shake, matching {@code com.ziggfreed.common.camera.CameraShakeService
     * #shake(PlayerRef, String cameraEffectId, float intensity)} EXACTLY (critique m6 binding
     * fix: verified against the ziggfreed-common source before authoring this leaf - the
     * service references a {@code CameraEffect} ASSET by id and applies an additional
     * intensity multiplier; it carries no raw duration field, since the shake's duration is
     * baked into the referenced {@code CameraEffect} asset). {@link #effectId} is a
     * {@code CameraEffect} asset id (e.g. a vanilla damage-shake asset); {@link #intensity} is
     * the engine's 0..1 contextual intensity space. Both leaves nullable so a station can omit
     * either and land on the service's own no-op guard.
     */
    public static final class Shake {
        @Nullable protected String effectId;
        @Nullable protected Double intensity;

        public static final BuilderCodec<Shake> CODEC = BuilderCodec.builder(Shake.class, Shake::new)
                .appendInherited(new KeyedCodec<>("EffectId", Codec.STRING, false),
                        (o, v) -> o.effectId = v, o -> o.effectId, (o, p) -> o.effectId = p.effectId)
                .documentation("The native CameraEffect asset id to play; a bare id (never a timed EffectRef) since the shake's duration is baked inside the referenced asset.").add()
                .appendInherited(new KeyedCodec<>("Intensity", Codec.DOUBLE, false),
                        (o, v) -> o.intensity = v, o -> o.intensity, (o, p) -> o.intensity = p.intensity)
                .documentation("The contextual shake intensity in the engine's 0..1 space; null lands on the shake service's own no-op guard.")
                .addValidator(CodecWarnValidators.nonNegative("Presentation.Shake.Intensity should not be negative.")).add()
                .build();

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static Shake of(@Nullable String effectId, @Nullable Double intensity) {
            Shake s = new Shake();
            s.effectId = effectId;
            s.intensity = intensity;
            return s;
        }

        /**
         * The {@code CameraEffect} asset id to play. Deliberately a BARE id, NOT the shared
         * {@link EffectRef} {@code {Id, DurationMs?}} group: a camera shake's duration lives
         * INSIDE the referenced asset (per first/third-person), and firing one is a single
         * fire-and-forget packet the client renders for the asset's own baked duration - the
         * engine exposes no per-use duration override anywhere on that path. A {@code DurationMs}
         * leaf here would be inert, so the leaf stays an id; vary the duration by referencing a
         * different {@code CameraEffect} asset.
         */
        @Nullable
        public String getEffectId() {
            return effectId;
        }

        @Nullable
        public Double getIntensity() {
            return intensity;
        }
    }

    /**
     * The native RootInteraction chain-fire reference (seam wave, decision 51b): an ID-REF-ONLY
     * {@code {Id}} naming a native Hytale {@code RootInteraction} chain to fire at this moment via
     * the {@code ziggfreed-common} chain-fire lift (engine-side scope). A step/moment/flair carrying
     * a {@code Presentation} can now trigger native interaction content by reference, never inlining
     * the chain body (decision 53's id-ref-only). An unresolvable id is a validator INFO (typo
     * detection) and a no-op at fire, never a throw.
     */
    public static final class Interaction {
        @Nullable protected String id;

        public static final BuilderCodec<Interaction> CODEC = BuilderCodec.builder(Interaction.class, Interaction::new)
                .appendInherited(new KeyedCodec<>("Id", Codec.STRING, false),
                        (o, v) -> o.id = v, o -> o.id, (o, p) -> o.id = p.id)
                .documentation("The native RootInteraction chain id to fire at this moment (id-ref-only; never inlines the chain body).").add()
                .build();

        @Nonnull
        public static Interaction of(@Nullable String id) {
            Interaction i = new Interaction();
            i.id = id;
            return i;
        }

        @Nullable
        public String getId() {
            return id;
        }

        /** True when {@link #id} is authored (a non-blank interaction id). */
        public boolean hasId() {
            return id != null && !id.isBlank();
        }
    }
}
