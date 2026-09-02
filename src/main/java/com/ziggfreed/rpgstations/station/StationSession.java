package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.effect.AppliedEffectTracker;
import com.ziggfreed.common.entity.performer.StationPerformer;
import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * One player's live, in-memory work session at a station block. Player-anchored: transient,
 * never persisted - a disconnect or death simply tears it down ({@code StationService#stop}).
 * The resolved config values (cycle cadence, hold effect id, ...) are snapshotted at start by
 * {@code StationService.toggle} so a mid-session catalog reload never half-changes a running
 * loop.
 *
 * <p>The session deliberately holds NO per-cycle progression accounting of its own: a listening
 * mod builds whatever running totals it wants from the fired {@code StationCycleCompletedEvent}s.
 * This session keeps only what the ENGINE itself needs: identity/anchors, the resolved config
 * snapshot, cadence, cycle count, item tallies (for the standalone summary HUD), and swing/impact
 * scheduling. {@link #sessionId} is what the loot engine and the api events key their
 * bookkeeping/dispatch off of.
 *
 * <p>Mutable fields are written on the world thread only (start + the frame drain); the
 * {@link #stopped} flag is the one cross-thread idempotency gate.
 */
final class StationSession {

    /** New in RPG Stations (design section 4.1): a per-session id for event dispatch + loot bookkeeping. */
    final UUID sessionId = UUID.randomUUID();

    // Identity + anchors.
    UUID playerUuid;
    Ref<EntityStore> ref;
    PlayerRef playerRef;
    String stationId;
    /**
     * The action id this session resolved at engage: the first entry of the station's ordered
     * {@code Actions} list whose {@code Select} matched the held or placed material. Fixed for the
     * WHOLE session (re-selected only on the next fresh engage, never mid-session) - every
     * {@code ActionResolver.resolve(asset, ...)} call this session's own code paths make MUST use
     * this field once a session exists.
     */
    String actionId;
    /** Occupancy key: {@code "<worldUuid>:<x>:<y>:<z>"} (enforces {@code Block.Exclusive}). */
    String blockKey;
    int blockX;
    int blockY;
    int blockZ;
    /**
     * The registered block-TYPE id at the primary block when the session started
     * (state-variant-distinct, {@code StationService#blockTypeIdAt}) - the block-gone check's
     * FALLBACK comparand, used only for a block that resolves to no containing Item at all (see
     * {@link #startBlockItemId}).
     */
    @Nullable
    String startBlockTypeId;
    /**
     * The primary block's own ITEM id at engage ({@code StationService#blockItemIdAt}) - the
     * PRIMARY block-gone comparand. A state flip ({@code Empty}/{@code Loaded}/{@code Working})
     * rewrites the block-type id to a distinct generated state-variant key, so the type-id compare
     * alone reads an in-session flip as "the station is gone"; every state variant of one block
     * resolves to the SAME {@code BlockType#getItem()} id, so comparing by item id survives a flip
     * while a real break/replace still ends the session. {@code null} for a block with no
     * containing Item (the type-id fallback then applies).
     */
    @Nullable
    String startBlockItemId;

    // Start transform (walk-off delta).
    double originX;
    double originY;
    double originZ;

    // Resolved config snapshot (reader defaults already applied).
    long cycleMs;
    long maxDurationMs;
    double maxMoveSq;
    boolean exclusive;
    boolean movementLock;
    String holdEffectId;
    boolean interruptOnDamage;
    /**
     * True when this session is using the native BLOCK mount hold (BlockMountAPI) instead of
     * the effect-mode movement lock. Set once at engage from {@code Hold.Mount.Surface} being
     * {@code "Block"} (or the group being authored with no recognized {@code Surface} - see
     * {@code StationAsset.Hold.Mount#isEntitySurface}). Formerly {@code Hold.Seat.Enabled}.
     */
    boolean seatMode;
    /**
     * True when this session is using the ENTITY mount hold (design section 9.2, phase 2 leg D
     * - the standing work mount: a spawned anchor entity) instead of the effect-mode movement
     * lock. Set once at engage from {@code Hold.Mount.Surface} being {@code "Entity"}; mutually
     * exclusive with {@link #seatMode} (the two are the same discriminator's two arms).
     */
    boolean entityMountMode;
    /** The entity-mount anchor's ref ({@link #entityMountMode} only); null otherwise. */
    @Nullable Ref<EntityStore> mountAnchorRef;
    /**
     * {@code Hold.Mount.Entity.DismountOnMove}, resolved at engage ({@link #entityMountMode}
     * only; default true). {@code true} = the heartbeat runs the SAME origin-delta walk-off
     * check effect-mode uses; {@code false} = hard-lock until crouch/re-press.
     */
    boolean entityDismountOnMove;
    boolean cameraApplied;
    boolean cameraLocked;
    boolean faceBlock;
    String cameraRecipe;
    String emoteId;
    /** The seated-worker swing fix's optional {@code Animation.ActionClip} override. */
    String actionClip;
    /**
     * True when this session runs an authored Steps program whose steps author any per-step
     * {@code Puppet.Clip} (round-8 step-synced swings) - SUPPRESSES the generic engage/swing puppet
     * clip so the per-step-entry clips ({@code StationStepRegistry}) are the sole puppet animation
     * driver and never double-fire on top of a generic swing. False for a non-stepped session, or a
     * stepped program with no step clips (that keeps its one generic engage swing). Resolved once at
     * engage ({@code StationStepDecisions#programAuthorsAnyStepClip}); the prop-sync path is
     * unaffected (see {@code StationPuppetController#playSwing}).
     */
    boolean stepProgramAuthorsClip;
    /** The station's held-tool gate, re-checked each heartbeat (null = no requirement). */
    StationAsset.Tool toolReq;

    /**
     * The resolved action's {@code Required} BLOCK sockets, snapshotted at engage (the resolved
     * config snapshot pattern - a mid-session catalog reload never half-changes a running loop):
     * the heartbeat re-verifies each one's world block beside its block-gone check, and a
     * vanished required block ends the session gracefully ({@code StopReason.SOCKET_LOST}).
     * Empty for every station without one (every degenerate-custody station included).
     */
    List<Custody.ResolvedSocket> requiredBlockSockets = List.of();

    /**
     * The multi-output category the player chose at the picker (selection wave, decision 50/56), or
     * {@code null} for a single-category station (every shipped station today - the sawmill authors
     * one {@code FromCrafting.Categories} entry, the anvil none) and for a plain-F engage. LIVE this
     * wave: the sneak+F picker's {@code onSelect} records a pending choice, the next plain-F engage
     * reads it into this field and clears the pending record only once that engage COMMITS (see
     * {@code StationService#peekPendingCategory}, so a denied press keeps the choice), and both the
     * engage viability check AND every runtime cycle narrow the derived conversions to it via the
     * pure {@code StationService#conversionsForCategory} filter ({@code null} = all, byte-identical
     * to the pre-selection engine). Session-scoped: {@code stop()} clears it, so it never bleeds
     * across sessions. Each derived {@code Conversion} now carries its native {@code Category}
     * (stamped by {@code StationRecipeDeriver}, decision 56) for the filter to match against.
     */
    @Nullable String chosenOutputCategory;

    /**
     * The item id for the enlarged summary-panel crest icon. Resolved ONCE at engage:
     * {@code Identity.Icon} when authored, else the anchor block's own item/BlockType id
     * captured AT START (the block can be gone by stop time).
     */
    String stationIconItemId;

    // Opt-in held-tool durability drain (Tool.Durability). Resolved reader defaults - 0 = off.
    int durabilityPerSwing;
    int durabilityPerCycle;

    // Per-swing cadence (Animation.Swing). 0 swingIntervalMs = no swing layer. What a swing SOUNDS
    // like is the "swing"/"impact" entries of `moments` below, not a field of its own.
    long swingIntervalMs;
    long nextSwingAtMs;

    /**
     * The running action's own {@code Moments} map, canonicalized to lowercase keys and snapshotted
     * ONCE at engage - the BASE presentation for every moment id the session emits where the engine
     * has nothing more specific to play. Null when the action authors no moments.
     */
    @Nullable Map<String, Presentation> moments;

    /**
     * The running action's RECIPE-level {@code Doneness} default, snapshotted at engage (like
     * every other resolved config value): what a custody-landing produce opens a ready window
     * with, and what the engaged heartbeat's throttled settle resolves against. Null when the
     * action's recipe authors none - the whole doneness path then costs this session nothing.
     */
    @Nullable StationAsset.Doneness doneness;
    /** Next game-time doneness settle check while engaged ({@code StationService#DONENESS_SETTLE_MS} throttle). */
    long nextDonenessSettleAtMs;

    // Opt-in idle practice mode (Work.Idle). The first three are the resolved config snapshot;
    // idleMode is a RUNTIME flag flipped by runCycle as materials come and go mid-session.
    boolean idleEnabled;
    long idleCycleMs;
    double idleFraction;
    boolean idleMode;

    // Cadence.
    long startedAtMs;
    long nextHeartbeatAtMs;
    long nextCycleAtMs;

    /** Cycles completed this session (real + idle). */
    int cyclesDone;

    // Step-program resume state (design section 9.3): survives ACROSS ticks, unlike
    // StationStepContext (rebuilt fresh every drain). programSuspended false + programIndex 0 is
    // the steady "no program in flight" state between cycle ticks - the phase-1 implicit program
    // (Consume/Produce/Roll/Present, no Wait step) always completes synchronously within ONE
    // runRealCycle call, so these never actually flip for the shipped sawmill; they exist so a
    // FUTURE authored program (a Wait step) can suspend a cycle across frames without new session
    // plumbing. stepDeadlineMs is the currently-suspending Wait step's OWN committed deadline
    // (written once by the handler, read - never re-derived - on every re-entry per the kernel's
    // binding resume contract); 0 = no deadline currently held.
    boolean programSuspended;
    int programIndex;
    long stepDeadlineMs;
    /**
     * The current {@code Repeat} iteration index within the step at {@link #programIndex} (scope-2
     * design 2.1, decision 34): survives across ticks so a REPEATING, {@code Duration}-holding step
     * resumes at the correct iteration without re-running its earlier iterations' mutations. 0 for a
     * single-iteration step (every shipped wave-2 program) and reset to 0 the instant a step
     * completes - the composite handler is its sole reader/writer. Dormant for the sawmill (its
     * one-step implicit program never repeats and never holds a Duration), exactly like
     * {@link #programIndex}/{@link #stepDeadlineMs} are dormant for it.
     */
    int stepIteration;
    /**
     * The {@code Repeat} count resolved ONCE at the step's fresh entry (scope-2 design 2.1's
     * "Repeat resolves once at step entry" invariant, m1): cached here the instant a {@code Duration}
     * hold suspends the step and read back verbatim on the resume, so a factor-scaled {@code Repeat}
     * combined with a {@code Duration} hold cannot re-resolve its iteration count mid-loop if the
     * underlying factor mutates during the hold. Paired 1:1 with {@link #stepDeadlineMs} (written
     * together at the hold; only read while a deadline is live), so a stale value can never be read.
     * The composite handler is its sole reader/writer; dormant for every shipped wave-2 program
     * (none combines a factor-scaled Repeat with a Duration hold).
     */
    int stepRepeatCount;

    // The IN-FLIGHT program's rebuild-avoiding snapshot, set only while programSuspended (design
    // 9.3): a resume must NOT re-derive which conversion is running (the live inventory may have
    // changed since the program started), so the fresh-start path snapshots its built steps and
    // attempt index here, and the resume path reads them back verbatim. Cleared
    // (nulled) the instant the program stops being suspended (Completed or Failed).
    @Nullable List<StationStep> activeProgramSteps;
    int activeProgramCycleIndex;

    // Puppet presentation (round-4 design, doc section 4 - "mount the player, hide their player
    // model, and spawn/display a visual of their character model performing the steps"): a
    // session-scoped spawned entity that performs the visual work instead of the real player, who
    // is optionally hidden. Resolved ONCE at engage from the resolved action's Puppet group
    // (StationPuppetController#spawnAndHide); Enabled==false or an absent group leaves every field
    // below at its default false/null - the classic in-body worker, byte-identical to a station
    // that never authors Puppet at all.
    boolean puppetActive;
    /**
     * The stateful performer backend driving the puppet double (seam wave decision 55): the bare-
     * {@code Holder} skinned puppet ({@code HolderPerformer}, the crowned PlayerClone/Model path) or
     * the Role-driven NPC ({@code NpcRolePerformer}). {@code StationPuppetController} owns ONE object
     * and never branches on the look source; each of its later mutations threads a fresh per-call
     * accessor into the performer. Null when {@link #puppetActive} is false.
     */
    @Nullable StationPerformer performer;
    /**
     * The spawned puppet entity, or null when {@link #puppetActive} is false. {@link #performer}
     * {@code .ref()} is the live source of truth; this field mirrors it and is RE-POINTED at it every
     * frame by {@code StationPuppetController#refreshPuppetRef} (F2 part a), covering the
     * {@code NpcRole} backend's one-tick deferred-spawn window (its {@code ref()} is null at engage,
     * non-null once the deferred spawn lands). Read by the direct-{@code puppetRef} consumers that
     * still need a raw ref (the {@code StationPuppetController#storeFor} store fallback, custody
     * retrieval); the {@code Walk} phase itself now drives the performer seam, not this field (F2
     * part b).
     */
    @Nullable Ref<EntityStore> puppetRef;
    /**
     * The resolved {@code Puppet.Hide.Route} ("Scale"/"Effect"/"None") applied at engage - drives
     * which revert {@code StationPuppetController#revealAndDespawn} runs in the {@code stop()}
     * funnel. Null when {@link #puppetActive} is false.
     */
    @Nullable String puppetHideRoute;
    /**
     * The {@code "Scale"} route's revert payload: the real player's prior
     * {@code EntityScaleComponent} scale BEFORE the hide was applied ({@code null} = no such
     * component existed - revert REMOVES the component rather than resetting to {@code 1.0}, per
     * {@code ziggfreed-common}'s {@code PlayerPuppetService#hideByScale}/{@code #revealByScale}
     * contract). Meaningless for any other {@link #puppetHideRoute}.
     */
    @Nullable Float puppetSavedScale;
    /**
     * The resolved action's default {@code Puppet.Prop} group, snapshotted at engage so the
     * per-swing prop sync ({@code StationPuppetController#playSwing}) reads it without
     * re-resolving the station catalog every beat. Null when {@link #puppetActive} is false.
     */
    @Nullable Puppet.Prop puppetDefaultProp;
    /**
     * The item id CURRENTLY mirrored onto the puppet's Hotbar (the dirty-gate's own last-known
     * value, per {@code ziggfreed-common}'s {@code PlayerPuppetService#updateHeldItem}/{@code
     * #heldItemChanged} contract - this primitive is stateless, so the session is where that
     * state lives). Set once at engage to the initial spawn-time mirror
     * ({@code StationPuppetController#spawnAndHide}), then kept in sync by the per-swing beat
     * ({@code #playSwing}/{@code #syncProp}) so a mid-work tool switch re-mirrors within one beat
     * without re-sending an unchanged Hotbar component every beat. Null when
     * {@link #puppetActive} is false, or the puppet is currently empty-handed.
     */
    @Nullable String puppetHeldItemId;
    /**
     * The puppet's world-space stance, resolved once at engage: {@code [x, y, z]} plus the three
     * angles in RADIANS ({@code yaw} with the placed block's facing already folded in,
     * {@code pitch}/{@code roll} the puppet's own untouched tilt). Kept because the tilt is applied
     * through the performer's re-anchor call rather than at spawn, which needs the same position
     * back. Null when {@link #puppetActive} is false.
     */
    @Nullable double[] puppetStance;
    /**
     * True while the authored {@code Puppet.Rotation} {@code Pitch}/{@code Roll} tilt still has to
     * be applied to the spawned performer. Set at engage ONLY when a non-zero tilt is authored, and
     * cleared by the first frame that finds a live performer ref - which is what covers the NpcRole
     * backend's one-tick deferred spawn, whose ref is honestly null at engage. A puppet with no
     * authored tilt never sets this, so the untilted path is byte-identical to having no tilt knob.
     */
    boolean puppetTiltPending;

    /**
     * The resolved anchor block keys (scope-2 wave 3, design 2.2/2.4 - decision 28c): {@code
     * anchorId -> "<worldUuid>:<x>:<y>:<z>"} for every declared {@code Anchors} entry the engage
     * DISCOVERED and CLAIMED (atomically, first-wins) plus the implicit reserved {@code "self"}
     * anchor (the primary station block). A step's {@code At}/{@code Walk.To} resolves its target
     * blockKey against this map; {@code stop()} releases every claimed anchor block (and returns its
     * custody) via it. Empty for a single-station program (every wave-2 program) - only an action
     * declaring {@code Anchors} and running a {@code Walk}/{@code At} step populates it.
     */
    final Map<String, String> anchorBlocks = new LinkedHashMap<>();

    /**
     * The in-flight walk state (scope-2 wave 3, design 2.3): non-null ONLY while a {@code Walk}
     * phase is driving the puppet toward an anchor. Carries the solved waypoints + parametric
     * progress so a walk survives suspend/resume across ticks (the composite handler re-enters it
     * every frame). Cleared the instant the walk arrives (or its path becomes blocked). Never
     * persisted, like every other session field.
     */
    @Nullable StationWalkState walkState;

    /**
     * The current program iteration's REFUND ledger (scope-2 wave 3, design 2.5, gate M1's single
     * rule): {@code itemId -> qty} of everything a {@code Consume} phase drained since the last
     * commit boundary. ANY {@code Produce.To:"Custody"} clears the ENTIRE map (the consumed inputs
     * BECAME the custody item {@code returnCustody} now hands back - refund and custody-return are
     * mutually exclusive per iteration). An orderly {@code stop()} refunds whatever remains here to
     * the player before the custody return runs; a hard crash loses it (accepted). The mandatory
     * cross-item transform-stop test asserts a mid-cook stop refunds NOTHING for the consumed Fish
     * while the custody return hands back the raw fish.
     */
    final Map<String, Integer> iterationConsumed = new LinkedHashMap<>();

    /**
     * The CUSTODY half of the iteration refund ledger, keyed by the pile the drain came from
     * ({@code "<blockKey>#<socketId>"}, the display side map's composite key shape): what a
     * {@code Consume From:"Custody"} phase drained since the last commit boundary, per originating
     * pile. An interrupted iteration refunds each entry back INTO its own pile (never to the
     * player, never merging piles - a shared session may legitimately have consumed from a pile it
     * does not own), while {@link #iterationConsumed} keeps the inventory-sourced half refunding
     * to the player. Cleared by the SAME commit boundaries.
     */
    final Map<String, Map<String, Integer>> iterationConsumedCustody = new LinkedHashMap<>();

    // Item ledger (for the future standalone summary HUD, leg 3): consumedItems covers both
    // the exact-ItemId route AND the ResourceTypeId ("any log" family) route (tallying the
    // REAL item ids the transactional removal actually drained). luckItems covers both the
    // tier-0 bonus copy and tier-ladder droplist grants; a luck grant is NOT also counted in
    // producedItems.
    final Map<String, Integer> consumedItems = new LinkedHashMap<>();
    final Map<String, Integer> producedItems = new LinkedHashMap<>();
    final Map<String, Integer> luckItems = new LinkedHashMap<>();

    /**
     * Per-produced-item YIELD BREAKDOWN, keyed by the same item ids {@link #producedItems} uses:
     * enough to say "1 base + 3 tool  x 12 cycles" on the summary panel's second row line instead of
     * only the bare total, which cannot tell a player whether their tool is doing anything.
     * Accumulated where the yield is resolved (one entry per output per real cycle) and read once by
     * {@code StationService#ledgerRows}. Session-scoped, never persisted.
     */
    final Map<String, YieldBreakdown> producedYield = new LinkedHashMap<>();

    /**
     * The accumulating decomposition behind ONE produced item id: how many cycles produced it, and
     * how much of the total came from the recipe's own deterministic {@code Yield} versus the
     * additive {@code Roll.Grants.OutputItems} a Bonus roll handed over.
     *
     * <p>{@link #changed} records whether the yield ever actually MOVED the number on any of those
     * cycles - a neutral {@code Scale} with no bonus reached leaves it false, which is what lets the
     * summary omit the breakdown line entirely for a starter tool. The line's presence is therefore
     * itself the signal that the tool is earning something.
     */
    static final class YieldBreakdown {
        int cycles;
        double baseSum;
        double bonusSum;
        boolean changed;

        /** One cycle's DETERMINISTIC produced quantity for this item id. */
        void add(double base, boolean yieldChangedIt) {
            cycles++;
            baseSum += base;
            changed |= yieldChangedIt;
        }

        /**
         * Extra items a {@code Roll.Grants.OutputItems} handed over on top of this cycle's
         * deterministic quantity - the RESOLVED whole-item count the player actually received, not
         * the fractional tally it came from, since the breakdown line reports what landed. Always
         * recorded AFTER {@link #add} for the same cycle (the roll phase runs after the produce
         * phase), so it lands on the cycle it belongs to.
         */
        void addBonus(double bonus) {
            if (bonus > 0.0) {
                bonusSum += bonus;
                changed = true;
            }
        }

        /** The mean base quantity per cycle; 0 when nothing was recorded. */
        double basePerCycle() {
            return cycles > 0 ? baseSum / cycles : 0.0;
        }

        /** The mean bonus quantity per cycle; 0 when nothing was recorded. */
        double bonusPerCycle() {
            return cycles > 0 ? bonusSum / cycles : 0.0;
        }
    }

    /**
     * One-shot {@code Roll.Grants.Contributions} posts that landed during the CURRENT cycle,
     * buffered here between the cycle's Roll phase and the cycle-completed event that forwards them
     * on {@code StationCycleCompletedEvent.oneShotContributions}. Drained (and cleared) by
     * {@code StationService#onCycleCompleted}, so a suspended program accumulates across its
     * iterations and delivers everything with the completing cycle. Session-scoped, never persisted.
     */
    final List<StationContribution> pendingOneShotContributions = new ArrayList<>();

    /**
     * The item id of the CURRENT cycle's primary output, set by the real-convert path just before
     * the program dispatches and read by a {@code Roll.Grants.OutputItems} grant (which adds extra
     * items of that same output). {@code null} for an authored Steps program, whose "primary
     * output" is undefined - an OutputItems grant there is dropped with a fine log. Session-scoped,
     * never persisted.
     */
    @Nullable String cycleOutputItemId;

    /**
     * Committed enhancement stamps this session (design section 9.5, phase 2 round-7 D-6): appended
     * by {@code StationStepHandlers.StampHandler} after each Stamp step writes its mutated item back
     * to custody, drained by {@code StationService#enhanceLedgerRows} into the end-of-session
     * summary. Session-scoped, never persisted, like every other ledger here.
     */
    final List<StationEnhanceOutcome> enhanceOutcomes = new ArrayList<>();

    /**
     * Session-scoped native {@code EntityEffect} bookkeeping (seam wave decision 51d): every
     * {@code Presentation.Effect} this session applies via {@code NativeEffectUtil} (through the
     * {@code emitMoment} choke point) is {@code track()}ed here, so the ONE {@code stop()} funnel
     * {@code removeAll()}s them on EVERY exit path (re-press, walk-off, damage, death, disconnect,
     * shutdown). Never persisted, world-thread only - matches every other session ledger here.
     */
    final AppliedEffectTracker appliedEffects = new AppliedEffectTracker();

    /** Idempotency gate: the first {@code compareAndSet(false, true)} wins the teardown. */
    final AtomicBoolean stopped = new AtomicBoolean(false);
}
