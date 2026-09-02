package com.ziggfreed.rpgstations.asset;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditorSectionStart;
import com.ziggfreed.common.codec.InheritMapCodec;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.common.codec.Vec3i;
import com.ziggfreed.common.codec.Rotation;

/**
 * Session-scoped PLACED-INPUT custody (design section 9.4, phase-2 leg C): an authored group
 * opts a station (or one multi-action {@link ActionDef}) INTO the state-dependent F interaction -
 * empty station + a held matching stack places the WHOLE stack into a per-block claim (a repeat
 * press tops up with further matching held stacks, capped by {@link #maxQuantity}), loaded
 * station + F (owner only) starts the session drawing from that claim instead of the live
 * inventory. The claim itself is CHUNK-PERSISTED world state (ziggfreed-common's per-block stash
 * on the block's own chunk section, read through {@code station.StationCustodyClaim}), so placed
 * materials survive a logoff and a restart - this codec is just the AUTHORING knob.
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

    /**
     * The reserved socket id a custody group with no authored {@link #sockets} synthesizes: ONE
     * degenerate socket whose effective leaves ARE the custody-level values, so every pre-socket
     * station decodes and behaves identically with the socket machinery underneath. Socket ids are
     * matched lowercase (the map key convention: author them lower-case-stable).
     */
    public static final String MAIN_SOCKET_ID = "main";

    @Nullable protected Integer maxQuantity;
    @Nullable protected Boolean singleFamily;
    @Nullable protected ActionInput input;
    @Nullable protected States states;
    @Nullable protected Display display;
    @Nullable protected Share share;
    @Nullable protected Map<String, Socket> sockets;

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
            .appendInherited(new KeyedCodec<>("Share", Share.CODEC, false),
                    (o, v) -> o.share = v, o -> o.share, (o, p) -> o.share = p.share)
            .documentation("Who besides an owner may place, work from, or take back placed materials here; every leaf defaults false (owner-only). A socket may override any leaf for its own pile.")
            .metadata(new UIEditorSectionStart("Sharing")).add()
            .appendInherited(new KeyedCodec<>("Sockets", new InheritMapCodec<>(Socket.CODEC), false),
                    (o, v) -> o.sockets = v, o -> o.sockets, (o, p) -> o.sockets = p.sockets)
            .documentation("Named placement slots by socket id (author ids lower-case; matching is case-insensitive), each holding its own independently owned pile. Merged per socket id under Parent inheritance, per leaf within a socket. Omit for the classic single-pile custody: the custody-level leaves above then act as the one implicit socket.")
            .metadata(new UIEditorSectionStart("Sockets")).add()
            .afterDecode((Custody custody, ExtraInfo extraInfo) -> {
                if (custody.sockets == null) {
                    return;
                }
                for (Map.Entry<String, Socket> e : custody.sockets.entrySet()) {
                    Socket socket = e.getValue();
                    if (socket == null) {
                        continue;
                    }
                    if (!socket.hasExactlyOneRoute()) {
                        extraInfo.getValidationResults().warn("Custody.Sockets['" + e.getKey()
                                + "'] should author exactly one of Item | Block; a socket authoring both or neither is ignored at runtime.");
                    }
                    if (socket.maxQuantity != null && custody.maxQuantity != null
                            && socket.maxQuantity > custody.maxQuantity) {
                        extraInfo.getValidationResults().warn("Custody.Sockets['" + e.getKey()
                                + "'].MaxQuantity exceeds the custody-level MaxQuantity; the effective capacity is the smaller of the two.");
                    }
                }
            })
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

    /** Java-side factory carrying every pre-socket leaf incl. {@link #singleFamily}. */
    @Nonnull
    public static Custody of(@Nullable Integer maxQuantity, @Nullable Boolean singleFamily,
            @Nullable ActionInput input, @Nullable States states, @Nullable Display display) {
        return of(maxQuantity, singleFamily, input, states, display, null, null);
    }

    /** Java-side factory carrying EVERY leaf ({@link #share} + {@link #sockets} included); sets the same fields the codec fills. */
    @Nonnull
    public static Custody of(@Nullable Integer maxQuantity, @Nullable Boolean singleFamily,
            @Nullable ActionInput input, @Nullable States states, @Nullable Display display,
            @Nullable Share share, @Nullable Map<String, Socket> sockets) {
        Custody c = new Custody();
        c.maxQuantity = maxQuantity;
        c.singleFamily = singleFamily;
        c.input = input;
        c.states = states;
        c.display = display;
        c.share = share;
        c.sockets = sockets;
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

    /** The custody-level {@link Share} defaults every socket inherits per leaf; null = owner-only everywhere. */
    @Nullable
    public Share getShare() {
        return share;
    }

    /** The authored socket map (by socket id, insertion order = placement priority), or null for the classic single-pile custody. */
    @Nullable
    public Map<String, Socket> getSockets() {
        return sockets;
    }

    /** True when at least one socket is authored; false = the degenerate single-{@value #MAIN_SOCKET_ID}-socket custody. */
    public boolean hasAuthoredSockets() {
        return sockets != null && !sockets.isEmpty();
    }

    /**
     * The EFFECTIVE socket list, in authored order (authored order is placement priority). With no
     * authored {@link #sockets}, synthesizes exactly ONE degenerate {@link ResolvedSocket} with the
     * reserved id {@value #MAIN_SOCKET_ID} whose leaves ARE the custody-level values (the Item
     * route, whole-stack placement, {@link #getInput()} as the match, the custody cap /
     * single-family / display / share), so every pre-socket station behaves identically. An
     * authored socket resolves per leaf: its capacity is {@code min(socket.MaxQuantity,
     * Custody.MaxQuantity)}, its {@code SingleFamily} and every {@code Share} leaf fall back to the
     * custody-level value when unauthored, and its id is lowercased (socket ids match
     * case-insensitively everywhere). A socket authoring both routes or neither is skipped here -
     * ignored at runtime, warned about at decode and by the validator, never a load failure.
     */
    @Nonnull
    public List<ResolvedSocket> effectiveSockets() {
        if (!hasAuthoredSockets()) {
            return List.of(new ResolvedSocket(MAIN_SOCKET_ID, true, input, null, null,
                    effectiveMaxQuantity(), effectiveSingleFamily(), false, display,
                    shareLeaf(null, Share::getPlace), shareLeaf(null, Share::getUse),
                    shareLeaf(null, Share::getReclaim), null));
        }
        List<ResolvedSocket> out = new ArrayList<>(sockets.size());
        for (Map.Entry<String, Socket> e : sockets.entrySet()) {
            String rawId = e.getKey();
            Socket socket = e.getValue();
            if (rawId == null || rawId.isBlank() || socket == null || !socket.hasExactlyOneRoute()) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            boolean itemRoute = socket.getItem() != null;
            ActionInput match = itemRoute ? socket.getItem().getMatch() : socket.getBlock().getMatch();
            Integer placePerPress = itemRoute ? socket.getItem().getPlacePerPress() : null;
            Vec3i blockAt = itemRoute ? null : socket.getBlock().getAt();
            int custodyMax = effectiveMaxQuantity();
            int socketMax = socket.getMaxQuantity() != null && socket.getMaxQuantity() > 0
                    ? Math.min(socket.getMaxQuantity(), custodyMax) : custodyMax;
            boolean single = socket.getSingleFamily() != null ? socket.getSingleFamily() : effectiveSingleFamily();
            boolean required = socket.getRequired() != null && socket.getRequired();
            out.add(new ResolvedSocket(id, itemRoute, match, placePerPress, blockAt,
                    socketMax, single, required, socket.getDisplay(),
                    shareLeaf(socket.getShare(), Share::getPlace),
                    shareLeaf(socket.getShare(), Share::getUse),
                    shareLeaf(socket.getShare(), Share::getReclaim),
                    socket.getLabel()));
        }
        return out;
    }

    /** One Share leaf resolved socket-first: the socket's own value, else the custody-level one, else false. */
    private boolean shareLeaf(@Nullable Share socketShare, @Nonnull Function<Share, Boolean> leaf) {
        Boolean own = socketShare != null ? leaf.apply(socketShare) : null;
        if (own != null) {
            return own;
        }
        Boolean custodyLevel = share != null ? leaf.apply(share) : null;
        return custodyLevel != null && custodyLevel;
    }

    /**
     * The block-state names custody flips between; nullable leaves each mean "no flip for that
     * side". Five INDEPENDENT, individually-nullable knobs, never a mode: a station may author any
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
     * <p><b>{@link #ready} / {@link #overdone} (the doneness pair):</b> the looks of a produced
     * batch waiting in a custody pile under an open {@code Doneness} ready window ({@code Ready}),
     * and of a pile whose window expired and collapsed to its {@code Overdone} items
     * ({@code Overdone}). {@code Ready} shows once output lands with a window open and between
     * work (the {@code Working} look wins while a work step actually runs); {@code Overdone}
     * shows from the expiry settle until the pile is gathered or reloaded. Both are inert on a
     * station whose recipe authors no {@code Doneness}.
     *
     * <p><b>The state SET is closed by code - overlay is not extension.</b> These five leaves are
     * every state the engine will ever flip to; an extension or pack may re-skin the NAME each
     * leaf resolves to (point {@code Loaded} at its own block-state variant), but can never add a
     * sixth state, because no collection exists here to append to.
     *
     * <p>Every named state must exist in the block's own {@code BlockType.State.Definitions} (a
     * state variant is a generated {@code BlockType} asset); a name the block never authored is a
     * silent no-op, retried on the next flip.
     */
    public static final class States {
        @Nullable protected String empty;
        @Nullable protected String loaded;
        @Nullable protected String working;
        @Nullable protected String ready;
        @Nullable protected String overdone;

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
                .appendInherited(new KeyedCodec<>("Ready", Codec.STRING, false),
                        (o, v) -> o.ready = v, o -> o.ready, (o, p) -> o.ready = p.ready)
                .documentation("The block State.Definitions name shown while a produced batch waits in custody under an open Doneness ready window (the Working look wins while work actually runs). Omit for no ready flip.").add()
                .appendInherited(new KeyedCodec<>("Overdone", Codec.STRING, false),
                        (o, v) -> o.overdone = v, o -> o.overdone, (o, p) -> o.overdone = p.overdone)
                .documentation("The block State.Definitions name shown after a ready window expired and the pile collapsed to its Overdone items, until that pile is gathered or reloaded. Omit for no overdone flip.").add()
                .build();

        /**
         * Two-leaf factory (no {@link #working}); kept for callers that only compose the
         * empty/loaded pair. Prefer {@link #of(String, String, String)} in new code.
         */
        @Nonnull
        public static States of(@Nullable String empty, @Nullable String loaded) {
            return of(empty, loaded, null);
        }

        /** Three-leaf factory (no doneness pair); kept for callers pre-dating the ready window. */
        @Nonnull
        public static States of(@Nullable String empty, @Nullable String loaded, @Nullable String working) {
            return of(empty, loaded, working, null, null);
        }

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static States of(@Nullable String empty, @Nullable String loaded, @Nullable String working,
                @Nullable String ready, @Nullable String overdone) {
            States s = new States();
            s.empty = empty;
            s.loaded = loaded;
            s.working = working;
            s.ready = ready;
            s.overdone = overdone;
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

        /** The waiting-Ready look under an open doneness window, or null for no ready flip. */
        @Nullable
        public String getReady() {
            return ready;
        }

        /** The collapsed-Overdone look after a window expired, or null for no overdone flip. */
        @Nullable
        public String getOverdone() {
            return overdone;
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
                .documentation("Uniform prop scale, a fraction of a real block for a block-shaped item; defaults to 1.0 (full block size) when absent or non-positive.").add()
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

    /**
     * Who besides the owner may touch placed materials - three ORTHOGONAL booleans, never a mode,
     * each defaulting false (owner-only, the classic behavior). Authorable at the custody level
     * (the default for every socket) AND per socket (each leaf overrides independently).
     *
     * <ul>
     *   <li>{@link #place} - a non-owner may START a pile in this socket while it is EMPTY (the
     *       first contributor then owns that pile until it drains empty again). It never opens a
     *       NON-empty pile: a pile always has exactly one owner and materials never co-mingle.</li>
     *   <li>{@link #use} - a non-owner may engage work that consumes from this socket's pile.</li>
     *   <li>{@link #reclaim} - a non-owner may take this socket's pile back out (press-F
     *       retrieval on its display prop).</li>
     * </ul>
     */
    public static final class Share {
        @Nullable protected Boolean place;
        @Nullable protected Boolean use;
        @Nullable protected Boolean reclaim;

        public static final BuilderCodec<Share> CODEC = BuilderCodec.builder(Share.class, Share::new)
                .appendInherited(new KeyedCodec<>("Place", Codec.BOOLEAN, false),
                        (o, v) -> o.place = v, o -> o.place, (o, p) -> o.place = p.place)
                .documentation("May a non-owner start a pile here while it is EMPTY? The first contributor owns that pile until it drains empty again; a non-empty pile never accepts a second player's materials. Default false.").add()
                .appendInherited(new KeyedCodec<>("Use", Codec.BOOLEAN, false),
                        (o, v) -> o.use = v, o -> o.use, (o, p) -> o.use = p.use)
                .documentation("May a non-owner engage work that consumes from this pile? Default false (owner-only).").add()
                .appendInherited(new KeyedCodec<>("Reclaim", Codec.BOOLEAN, false),
                        (o, v) -> o.reclaim = v, o -> o.reclaim, (o, p) -> o.reclaim = p.reclaim)
                .documentation("May a non-owner take this pile back out of the station? Default false (owner-only).").add()
                .build();

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static Share of(@Nullable Boolean place, @Nullable Boolean use, @Nullable Boolean reclaim) {
            Share s = new Share();
            s.place = place;
            s.use = use;
            s.reclaim = reclaim;
            return s;
        }

        @Nullable
        public Boolean getPlace() {
            return place;
        }

        @Nullable
        public Boolean getUse() {
            return use;
        }

        @Nullable
        public Boolean getReclaim() {
            return reclaim;
        }
    }

    /**
     * ONE named placement slot of a multi-socket custody: its own independently owned pile, its
     * own acceptance matcher, capacity, display prop and share posture. Authored under
     * {@code Custody.Sockets} keyed by socket id (author ids lower-case; matching is
     * case-insensitive; authored order is placement priority).
     *
     * <p><b>Exactly one of {@link #item} | {@link #block}.</b> An {@code Item} socket holds placed
     * item stacks in its own pile; a {@code Block} socket is a REAL WORLD BLOCK beside the station
     * (a pot on the fire) - nothing is stored for it, the world block IS its state, and
     * {@code MaxQuantity}/{@code SingleFamily}/{@code Share}/{@code Display} are meaningless on
     * one. A socket authoring both routes or neither is ignored at runtime (warned, never a load
     * failure). A future route would be a third sibling group beside these two - additive, no
     * reserved fields.
     */
    public static final class Socket {
        @Nullable protected ItemRoute item;
        @Nullable protected BlockRoute block;
        @Nullable protected Integer maxQuantity;
        @Nullable protected Boolean singleFamily;
        @Nullable protected Boolean required;
        @Nullable protected Display display;
        @Nullable protected Share share;
        @Nullable protected String label;

        public static final BuilderCodec<Socket> CODEC = BuilderCodec.builder(Socket.class, Socket::new)
                .appendInherited(new KeyedCodec<>("Item", ItemRoute.CODEC, false),
                        (o, v) -> o.item = v, o -> o.item, (o, p) -> o.item = p.item)
                .documentation("The placed-ITEM route: this socket holds a pile of placed stacks. Exactly one of Item | Block.").add()
                .appendInherited(new KeyedCodec<>("Block", BlockRoute.CODEC, false),
                        (o, v) -> o.block = v, o -> o.block, (o, p) -> o.block = p.block)
                .documentation("The world-BLOCK route: this socket is satisfied by a real block standing at a facing-relative offset. Nothing is stored for it. Exactly one of Item | Block.").add()
                .appendInherited(new KeyedCodec<>("MaxQuantity", Codec.INTEGER, false),
                        (o, v) -> o.maxQuantity = v, o -> o.maxQuantity, (o, p) -> o.maxQuantity = p.maxQuantity)
                .documentation("This socket's own item cap; the effective capacity is the smaller of this and the custody-level MaxQuantity. Absent = the custody-level cap.")
                .addValidator(CodecWarnValidators.positive("Socket.MaxQuantity should be positive; it falls back to the custody-level cap otherwise.")).add()
                .appendInherited(new KeyedCodec<>("SingleFamily", Codec.BOOLEAN, false),
                        (o, v) -> o.singleFamily = v, o -> o.singleFamily, (o, p) -> o.singleFamily = p.singleFamily)
                .documentation("Locks THIS socket's pile to the first placed item's resource family. Absent = the custody-level SingleFamily.").add()
                .appendInherited(new KeyedCodec<>("Required", Codec.BOOLEAN, false),
                        (o, v) -> o.required = v, o -> o.required, (o, p) -> o.required = p.required)
                .documentation("Must this socket be satisfied before work can start? An Item socket needs a non-empty pile; a Block socket needs its matching world block (re-checked while working: a vanished required block ends the session). Default false.").add()
                .appendInherited(new KeyedCodec<>("Display", Display.CODEC, false),
                        (o, v) -> o.display = v, o -> o.display, (o, p) -> o.display = p.display)
                .documentation("This socket's own placed-as-entity prop (same knobs as the custody-level Display); omit and this socket renders nothing.").add()
                .appendInherited(new KeyedCodec<>("Share", Share.CODEC, false),
                        (o, v) -> o.share = v, o -> o.share, (o, p) -> o.share = p.share)
                .documentation("Per-socket share overrides; each leaf falls back to the custody-level Share, then to false.").add()
                .appendInherited(new KeyedCodec<>("Label", Codec.STRING, false),
                        (o, v) -> o.label = v, o -> o.label, (o, p) -> o.label = p.label)
                .documentation("A lang key naming this socket in player-facing refusals ('the meat rack', 'the pot'); omit and refusals stay generic.").add()
                .build();

        /** Java-side factory; sets the same fields the codec fills. */
        @Nonnull
        public static Socket of(@Nullable ItemRoute item, @Nullable BlockRoute block,
                @Nullable Integer maxQuantity, @Nullable Boolean singleFamily, @Nullable Boolean required,
                @Nullable Display display, @Nullable Share share, @Nullable String label) {
            Socket s = new Socket();
            s.item = item;
            s.block = block;
            s.maxQuantity = maxQuantity;
            s.singleFamily = singleFamily;
            s.required = required;
            s.display = display;
            s.share = share;
            s.label = label;
            return s;
        }

        @Nullable
        public ItemRoute getItem() {
            return item;
        }

        @Nullable
        public BlockRoute getBlock() {
            return block;
        }

        @Nullable
        public Integer getMaxQuantity() {
            return maxQuantity;
        }

        @Nullable
        public Boolean getSingleFamily() {
            return singleFamily;
        }

        @Nullable
        public Boolean getRequired() {
            return required;
        }

        @Nullable
        public Display getDisplay() {
            return display;
        }

        @Nullable
        public Share getShare() {
            return share;
        }

        @Nullable
        public String getLabel() {
            return label;
        }

        /** True when EXACTLY one of {@link #item}/{@link #block} is authored (the exactly-one-of contract). */
        public boolean hasExactlyOneRoute() {
            return (item != null) ^ (block != null);
        }

        /**
         * The placed-ITEM route of one socket: what the socket accepts and how much of a held
         * stack one press moves.
         */
        public static final class ItemRoute {
            @Nullable protected ActionInput match;
            @Nullable protected Integer placePerPress;

            public static final BuilderCodec<ItemRoute> CODEC = BuilderCodec.builder(ItemRoute.class, ItemRoute::new)
                    .appendInherited(new KeyedCodec<>("Match", ActionInput.CODEC, false),
                            (o, v) -> o.match = v, o -> o.match, (o, p) -> o.match = p.match)
                    .documentation("What this socket accepts (the ItemId/ResourceTypeId/Tags/Function routes). Absent derives acceptance from the action's Recipe.Conversions inputs, exactly like the custody-level Input.").add()
                    .appendInherited(new KeyedCodec<>("PlacePerPress", Codec.INTEGER, false),
                            (o, v) -> o.placePerPress = v, o -> o.placePerPress,
                            (o, p) -> o.placePerPress = p.placePerPress)
                    .documentation("How many items one press moves in; absent = the whole held stack (the classic press). Author 1 for one-at-a-time loading.")
                    .addValidator(CodecWarnValidators.positive("Socket.Item.PlacePerPress should be positive; it falls back to the whole held stack otherwise.")).add()
                    .build();

            /** Java-side factory; sets the same fields the codec fills. */
            @Nonnull
            public static ItemRoute of(@Nullable ActionInput match, @Nullable Integer placePerPress) {
                ItemRoute r = new ItemRoute();
                r.match = match;
                r.placePerPress = placePerPress;
                return r;
            }

            @Nullable
            public ActionInput getMatch() {
                return match;
            }

            @Nullable
            public Integer getPlacePerPress() {
                return placePerPress;
            }
        }

        /**
         * The world-BLOCK route of one socket: satisfied by a real block standing at {@link #at}
         * (a whole-block offset in the STATION block's own facing frame - {@code +Z} its front,
         * {@code +X} its right, {@code Y} vertical, exactly the {@code Custody.Display} offset
         * convention) whose base item identity satisfies {@link #match}. Nothing is stored for a
         * block socket: the world block IS its state.
         */
        public static final class BlockRoute {
            @Nullable protected Vec3i at;
            @Nullable protected ActionInput match;

            public static final BuilderCodec<BlockRoute> CODEC = BuilderCodec.builder(BlockRoute.class, BlockRoute::new)
                    .appendInherited(new KeyedCodec<>("At", Vec3i.CODEC, false),
                            (o, v) -> o.at = v, o -> o.at, (o, p) -> o.at = p.at)
                    .documentation("The whole-block offset from the station block, in its own facing frame (+Z = its front, +X = its right, Y vertical); rotates with the placed block. Absent means the station block's own cell.").add()
                    .appendInherited(new KeyedCodec<>("Match", ActionInput.CODEC, false),
                            (o, v) -> o.match = v, o -> o.match, (o, p) -> o.match = p.match)
                    .documentation("What block satisfies this socket, matched against the block's base ITEM identity (id, resource families, tags). Absent accepts any non-air block.").add()
                    .build();

            /** Java-side factory; sets the same fields the codec fills. */
            @Nonnull
            public static BlockRoute of(@Nullable Vec3i at, @Nullable ActionInput match) {
                BlockRoute r = new BlockRoute();
                r.at = at;
                r.match = match;
                return r;
            }

            @Nullable
            public Vec3i getAt() {
                return at;
            }

            @Nullable
            public ActionInput getMatch() {
                return match;
            }
        }
    }

    /**
     * One socket RESOLVED for the engine: authored leaves folded with the custody-level defaults
     * ({@link #effectiveSockets()}'s output), so runtime code reads one flat view and never
     * re-derives a fallback. {@code maxQuantity} is already the min-of-caps; {@code singleFamily}
     * and the three share leaves already fell back custody-first; {@code id} is lowercased.
     * {@code match} may be null - acceptance then derives from the action's own
     * {@code Recipe.Conversions} inputs (an Item socket) or any non-air block (a Block socket).
     */
    public record ResolvedSocket(@Nonnull String id, boolean itemRoute, @Nullable ActionInput match,
            @Nullable Integer placePerPress, @Nullable Vec3i blockAt, int maxQuantity,
            boolean singleFamily, boolean required, @Nullable Display display,
            boolean sharePlace, boolean shareUse, boolean shareReclaim, @Nullable String label) {

        /** True for the world-block route (nothing stored; the world block is the state). */
        public boolean blockRoute() {
            return !itemRoute;
        }
    }
}
