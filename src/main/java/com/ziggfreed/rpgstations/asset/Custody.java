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
 * ItemId/ResourceTypeId/Tags routes (the {@code Function} route stays deferred to phase-2 leg E,
 * same posture as {@link ActionInput} itself). When {@link #input} is absent, acceptance derives
 * from the resolved station's {@code Recipe.Conversions} inputs instead (the sawmill's "logs by
 * ResourceTypeId family" - zero extra authoring needed on top of the existing {@code Recipe}
 * group); an explicit {@link #input} is for a future non-Recipe custody item (e.g. the anvil's
 * weapon placement, leg E).
 *
 * <p>{@link #states} is nullable: authoring it opts the BLOCK's {@code State.Definitions} into
 * the empty/loaded hint flip (a pack-authored {@code BlockType} state pair, see
 * {@code station.StationService#flipCustodyState}); omitting it means custody still works
 * mechanically (placement/drain/auto-return) with no visual/hint flip - hint-only states are the
 * whole of THIS leg's visual scope (design's mechanism-first ruling; the display-entity visual
 * layer is a later Visuals leg).
 *
 * <pre>{@code
 * "Custody": { "MaxQuantity": 100, "States": { "Empty": "Default", "Loaded": "Loaded" } }
 * }</pre>
 */
public final class Custody {

    /** Maintainer decision (design decision log #5, 2026-07-21/22): whole-stack + top-up + this default. */
    public static final int DEFAULT_MAX_QUANTITY = 100;

    @Nullable protected Integer maxQuantity;
    @Nullable protected ActionInput input;
    @Nullable protected States states;

    public static final BuilderCodec<Custody> CODEC = BuilderCodec.builder(Custody.class, Custody::new)
            .appendInherited(new KeyedCodec<>("MaxQuantity", Codec.INTEGER, false),
                    (o, v) -> o.maxQuantity = v, o -> o.maxQuantity, (o, p) -> o.maxQuantity = p.maxQuantity).add()
            .appendInherited(new KeyedCodec<>("Input", ActionInput.CODEC, false),
                    (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input).add()
            .appendInherited(new KeyedCodec<>("States", States.CODEC, false),
                    (o, v) -> o.states = v, o -> o.states, (o, p) -> o.states = p.states).add()
            .build();

    public Custody() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
    @Nonnull
    public static Custody of(@Nullable Integer maxQuantity, @Nullable ActionInput input, @Nullable States states) {
        Custody c = new Custody();
        c.maxQuantity = maxQuantity;
        c.input = input;
        c.states = states;
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
}
