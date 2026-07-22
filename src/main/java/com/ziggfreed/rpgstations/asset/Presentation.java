package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * One presentation moment: sound/particles/animation/camera/shake asset ids. RpgStations'
 * OWN copy of the MMO's {@code asset.type.Presentation} shared value type (design section
 * 4.1/4.4.3 - deliberate small duplication, not a common lift, since the two copies diverge:
 * the MMO keeps a {@code Feedback} leaf that only makes sense against its own
 * {@code FeedbackService}; RpgStations drops it and gains {@link #shake}).
 *
 * <p>Every leaf is {@code appendInherited} so a station whose {@code Parent} sibling
 * partially overrides a nested {@code Presentation} object still inherits the leaves it did
 * not mention.
 */
public final class Presentation {

    @Nullable protected String sound;
    @Nullable protected String particles;
    @Nullable protected String animation;
    @Nullable protected String animationItem;
    @Nullable protected String animationSlot;
    @Nullable protected String camera;
    @Nullable protected Shake shake;

    public static final BuilderCodec<Presentation> CODEC = BuilderCodec.builder(Presentation.class, Presentation::new)
            .appendInherited(new KeyedCodec<>("Sound", Codec.STRING, false),
                    (o, v) -> o.sound = v, o -> o.sound, (o, p) -> o.sound = p.sound).add()
            .appendInherited(new KeyedCodec<>("Particles", Codec.STRING, false),
                    (o, v) -> o.particles = v, o -> o.particles, (o, p) -> o.particles = p.particles).add()
            .appendInherited(new KeyedCodec<>("Animation", Codec.STRING, false),
                    (o, v) -> o.animation = v, o -> o.animation, (o, p) -> o.animation = p.animation).add()
            .appendInherited(new KeyedCodec<>("AnimationItem", Codec.STRING, false),
                    (o, v) -> o.animationItem = v, o -> o.animationItem, (o, p) -> o.animationItem = p.animationItem).add()
            .appendInherited(new KeyedCodec<>("AnimationSlot", Codec.STRING, false),
                    (o, v) -> o.animationSlot = v, o -> o.animationSlot, (o, p) -> o.animationSlot = p.animationSlot).add()
            .appendInherited(new KeyedCodec<>("Camera", Codec.STRING, false),
                    (o, v) -> o.camera = v, o -> o.camera, (o, p) -> o.camera = p.camera).add()
            .appendInherited(new KeyedCodec<>("Shake", Shake.CODEC, false),
                    (o, v) -> o.shake = v, o -> o.shake, (o, p) -> o.shake = p.shake).add()
            .build();

    public Presentation() {
    }

    /** Java-side factory carrying only a {@code Sound}. */
    @Nonnull
    public static Presentation ofSound(@Nullable String sound) {
        Presentation p = new Presentation();
        p.sound = sound;
        return p;
    }

    /** Java-side factory carrying a {@code Sound} and/or {@code Particles}. */
    @Nonnull
    public static Presentation of(@Nullable String sound, @Nullable String particles) {
        Presentation p = new Presentation();
        p.sound = sound;
        p.particles = particles;
        return p;
    }

    /** Fully-populated Java-side factory; does NOT touch the codec or JSON keys. */
    @Nonnull
    public static Presentation of(@Nullable String sound, @Nullable String particles,
            @Nullable String animation, @Nullable String animationItem, @Nullable String animationSlot,
            @Nullable String camera, @Nullable Shake shake) {
        Presentation p = new Presentation();
        p.sound = sound;
        p.particles = particles;
        p.animation = animation;
        p.animationItem = animationItem;
        p.animationSlot = animationSlot;
        p.camera = camera;
        p.shake = shake;
        return p;
    }

    @Nullable
    public String getSound() {
        return sound;
    }

    @Nullable
    public String getParticles() {
        return particles;
    }

    @Nullable
    public String getAnimation() {
        return animation;
    }

    @Nullable
    public String getAnimationItem() {
        return animationItem;
    }

    @Nullable
    public String getAnimationSlot() {
        return animationSlot;
    }

    @Nullable
    public String getCamera() {
        return camera;
    }

    /** One-shot camera shake (nullable); null = no shake. */
    @Nullable
    public Shake getShake() {
        return shake;
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
                        (o, v) -> o.effectId = v, o -> o.effectId, (o, p) -> o.effectId = p.effectId).add()
                .appendInherited(new KeyedCodec<>("Intensity", Codec.DOUBLE, false),
                        (o, v) -> o.intensity = v, o -> o.intensity, (o, p) -> o.intensity = p.intensity).add()
                .build();

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static Shake of(@Nullable String effectId, @Nullable Double intensity) {
            Shake s = new Shake();
            s.effectId = effectId;
            s.intensity = intensity;
            return s;
        }

        @Nullable
        public String getEffectId() {
            return effectId;
        }

        @Nullable
        public Double getIntensity() {
            return intensity;
        }
    }
}
