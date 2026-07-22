package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Session-scoped PLACED-INPUT custody (design section 9.4, phase-2 leg C): an authored group
 * opts a station (or one multi-action {@link ActionDef}) INTO the state-dependent F interaction -
 * empty station + a held matching stack places the WHOLE stack into a per-block claim (a repeat
 * press tops up with further matching held stacks, capped by {@link #maxQuantity}), loaded
 * station + F (owner only) starts the session drawing from that claim instead of the live
 * inventory. The claim itself lives in {@code station.StationCustodyClaim}/memory only (the
 * repo-wide no-per-player-persistence constraint) - this codec is just the AUTHORING knob.
 *
 * <p>{@link #input} is the placement-acceptance matcher, reusing {@link ActionInput}'s
 * ItemId/ResourceTypeId/Tags/Function routes (SMOKE-FIX S4: the {@code Function} route now
 * matches in {@code station.StationCustody#matchesInput} - the anvil's {@code enhance} action
 * relies on it for weapon placement). When {@link #input} is absent, acceptance derives
 * from the resolved station's {@code Recipe.Conversions} inputs instead (the sawmill's "logs by
 * ResourceTypeId family" - zero extra authoring needed on top of the existing {@code Recipe}
 * group); an explicit {@link #input} is for a future non-Recipe custody item (e.g. the anvil's
 * weapon placement, leg E).
 *
 * <p>{@link #states} is nullable: authoring it opts the BLOCK's {@code State.Definitions} into
 * the empty/loaded hint flip (a pack-authored {@code BlockType} state pair, see
 * {@code station.StationService#flipCustodyState}); omitting it means custody still works
 * mechanically (placement/drain/auto-return) with no visual/hint flip.
 *
 * <p>{@link #display} is nullable (design section 9's Visuals leg, phase 2 leg G): authoring it
 * opts the placed input into a PLACED-AS-ENTITY visual (a static, network-replicated,
 * pickup-immune, physics-free prop entity rendering the placed item/block at the station's
 * anchor point - the maintainer-directed route over a Blockbench baked-node model swap) - see
 * {@code station.StationCustodyDisplay} for the engine-side spawn/despawn. Omitting it means
 * custody still works mechanically with no visual (the leg-C default).
 *
 * <pre>{@code
 * "Custody": { "MaxQuantity": 100, "States": { "Empty": "Default", "Loaded": "Loaded" },
 *              "Display": { "Offset": { "Y": 0.55 }, "Scale": 1.0 } }
 * }</pre>
 */
public final class Custody {

    /** Maintainer decision (design decision log #5, 2026-07-21/22): whole-stack + top-up + this default. */
    public static final int DEFAULT_MAX_QUANTITY = 100;

    @Nullable protected Integer maxQuantity;
    @Nullable protected ActionInput input;
    @Nullable protected States states;
    @Nullable protected Display display;

    public static final BuilderCodec<Custody> CODEC = BuilderCodec.builder(Custody.class, Custody::new)
            .appendInherited(new KeyedCodec<>("MaxQuantity", Codec.INTEGER, false),
                    (o, v) -> o.maxQuantity = v, o -> o.maxQuantity, (o, p) -> o.maxQuantity = p.maxQuantity).add()
            .appendInherited(new KeyedCodec<>("Input", ActionInput.CODEC, false),
                    (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input).add()
            .appendInherited(new KeyedCodec<>("States", States.CODEC, false),
                    (o, v) -> o.states = v, o -> o.states, (o, p) -> o.states = p.states).add()
            .appendInherited(new KeyedCodec<>("Display", Display.CODEC, false),
                    (o, v) -> o.display = v, o -> o.display, (o, p) -> o.display = p.display).add()
            .build();

    public Custody() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static Custody of(@Nullable Integer maxQuantity, @Nullable ActionInput input, @Nullable States states) {
        return of(maxQuantity, input, states, null);
    }

    /** Java-side factory (leg G overload, adds {@link #display}); sets the same fields the codec fills. */
    @Nonnull
    public static Custody of(@Nullable Integer maxQuantity, @Nullable ActionInput input, @Nullable States states,
            @Nullable Display display) {
        Custody c = new Custody();
        c.maxQuantity = maxQuantity;
        c.input = input;
        c.states = states;
        c.display = display;
        return c;
    }

    @Nullable
    public Integer getMaxQuantity() {
        return maxQuantity;
    }

    /** {@link #maxQuantity}, reader-defaulted to {@link #DEFAULT_MAX_QUANTITY} when null/non-positive. */
    public int effectiveMaxQuantity() {
        return maxQuantity != null && maxQuantity > 0 ? maxQuantity : DEFAULT_MAX_QUANTITY;
    }

    @Nullable
    public ActionInput getInput() {
        return input;
    }

    @Nullable
    public States getStates() {
        return states;
    }

    @Nullable
    public Display getDisplay() {
        return display;
    }

    /** The block-state names custody flips between; nullable leaves each mean "no flip for that side". */
    public static final class States {
        @Nullable protected String empty;
        @Nullable protected String loaded;

        public static final BuilderCodec<States> CODEC = BuilderCodec.builder(States.class, States::new)
                .appendInherited(new KeyedCodec<>("Empty", Codec.STRING, false),
                        (o, v) -> o.empty = v, o -> o.empty, (o, p) -> o.empty = p.empty).add()
                .appendInherited(new KeyedCodec<>("Loaded", Codec.STRING, false),
                        (o, v) -> o.loaded = v, o -> o.loaded, (o, p) -> o.loaded = p.loaded).add()
                .build();

        @Nonnull
        public static States of(@Nullable String empty, @Nullable String loaded) {
            States s = new States();
            s.empty = empty;
            s.loaded = loaded;
            return s;
        }

        @Nullable
        public String getEmpty() {
            return empty;
        }

        @Nullable
        public String getLoaded() {
            return loaded;
        }
    }

    /**
     * The placed-input PLACED-AS-ENTITY visual (design section 9, phase 2 leg G): per-station
     * asset-authored knobs for the display prop's spatial fit relative to the station's block-top
     * anchor - the same point every cycle/swing/impact/rare-find moment already targets
     * ({@code blockX+0.5, blockY+0.5, blockZ+0.5}). All three leaves are nullable/orthogonal
     * (independently composable, never a mode): {@link #offset} shifts the anchor,
     * {@link #scale} resizes the prop, {@link #rotation} (degrees, world-space yaw) turns it.
     *
     * <p><b>World-space, not block-facing-relative (a documented simplification):</b> a station
     * block placed with a non-default {@code VariantRotation} orientation is NOT compensated for
     * here - {@link #offset} and {@link #rotation} apply in absolute world space. This codebase
     * has no existing "read a placed block's live facing yaw" helper to compose against (the
     * seat-mount route sidesteps the question entirely via the native {@code BlockMountAPI}), so
     * a non-zero horizontal offset on a station whose block CAN be placed rotated will land
     * consistently but not "toward the front" for every placement. Both shipped exemplars (the
     * sawmill's placed logs, the anvil's placed weapon) use a small/zero horizontal offset for
     * exactly this reason. A future leg can compose the offset against the block's own rotation
     * (see {@code BlockRotationUtil}/{@code RotationTuple} in the shared source) if a station
     * needs a large facing-relative offset.
     */
    public static final class Display {
        @Nullable protected Offset offset;
        @Nullable protected Double scale;
        @Nullable protected Double rotation;

        public static final BuilderCodec<Display> CODEC = BuilderCodec.builder(Display.class, Display::new)
                .appendInherited(new KeyedCodec<>("Offset", Offset.CODEC, false),
                        (o, v) -> o.offset = v, o -> o.offset, (o, p) -> o.offset = p.offset).add()
                .appendInherited(new KeyedCodec<>("Scale", Codec.DOUBLE, false),
                        (o, v) -> o.scale = v, o -> o.scale, (o, p) -> o.scale = p.scale).add()
                .appendInherited(new KeyedCodec<>("Rotation", Codec.DOUBLE, false),
                        (o, v) -> o.rotation = v, o -> o.rotation, (o, p) -> o.rotation = p.rotation).add()
                .build();

        @Nonnull
        public static Display of(@Nullable Offset offset, @Nullable Double scale, @Nullable Double rotation) {
            Display d = new Display();
            d.offset = offset;
            d.scale = scale;
            d.rotation = rotation;
            return d;
        }

        @Nullable
        public Offset getOffset() {
            return offset;
        }

        @Nullable
        public Double getScale() {
            return scale;
        }

        @Nullable
        public Double getRotation() {
            return rotation;
        }

        /** {@link #scale}, reader-defaulted to {@code 1.0} when null/non-positive. */
        public double effectiveScale() {
            return scale != null && scale > 0 ? scale : 1.0;
        }

        /** {@link #rotation} (degrees), reader-defaulted to {@code 0.0} when null. */
        public double effectiveRotationDegrees() {
            return rotation != null ? rotation : 0.0;
        }

        /** A relative {@code X}/{@code Y}/{@code Z} shift off the block-top anchor, each leaf independently nullable (default 0). */
        public static final class Offset {
            @Nullable protected Double x;
            @Nullable protected Double y;
            @Nullable protected Double z;

            public static final BuilderCodec<Offset> CODEC = BuilderCodec.builder(Offset.class, Offset::new)
                    .appendInherited(new KeyedCodec<>("X", Codec.DOUBLE, false),
                            (o, v) -> o.x = v, o -> o.x, (o, p) -> o.x = p.x).add()
                    .appendInherited(new KeyedCodec<>("Y", Codec.DOUBLE, false),
                            (o, v) -> o.y = v, o -> o.y, (o, p) -> o.y = p.y).add()
                    .appendInherited(new KeyedCodec<>("Z", Codec.DOUBLE, false),
                            (o, v) -> o.z = v, o -> o.z, (o, p) -> o.z = p.z).add()
                    .build();

            @Nonnull
            public static Offset of(@Nullable Double x, @Nullable Double y, @Nullable Double z) {
                Offset o = new Offset();
                o.x = x;
                o.y = y;
                o.z = z;
                return o;
            }

            @Nullable
            public Double getX() {
                return x;
            }

            @Nullable
            public Double getY() {
                return y;
            }

            @Nullable
            public Double getZ() {
                return z;
            }
        }
    }
}
