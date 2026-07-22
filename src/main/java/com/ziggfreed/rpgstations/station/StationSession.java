package com.ziggfreed.rpgstations.station;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;

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
     * True when this session is using the native SEAT mount hold (BlockMountAPI) instead of
     * the effect-mode movement lock. Set once at engage from {@code Hold.Seat.Enabled}.
     */
    boolean seatMode;
    boolean cameraApplied;
    boolean cameraLocked;
    boolean faceBlock;
    String faceBlockMode;
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

    // Item ledger (for the future standalone summary HUD, leg 3): consumedItems covers both
    // the exact-ItemId route AND the ResourceTypeId ("any log" family) route (tallying the
    // REAL item ids the transactional removal actually drained). luckItems covers both the
    // tier-0 bonus copy and tier-ladder droplist grants; a luck grant is NOT also counted in
    // producedItems.
    final Map<String, Integer> consumedItems = new LinkedHashMap<>();
    final Map<String, Integer> producedItems = new LinkedHashMap<>();
    final Map<String, Integer> luckItems = new LinkedHashMap<>();

    /** Idempotency gate: the first {@code compareAndSet(false, true)} wins the teardown. */
    final AtomicBoolean stopped = new AtomicBoolean(false);
}
