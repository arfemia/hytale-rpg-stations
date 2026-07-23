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
 * <p>Ported from the MMO's {@code station.StationSession} (RPG Stations extraction leg 2).
 * Per the design's severance list, this port DROPS the MMO's per-cycle XP-factor-breakdown
 * bookkeeping ({@code xpBaseBySkill}/{@code xpAwardedBySkill}/{@code xpFactorSumBySkill}/
 * {@code accumulateXpFactors}/{@code avgXpFactor}/{@code toolMultSum}/{@code avgToolMult}) -
 * that accounting is MMO-bridge-side state now, built from the fired
 * {@code StationCycleCompletedEvent}s (an api-artifact concern, leg 4/5). This session keeps
 * only what the ENGINE itself needs: identity/anchors, the resolved config snapshot, cadence,
 * cycle count, item tallies (for the future standalone summary HUD, leg 3), and swing/impact
 * scheduling. {@link #sessionId} is a new field the loot engine (leg 3) and the api events
 * (leg 4) will key bookkeeping/dispatch off of.
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
     * The action id this session resolved at engage (design section 9.1, phase 2 leg E):
     * {@link ActionResolver#ACTION_WORK} for a single-action station (unchanged behavior); a
     * multi-action station's diegetic input-matched selection otherwise. Fixed for the WHOLE
     * session (re-selected only on the next fresh engage, never mid-session) - every
     * {@code ActionResolver.resolve(asset, ...)} call this session's own code paths make MUST use
     * this field, never the bare {@code ACTION_WORK} constant, once a session exists.
     */
    String actionId;
    /** Occupancy key: {@code "<worldUuid>:<x>:<y>:<z>"} (enforces {@code Work.Exclusive}). */
    String blockKey;
    int blockX;
    int blockY;
    int blockZ;
    /** The engine block id at the anchor position when the session started (block-gone check). */
    int startBlockId;

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
     * {@code Hold.Mount.Entity.Steerable}, resolved at engage ({@link #entityMountMode} only;
     * default false). {@code true} skips the hold-effect lock + heartbeat snap-back (reserved
     * for a future vehicle-like station).
     */
    boolean entitySteerable;
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
    /** The station's held-tool gate, re-checked each heartbeat (null = no requirement). */
    StationAsset.Tool toolReq;

    /**
     * The item id for the enlarged summary-panel crest icon. Resolved ONCE at engage:
     * {@code Identity.Icon} when authored, else the anchor block's own item/BlockType id
     * captured AT START (the block can be gone by stop time).
     */
    String stationIconItemId;

    // Opt-in held-tool durability drain (Tool.Durability). Resolved reader defaults - 0 = off.
    int durabilityPerSwing;
    int durabilityPerCycle;

    // Per-swing cadence + flair (Animation.Swing). 0 swingIntervalMs = no swing layer.
    long swingIntervalMs;
    Presentation swingPresentation;
    long nextSwingAtMs;

    // Delayed swing-impact cue (Animation.Swing.Impact). impactDelayMs/impactPresentation are
    // the resolved config snapshot; pendingImpactAtMs is the RUNTIME due-at-millis for the one
    // pending impact this session may owe (0 = none pending).
    long impactDelayMs;
    Presentation impactPresentation;
    long pendingImpactAtMs;

    // Opt-in idle practice mode (Work.Idle). The first three are the resolved config snapshot;
    // idleMode is a RUNTIME flag flipped by runCycle as materials come and go mid-session.
    boolean idleEnabled;
    long idleCycleMs;
    double idleXpFraction;
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

    // The IN-FLIGHT program's rebuild-avoiding snapshot, set only while programSuspended (design
    // 9.3): a resume must NOT re-derive which conversion is running (the live inventory may have
    // changed since the program started), so the fresh-start path snapshots its built steps /
    // cycle output / attempt index here, and the resume path reads them back verbatim. Cleared
    // (nulled) the instant the program stops being suspended (Completed or Failed).
    @Nullable List<StationStep> activeProgramSteps;
    @Nullable ItemStack activeProgramCycleOutput;
    int activeProgramCycleIndex;

    // Puppet presentation (round-4 design, doc section 4 - "mount the player, hide their player
    // model, and spawn/display a visual of their character model performing the steps"): a
    // session-scoped spawned entity that performs the visual work instead of the real player, who
    // is optionally hidden. Resolved ONCE at engage from the resolved action's Puppet group
    // (StationPuppetController#spawnAndHide); Enabled==false or an absent group leaves every field
    // below at its default false/null - the classic in-body worker, byte-identical to a station
    // that never authors Puppet at all.
    boolean puppetActive;
    /** The spawned puppet entity, or null when {@link #puppetActive} is false. */
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

    // Item ledger (for the future standalone summary HUD, leg 3): consumedItems covers both
    // the exact-ItemId route AND the ResourceTypeId ("any log" family) route (tallying the
    // REAL item ids the transactional removal actually drained). luckItems covers both the
    // tier-0 bonus copy and tier-ladder droplist grants; a luck grant is NOT also counted in
    // producedItems.
    final Map<String, Integer> consumedItems = new LinkedHashMap<>();
    final Map<String, Integer> producedItems = new LinkedHashMap<>();
    final Map<String, Integer> luckItems = new LinkedHashMap<>();

    /**
     * Committed enhancement stamps this session (design section 9.5, phase 2 round-7 D-6): appended
     * by {@code StationStepHandlers.StampHandler} after each Stamp step writes its mutated item back
     * to custody, drained by {@code StationService#enhanceLedgerRows} into the end-of-session
     * summary. Session-scoped, never persisted, like every other ledger here.
     */
    final List<StationEnhanceOutcome> enhanceOutcomes = new ArrayList<>();

    /** Idempotency gate: the first {@code compareAndSet(false, true)} wins the teardown. */
    final AtomicBoolean stopped = new AtomicBoolean(false);
}
