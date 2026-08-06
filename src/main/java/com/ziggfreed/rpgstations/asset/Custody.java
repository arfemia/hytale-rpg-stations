package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.common.codec.Rotation;

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
 * {@code station.StationService#flipCustodyState}) and, when its own nullable {@code Working} leaf
 * is authored, into the actively-working flip on top ({@code station.StationService
 * #enterWorkingState}); omitting it means custody still works mechanically (placement/drain/
 * auto-return) with no visual/hint flip.
 *
 * <p>{@link #display} is nullable (design section 9's Visuals leg, phase 2 leg G): authoring it
 * opts the placed input into a PLACED-AS-ENTITY visual (a static, network-replicated,
 * pickup-immune, physics-free prop entity rendering the placed item/block at the station's
 * anchor point - the maintainer-directed route over a Blockbench baked-node model swap) - see
 * {@code station.StationCustodyDisplay} for the engine-side spawn/despawn. Omitting it means
 * custody still works mechanically with no visual (the leg-C default).
 *
 * <pre>{@code
 * "Custody": { "MaxQuantity": 100,
 *              "States": { "Empty": "Default", "Loaded": "Loaded", "Working": "Lit" },
 *              "Display": { "Offset": { "Y": 0.55 }, "Scale": 1.0 } }
 * }</pre>
 */
public final class Custody {

    /** Maintainer decision (design decision log #5, 2026-07-21/22): whole-stack + top-up + this default. */
    public static final int DEFAULT_MAX_QUANTITY = 100;

    @Nullable protected Integer maxQuantity;
    @Nullable protected Boolean singleFamily;
    @Nullable protected ActionInput input;
    @Nullable protected States states;
    @Nullable protected Display display;

    public static final BuilderCodec<Custody> CODEC = BuilderCodec.builder(Custody.class, Custody::new)
            .appendInherited(new KeyedCodec<>("MaxQuantity", Codec.INTEGER, false),
                    (o, v) -> o.maxQuantity = v, o -> o.maxQuantity, (o, p) -> o.maxQuantity = p.maxQuantity)
            .documentation("The total item count this block's claim holds; reader-defaults to 100.")
            .addValidator(CodecWarnValidators.positive("Custody.MaxQuantity should be positive; it reader-defaults to 100 otherwise.")).add()
            .appendInherited(new KeyedCodec<>("SingleFamily", Codec.BOOLEAN, false),
                    (o, v) -> o.singleFamily = v, o -> o.singleFamily, (o, p) -> o.singleFamily = p.singleFamily)
            .documentation("When true the claim locks to the FIRST placed item's resource family: a later placement of a different family is refused until the claim empties. Default false (any accepted material mixes freely).").add()
            .appendInherited(new KeyedCodec<>("Input", ActionInput.CODEC, false),
                    (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input)
            .documentation("The explicit placement-acceptance matcher; absent derives acceptance from the resolved action's Recipe.Conversions inputs.").add()
            .appendInherited(new KeyedCodec<>("States", States.CODEC, false),
                    (o, v) -> o.states = v, o -> o.states, (o, p) -> o.states = p.states)
            .documentation("The block State.Definitions names custody flips between; null = no visual/hint flip.").add()
            .appendInherited(new KeyedCodec<>("Display", Display.CODEC, false),
                    (o, v) -> o.display = v, o -> o.display, (o, p) -> o.display = p.display)
            .documentation("Opts the placed input into a placed-as-entity prop visual at the block-top anchor; null = no visual.").add()
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
        return of(maxQuantity, null, input, states, display);
    }

    /** Java-side factory carrying every leaf incl. {@link #singleFamily}. */
    @Nonnull
    public static Custody of(@Nullable Integer maxQuantity, @Nullable Boolean singleFamily,
            @Nullable ActionInput input, @Nullable States states, @Nullable Display display) {
        Custody c = new Custody();
        c.maxQuantity = maxQuantity;
        c.singleFamily = singleFamily;
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

    /**
     * Does this claim lock to ONE resource family (decision 74)? When true, the first placement
     * fixes the claim's family and any later placement outside it is refused with
     * {@code ui.station.wrong_input} until the claim empties again - "50 oak OR 50 pine, never 100
     * mixed", the exclusivity a per-family quantity map could not express. Enforced in the accept
     * path ({@code station.StationCustody#acceptsFamily}).
     */
    @Nullable
    public Boolean getSingleFamily() {
        return singleFamily;
    }

    /** {@link #getSingleFamily()}, reader-defaulted to {@code false} (mixing allowed). */
    public boolean effectiveSingleFamily() {
        return singleFamily != null && singleFamily;
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

    /**
     * The block-state names custody flips between; nullable leaves each mean "no flip for that
     * side". Three INDEPENDENT, individually-nullable knobs, never a mode: a station may author any
     * subset (an {@code Empty}-only pair is legal, a {@code Working}-only station is legal), and an
     * unauthored leaf simply skips that flip.
     *
     * <p><b>{@link #working} (the actively-working look):</b> the state the engine holds the block
     * in WHILE a work step is actually executing there, flipping back to {@link #loaded} (claim
     * non-empty) or {@link #empty} (claim empty) the moment it is not - on step exit, program
     * completion, and EVERY session stop path (re-press, walk-off, damage, death, disconnect,
     * shutdown, {@code ANCHOR_LOST}, {@code PATH_BLOCKED}, {@code INPUTS_EXHAUSTED}, ...), plus
     * across a multi-station program's {@code Walk} phases. Semantics are "actively working", NOT
     * "has input in it": the cooking fire's burning look lives here, so placing raw fish on a cold
     * fire leaves it unlit until the cook beat begins. Applies to the block a step runs AT, so both
     * the primary station block and a claimed remote anchor get it (engine side:
     * {@code station.StationService#enterWorkingState}/{@code #exitWorkingState}). Which steps count
     * as work is the step's own {@code IsWork} knob ({@code asset.StationStep#effectiveIsWork}),
     * defaulting to "a step that both Consumes and Produces is a convert". Omitting {@code Working}
     * is byte-identical to the pre-knob behavior - no extra flip ever happens.
     *
     * <p>Every named state must exist in the block's own {@code BlockType.State.Definitions} (a
     * state variant is a generated {@code BlockType} asset); a name the block never authored is a
     * silent no-op, retried on the next flip.
     */
    public static final class States {
        @Nullable protected String empty;
        @Nullable protected String loaded;
        @Nullable protected String working;

        public static final BuilderCodec<States> CODEC = BuilderCodec.builder(States.class, States::new)
                .appendInherited(new KeyedCodec<>("Empty", Codec.STRING, false),
                        (o, v) -> o.empty = v, o -> o.empty, (o, p) -> o.empty = p.empty)
                .documentation("The block State.Definitions name shown while no input is held in custody.").add()
                .appendInherited(new KeyedCodec<>("Loaded", Codec.STRING, false),
                        (o, v) -> o.loaded = v, o -> o.loaded, (o, p) -> o.loaded = p.loaded)
                .documentation("The block State.Definitions name shown while custody holds input but no work is running.").add()
                .appendInherited(new KeyedCodec<>("Working", Codec.STRING, false),
                        (o, v) -> o.working = v, o -> o.working, (o, p) -> o.working = p.working)
                .documentation("The block State.Definitions name shown ONLY while a work step is actively executing at this block; reverts to Loaded/Empty on step exit and every session stop. Omit for no working flip.").add()
                .build();

        /**
         * Two-leaf factory (no {@link #working}); kept for callers that only compose the
         * empty/loaded pair. Prefer {@link #of(String, String, String)} in new code.
         */
        @Nonnull
        public static States of(@Nullable String empty, @Nullable String loaded) {
            return of(empty, loaded, null);
        }

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static States of(@Nullable String empty, @Nullable String loaded, @Nullable String working) {
            States s = new States();
            s.empty = empty;
            s.loaded = loaded;
            s.working = working;
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

        @Nullable
        public String getWorking() {
            return working;
        }
    }

    /**
     * The placed-input PLACED-AS-ENTITY visual (design section 9, phase 2 leg G): per-station
     * asset-authored knobs for the display prop's spatial fit relative to the station's block-top
     * anchor - the same point every cycle/swing/impact/rare-find moment already targets
     * ({@code blockX+0.5, blockY+0.5, blockZ+0.5}). All three leaves are nullable/orthogonal
     * (independently composable, never a mode): {@link #offset} (the shared {@link Vec3}
     * {@code {X,Y,Z}} group) shifts the anchor, {@link #scale} resizes the prop, and
     * {@link #rotation} (the shared {@link Rotation} {@code {Yaw,Pitch,Roll}} degrees group) turns
     * it about all three axes.
     *
     * <p><b>FACING-RELATIVE (round-8), not absolute world-space:</b> {@link #offset} and
     * {@link #rotation} are authored RELATIVE TO THE PLACED STATION BLOCK'S OWN FACING, not in
     * absolute world axes (the pre-round-8 simplification). {@code station.StationCustodyDisplay}
     * reads the placed block's own facing yaw at spawn time (the non-deprecated
     * {@code World#getBlockRotationIndex} -> {@code RotationTuple#yaw()}, resolved off the spawn
     * command buffer's store) and composes it two ways: the horizontal {@code Offset} (X/Z) is
     * ROTATED by that yaw (Y stays vertical), and the block's yaw is ADDED into the {@code Rotation}
     * group's {@code Yaw} axis - so a station block placed rotated carries its display prop's POSITION and
     * FACING around with it, and a {@code +Z} authored offset lands toward the SAME face of the block
     * for every placement orientation. <b>Convention:</b> authored {@code Offset.X}/{@code .Z} are in
     * the block's own horizontal frame under the engine's block-vector yaw convention; at a
     * DEFAULT-orientation placement (block yaw {@code None}/0deg) the
     * local frame equals the world frame and the yaw addition is 0, so <b>every pre-round-8 authored
     * value renders byte-identically</b> - existing packs need no blind re-tune (both shipped exemplars
     * authored only a vertical {@code Offset.Y}, untouched by the change; only the anvil's enhance
     * weapon adds a horizontal leaf this round). The composition math is the pure, unit-tested
     * {@code StationCustodyDisplay#resolveWorldOffset}/{@code #resolveRotationRadians} (each taking the
     * block yaw as a plain scalar); the ONE impure block-facing read try-guards to yaw 0 (an unloaded
     * chunk / bad read degrades to the old world-space behavior, never aborts the spawn). Grounded in
     * the shared source's {@code BlockRotationUtil}/{@code RotationTuple} discrete
     * 0/90/180/270 block-rotation model.
     *
     * <p><b>Rotation applies through {@code TransformComponent} on BOTH spawn routes, but is only
     * MIRRORED onto {@code HeadRotation} for the ITEM-entity route (critique m5):</b> the anvil's
     * placed weapon takes {@code ItemPropEntityService.buildHolder}'s item route, which writes the
     * full {@code Rotation3f} to the {@code TransformComponent} AND mirrors it onto a
     * {@code HeadRotation} component (matching the first-party bare-{@code ItemComponent} prop
     * parity). A block-shaped custody item (the sawmill's placed logs) takes the {@code BlockEntity}
     * route, which writes {@code TransformComponent} ONLY (no {@code HeadRotation}). So a future
     * block-shaped custody prop authoring a non-zero pitch/roll gets whatever the block-entity
     * transform renders, NOT the item-prop head-rotation path - do not assume the two routes tilt
     * identically.
     */
    public static final class Display {
        @Nullable protected Vec3 offset;
        @Nullable protected Double scale;
        @Nullable protected Rotation rotation;

        public static final BuilderCodec<Display> CODEC = BuilderCodec.builder(Display.class, Display::new)
                .appendInherited(new KeyedCodec<>("Offset", Vec3.CODEC, false),
                        (o, v) -> o.offset = v, o -> o.offset, (o, p) -> o.offset = p.offset)
                .documentation("Facing-relative shift off the block-top anchor: X/Z are in the placed block's own horizontal frame (+Z = its front), Y is vertical.").add()
                .appendInherited(new KeyedCodec<>("Scale", Codec.DOUBLE, false),
                        (o, v) -> o.scale = v, o -> o.scale, (o, p) -> o.scale = p.scale)
                .documentation("Uniform prop scale; defaults to 1.0 when absent or non-positive.").add()
                .appendInherited(new KeyedCodec<>("Rotation", Rotation.CODEC, false),
                        (o, v) -> o.rotation = v, o -> o.rotation, (o, p) -> o.rotation = p.rotation)
                .documentation("Facing-relative rotation in degrees; the placed block's own facing is added into Yaw at spawn.").add()
                .build();

        @Nonnull
        public static Display of(@Nullable Vec3 offset, @Nullable Double scale, @Nullable Rotation rotation) {
            Display d = new Display();
            d.offset = offset;
            d.scale = scale;
            d.rotation = rotation;
            return d;
        }

        @Nullable
        public Vec3 getOffset() {
            return offset;
        }

        @Nullable
        public Double getScale() {
            return scale;
        }

        @Nullable
        public Rotation getRotation() {
            return rotation;
        }

        /** {@link #scale}, reader-defaulted to {@code 1.0} when null/non-positive. */
        public double effectiveScale() {
            return scale != null && scale > 0 ? scale : 1.0;
        }
    }
}
