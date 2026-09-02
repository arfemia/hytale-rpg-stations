package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.common.i18n.Msg;
import com.ziggfreed.common.world.BlockOps;
import com.ziggfreed.common.world.pattern.BlockPattern;
import com.ziggfreed.common.world.pattern.BlockReader;
import com.ziggfreed.common.world.pattern.CellPredicate;
import com.ziggfreed.common.world.pattern.PatternIndex;
import com.ziggfreed.common.world.pattern.PatternMatch;
import com.ziggfreed.common.world.pattern.PatternVariant;
import com.ziggfreed.common.world.stash.BlockStash;
import com.ziggfreed.common.world.stash.BlockStashes;
import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.station.PatternCatalog.CompiledPattern;
import com.ziggfreed.rpgstations.station.PatternCells.CellMatcher;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The multiblock-structure RUNTIME: recognizes a player-built {@link StructurePatternAsset}
 * arrangement, activates its anchor into a working station, and reverts it when the standing shape
 * breaks. Fed by the same two event surfaces custody already listens on
 * ({@code StationBlockPlaceSystem} and {@code StationCustodyBreakSystem} + its environment
 * sibling); everything here runs on the owning world's thread.
 *
 * <p><b>Placement is detected DEFERRED.</b> {@code PlaceBlockEvent} fires BEFORE the engine writes
 * the placed block, so an inline walk would not see the placement and an inline anchor swap would
 * be clobbered by the engine's own write. {@link #onBlockPlaced} therefore only pre-filters (an
 * index probe plus a pending-radius check, both cheap) and hands the authoritative walk to
 * {@code world.execute}, where the block is real - which also re-verifies for free against a later
 * listener cancelling the placement.
 *
 * <p><b>Membership is never stored.</b> Which blocks belong to a standing build is re-derived by
 * walking the compiled pattern from the anchor; the only persisted mark is the anchor stash's tag
 * (pattern id + matched variant), which is what a break re-walk and a revert key off - so a
 * standing build survives restarts with zero extra state.
 *
 * <p><b>Build order is free.</b> A placement that matches an exact-id pattern cell but completes
 * nothing registers the implied anchor in the volatile {@link PendingAnchorIndex}; any later
 * placement within the widest pattern's bounding radius re-walks just those candidates. The index
 * never persists: after a restart, re-placing any exact-id block of the pattern completes a
 * half-built shape (the documented recovery).
 */
public final class StationStructures {

    private static final StationStructures INSTANCE = new StationStructures();

    private final PendingAnchorIndex pending = new PendingAnchorIndex();

    private StationStructures() {
    }

    @Nonnull
    public static StationStructures getInstance() {
        return INSTANCE;
    }

    // ==================== pure decision cores (unit-tested; the live paths below call ONLY these) ====================

    /** What a completed DETECT walk does at its anchor, decided from the anchor stash's tag + the gate. */
    enum ActivationDecision {
        /** No conflicting mark stands and the gate passed: activate. */
        ACTIVATE,
        /** The anchor already carries THIS pattern's mark: idempotent, nothing to do. */
        ALREADY_ACTIVE,
        /** The anchor carries a DIFFERENT pattern's mark, or another consumer's stash: refuse. */
        CONFLICT,
        /** The shape is complete but the pattern's {@code Requires} gate failed for the placer. */
        DENIED
    }

    /**
     * PURE: the activation outcome for an anchor whose stash carries {@code existingTag} (null =
     * no stash). A pattern-tagged stash naming THIS pattern is {@link ActivationDecision#ALREADY_ACTIVE};
     * naming another pattern, or a foreign consumer's tag, is {@link ActivationDecision#CONFLICT};
     * our own plain custody tag (a custom core block already holding materials) activates freely -
     * the pattern segment is stamped beside the custody half, never over it.
     */
    @Nonnull
    static ActivationDecision decideActivation(@Nullable String existingTag, @Nonnull String patternId,
            boolean requiresPassed) {
        String taggedPattern = StationCustodyClaim.patternIdOfTag(existingTag);
        if (taggedPattern != null) {
            return taggedPattern.equalsIgnoreCase(patternId)
                    ? ActivationDecision.ALREADY_ACTIVE : ActivationDecision.CONFLICT;
        }
        if (existingTag != null && !existingTag.isBlank() && !StationCustodyClaim.isOurTag(existingTag)) {
            return ActivationDecision.CONFLICT;
        }
        return requiresPassed ? ActivationDecision.ACTIVATE : ActivationDecision.DENIED;
    }

    /**
     * One block swap, decided purely: which block to write and with which rotation index -
     * {@code skip} when there is nothing to write ({@code targetBlockId} absent) or the target
     * already stands there ({@code currentBaseId} equal ignoring case, so a state variant of the
     * target never swaps either). The read rotation is CARRIED verbatim; {@code null} (unreadable)
     * keeps {@code rotationIndex} null and the live write uses the engine's no-rotation form.
     */
    record SwapDecision(@Nullable String blockItemId, @Nullable Integer rotationIndex, boolean skip) {
    }

    /** PURE: see {@link SwapDecision}. */
    @Nonnull
    static SwapDecision swapFor(@Nullable String currentBaseId, @Nullable String targetBlockId,
            @Nullable Integer currentRotationIndex) {
        if (targetBlockId == null || targetBlockId.isBlank()) {
            return new SwapDecision(null, null, true);
        }
        if (currentBaseId != null && targetBlockId.equalsIgnoreCase(currentBaseId)) {
            return new SwapDecision(null, null, true);
        }
        return new SwapDecision(targetBlockId, currentRotationIndex, false);
    }

    /** One break-side re-walk candidate: this cell of this HOLD variant of this pattern could have been the broken block. */
    record HoldCandidate(@Nonnull CompiledPattern compiled, int variantIndex, int cellIndex) {

        @Nonnull
        PatternVariant<CellMatcher> variant() {
            return compiled.hold().variants().get(variantIndex);
        }
    }

    /**
     * PURE: every (pattern, HOLD variant, cell) whose matcher accepts the broken block - the
     * break-side probe. Wider than the placement index on purpose: a family- or tag-matched cell
     * (any rock in the ring) is not exact-id-indexable, but its break must still find the anchor.
     * {@code Empty} cells are skipped (a break can only SATISFY one), and the HOLD anchor cell is
     * skipped too (breaking the activated anchor block is the anchor-break path, keyed off that
     * block's own stash, never a re-walk). Bounded by patterns x variants x cells - a handful.
     */
    @Nonnull
    static List<HoldCandidate> holdCandidatesFor(@Nonnull List<CompiledPattern> compiled,
            @Nonnull String brokenBlockId, @Nonnull UnaryOperator<String> baseIdOf,
            @Nonnull Function<String, List<String>> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        List<HoldCandidate> out = new ArrayList<>();
        for (CompiledPattern cp : compiled) {
            BlockPattern<CellMatcher> hold = cp.hold();
            for (int c = 0; c < hold.cellCount(); c++) {
                if (c == hold.anchorIndex()) {
                    continue;
                }
                CellMatcher matcher = hold.payload(c);
                if (matcher.empty()
                        || !PatternCells.matches(matcher, brokenBlockId, baseIdOf, resourceTypesOf, tagsOf)) {
                    continue;
                }
                for (int v = 0; v < hold.variants().size(); v++) {
                    out.add(new HoldCandidate(cp, v, c));
                }
            }
        }
        return out;
    }

    /**
     * PURE: does the standing build at {@code (ax, ay, az)} still satisfy its HOLD form? The
     * TAGGED variant is walked when its index is still valid; a stale index (the pattern's
     * {@code Rotate} flags changed since the stash was stamped) degrades to "any variant still
     * standing counts", so an asset re-tune never demolishes a legitimate build.
     */
    static boolean holdStands(@Nonnull BlockPattern<CellMatcher> hold, @Nullable Integer taggedVariant,
            int ax, int ay, int az, @Nonnull BlockReader reader,
            @Nonnull CellPredicate<CellMatcher> predicate) {
        List<PatternVariant<CellMatcher>> variants = hold.variants();
        if (taggedVariant != null && taggedVariant >= 0 && taggedVariant < variants.size()) {
            return variants.get(taggedVariant).matchAt(ax, ay, az, reader, predicate);
        }
        for (PatternVariant<CellMatcher> variant : variants) {
            if (variant.matchAt(ax, ay, az, reader, predicate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PURE: {@code reader} with one position overridden to air - the break-side walk's view of the
     * world, where the block the current event is breaking already reads as gone whether or not
     * the engine has removed it yet (a player break event fires BEFORE the removal).
     */
    @Nonnull
    static BlockReader withBrokenAt(@Nonnull BlockReader reader, int bx, int by, int bz) {
        return (x, y, z) -> x == bx && y == by && z == bz
                ? PatternCells.EMPTY_KEY : reader.blockItemIdAt(x, y, z);
    }

    // ==================== placement (detection + activation) ====================

    /**
     * The {@code PlaceBlockEvent} feed ({@code StationBlockPlaceSystem}): pre-filters on the event
     * thread, then defers the authoritative walk to {@code world.execute} (see the class javadoc
     * for why inline detection cannot work). Creative placement activates like any other -
     * building is not an economy action - while {@code Requires} still evaluates.
     */
    public void onBlockPlaced(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> placerRef,
            @Nullable PlayerRef playerRef, @Nonnull UUID worldUuid, int x, int y, int z,
            @Nonnull String placedItemId) {
        PatternCatalog catalog = PatternCatalog.getInstance();
        if (catalog.isEmpty()) {
            return;
        }
        try {
            boolean indexed = !catalog.index()
                    .candidatesFor(BlockOps.baseItemIdOf(placedItemId).toLowerCase(Locale.ROOT)).isEmpty();
            boolean nearPending = !pending
                    .candidatesNear(worldUuid, x, y, z, catalog.maxBoundingRadius()).isEmpty();
            if (!indexed && !nearPending) {
                return;
            }
            World world = WorldEvictors.worldOf(store);
            if (world == null || !world.isAlive()) {
                return;
            }
            world.execute(() -> scanAfterPlacement(world, worldUuid, playerRef, placerRef, x, y, z, placedItemId));
        } catch (Throwable t) {
            Log.fine("STRUCTURE placement pre-filter failed at (" + x + ", " + y + ", " + z + "): "
                    + t.getMessage());
        }
    }

    /**
     * The deferred, authoritative placement scan: the pending candidates near the placement
     * re-walk first (they were registered earlier, so a completing ring block finds them), then the
     * placed block's own index candidates; the FIRST completed walk in deterministic order
     * (pattern id, then variant index, then cell index) is THE outcome and ends the scan, whether
     * it activates, refuses a conflict, or denies the gate. With no completed walk, every fresh
     * candidate's implied anchor registers as pending.
     */
    private void scanAfterPlacement(@Nonnull World world, @Nonnull UUID worldUuid,
            @Nullable PlayerRef playerRef, @Nullable Ref<EntityStore> placerRef,
            int x, int y, int z, @Nonnull String placedItemId) {
        try {
            PatternCatalog catalog = PatternCatalog.getInstance();
            if (catalog.isEmpty() || !world.isAlive()) {
                return;
            }
            ChunkStore chunkStore = world.getChunkStore();
            BlockReader reader = BlockReader.over(chunkStore);
            CellPredicate<CellMatcher> predicate = PatternCells.livePredicate();

            for (PendingAnchorIndex.Pending candidate
                    : pending.candidatesNear(worldUuid, x, y, z, catalog.maxBoundingRadius())) {
                CompiledPattern cp = catalog.byId(candidate.patternId());
                if (cp == null) {
                    pending.remove(worldUuid, candidate);
                    continue;
                }
                PatternMatch<CellMatcher> match = detectAnyVariantAt(cp.detect(),
                        candidate.x(), candidate.y(), candidate.z(), reader, predicate);
                if (match != null) {
                    handleCompletedShape(world, worldUuid, chunkStore, playerRef, placerRef, cp, match);
                    return;
                }
            }

            List<PatternIndex.Candidate<CellMatcher>> candidates = catalog.index()
                    .candidatesFor(BlockOps.baseItemIdOf(placedItemId).toLowerCase(Locale.ROOT));
            for (PatternIndex.Candidate<CellMatcher> candidate : candidates) {
                PatternMatch<CellMatcher> match = candidate.variant()
                        .matchFromCell(candidate.cellIndex(), x, y, z, reader, predicate);
                if (match != null) {
                    CompiledPattern cp = catalog.compiledForDetect(candidate.pattern());
                    if (cp != null) {
                        handleCompletedShape(world, worldUuid, chunkStore, playerRef, placerRef, cp, match);
                        return;
                    }
                }
            }
            for (PatternIndex.Candidate<CellMatcher> candidate : candidates) {
                CompiledPattern cp = catalog.compiledForDetect(candidate.pattern());
                if (cp == null) {
                    continue;
                }
                Vector3i anchor = candidate.variant().anchorFromCell(candidate.cellIndex(), x, y, z);
                pending.register(worldUuid, anchor.x, anchor.y, anchor.z, cp.id());
            }
        } catch (Throwable t) {
            Log.warn("STRUCTURE placement scan failed at (" + x + ", " + y + ", " + z + "): " + t.getMessage());
        }
    }

    /** Walk every variant of {@code detect} rooted at a candidate anchor; the first match wins. */
    @Nullable
    private static PatternMatch<CellMatcher> detectAnyVariantAt(@Nonnull BlockPattern<CellMatcher> detect,
            int ax, int ay, int az, @Nonnull BlockReader reader,
            @Nonnull CellPredicate<CellMatcher> predicate) {
        for (PatternVariant<CellMatcher> variant : detect.variants()) {
            PatternMatch<CellMatcher> match = variant.matchFromCell(detect.anchorIndex(), ax, ay, az,
                    reader, predicate);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * One completed DETECT walk: decide against the anchor's stash tag, evaluate the gate, and on
     * {@link ActivationDecision#ACTIVATE} swap the anchor (carrying its placed rotation), stamp the
     * stash's pattern segment, feed the station discovery index, clear the pending entries, and
     * play the {@code activated} moment.
     */
    private void handleCompletedShape(@Nonnull World world, @Nonnull UUID worldUuid,
            @Nonnull ChunkStore chunkStore, @Nullable PlayerRef playerRef,
            @Nullable Ref<EntityStore> placerRef, @Nonnull CompiledPattern cp,
            @Nonnull PatternMatch<CellMatcher> match) {
        int ax = match.anchorX();
        int ay = match.anchorY();
        int az = match.anchorZ();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(ax, ay, az);
        if (sectionRef == null || !sectionRef.isValid()) {
            return;
        }
        Store<ChunkStore> accessor = chunkStore.getStore();
        BlockStash stash = BlockStashes.stashAt(accessor, sectionRef, ax, ay, az);
        String existingTag = stash != null ? stash.getTag() : null;

        ActivationDecision tagDecision = decideActivation(existingTag, cp.id(), true);
        if (tagDecision == ActivationDecision.ALREADY_ACTIVE) {
            pending.removeAt(worldUuid, ax, ay, az);
            return;
        }
        if (tagDecision == ActivationDecision.CONFLICT) {
            toast(playerRef, RpgMsg.tr("ui.station.structure_conflict"));
            return;
        }
        if (!requiresPassed(cp, playerRef)) {
            toast(playerRef, requirementsUnmetToast(cp));
            return;
        }

        String activateBlock = cp.asset().getActivate() != null ? cp.asset().getActivate().getBlock() : null;
        String currentId = BlockOps.blockItemIdAt(chunkStore, ax, ay, az);
        SwapDecision swap = swapFor(currentId != null ? BlockOps.baseItemIdOf(currentId) : null,
                activateBlock, BlockOps.rotationIndexAt(chunkStore, ax, ay, az));
        if (!swap.skip()) {
            boolean written = swap.rotationIndex() != null
                    ? BlockOps.setBlock(chunkStore, ax, ay, az, swap.blockItemId(), swap.rotationIndex())
                    : BlockOps.setBlock(chunkStore, ax, ay, az, swap.blockItemId());
            if (!written) {
                Log.warn("STRUCTURE '" + cp.id() + "' completed at (" + ax + ", " + ay + ", " + az
                        + ") but the anchor swap to '" + swap.blockItemId() + "' failed - not activating");
                return;
            }
        }

        BlockStash ensured = BlockStashes.ensureStashAt(accessor, sectionRef, ax, ay, az);
        if (ensured == null) {
            Log.warn("STRUCTURE '" + cp.id() + "' activated at (" + ax + ", " + ay + ", " + az
                    + ") but its anchor stash could not be written - a break will not revert it");
        } else {
            ensured.setTag(StationCustodyClaim.withPatternSegment(ensured.getTag(), cp.id(),
                    match.variantIndex()));
            UUID placerId = playerRef != null ? playerRef.getUuid() : null;
            if (ensured.getOwner() == null && placerId != null) {
                ensured.setOwner(placerId.toString());
            }
            BlockStashes.markDirty(accessor, sectionRef);
        }

        if (cp.stationId() != null) {
            StationService.getInstance().registerKnownStationBlock(
                    StationAnchors.blockKey(worldUuid.toString(), ax, ay, az), cp.stationId(), activateBlock);
        }
        pending.removeAt(worldUuid, ax, ay, az);
        playPatternMoment(world, playerRef, placerRef,
                cp.asset().moment(StructurePatternAsset.MOMENT_ACTIVATED), ax, ay, az);
        Log.fine("STRUCTURE '" + cp.id() + "' activated at (" + ax + ", " + ay + ", " + az
                + "), variant " + match.variantIndex());
    }

    /**
     * The pattern's {@code Requires} gate against the placer, mirroring the station engage gate's
     * semantics: an absent group passes, an unresolvable player fails CLOSED, an unregistered
     * factor fails CLOSED (with a warn naming it).
     */
    private static boolean requiresPassed(@Nonnull CompiledPattern cp, @Nullable PlayerRef playerRef) {
        Requires reqs = cp.asset().getRequires();
        if (reqs == null || reqs.isEmpty()) {
            return true;
        }
        if (playerRef == null) {
            return false;
        }
        String permission = reqs.getPermission();
        if (permission != null && !permission.isBlank() && !playerRef.hasPermission(permission)) {
            return false;
        }
        var conditions = reqs.getConditions();
        if (conditions == null || conditions.length == 0) {
            return true;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return false;
        }
        FactorContext ctx = FactorContext.builder()
                .playerRef(playerRef)
                .playerId(playerId)
                .stationId(cp.stationId() != null ? cp.stationId() : cp.id())
                .build();
        String failed = FactorRegistryImpl.getInstance().firstFailedCondition(conditions, ctx);
        if (failed != null) {
            if (!FactorRegistryImpl.getInstance().isKnown(failed)) {
                Log.warn("STRUCTURE Requires condition references unknown factor '" + failed
                        + "' - denying (fail closed)");
            }
            return false;
        }
        return true;
    }

    /** The gate-denial toast, naming the structure when its {@code Identity.NameKey} is authored. */
    @Nonnull
    private static Message requirementsUnmetToast(@Nonnull CompiledPattern cp) {
        String nameKey = cp.asset().getIdentity() != null ? cp.asset().getIdentity().getNameKey() : null;
        if (nameKey != null && !nameKey.isBlank()) {
            return RpgMsg.tr("ui.station.pattern_requirements_unmet_named", Msg.key(nameKey));
        }
        return RpgMsg.tr("ui.station.pattern_requirements_unmet");
    }

    // ==================== break (invalidation + revert) ====================

    /**
     * The break feed ({@code StationCustodyBreakSystem} + its environment sibling), run BEFORE the
     * custody break funnel so an activated anchor's own stash tag is still readable. Two halves:
     * the broken block itself may be an ACTIVATED ANCHOR (clean the pattern bookkeeping up - no
     * swap-back onto air, the anchor is gone; a pattern-only stash is removed here since it
     * resolves no custody claim for the L4 funnel to drop), and it may be a MEMBER of a standing
     * shape (re-walk each candidate anchor's HOLD form with the broken position reading as air; a
     * failed walk reverts that anchor).
     */
    public void onBlockBroken(@Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nonnull UUID worldUuid,
            int x, int y, int z, @Nullable String brokenBlockTypeId,
            @Nullable PlayerRef breakerPlayerRef, @Nullable Ref<EntityStore> breakerRef) {
        PatternCatalog catalog = PatternCatalog.getInstance();
        if (catalog.isEmpty()) {
            return;
        }
        World world;
        try {
            world = WorldEvictors.worldOf(store);
        } catch (Throwable t) {
            Log.fine("STRUCTURE break handler could not resolve a world: " + t.getMessage());
            return;
        }
        if (world == null) {
            return;
        }
        try {
            ChunkStore chunkStore = world.getChunkStore();

            // The broken block itself: a pending candidate there is moot, and an activated
            // anchor's pattern-only stash is ours to remove (a custody-carrying stash stays for
            // the custody break funnel, which runs after this and drops its contents).
            pending.removeAt(worldUuid, x, y, z);
            String anchorTag = stashTagAt(chunkStore, x, y, z);
            if (StationCustodyClaim.patternIdOfTag(anchorTag) != null
                    && StationCustodyClaim.stationIdOfTag(anchorTag) == null) {
                removeStashDirect(chunkStore, x, y, z);
            }

            if (brokenBlockTypeId == null || brokenBlockTypeId.isBlank()) {
                return;
            }
            BlockReader reader = withBrokenAt(BlockReader.over(chunkStore), x, y, z);
            CellPredicate<CellMatcher> predicate = PatternCells.livePredicate();
            String baseBroken = BlockOps.baseItemIdOf(brokenBlockTypeId);
            for (HoldCandidate candidate : holdCandidatesFor(catalog.compiled(), baseBroken,
                    BlockOps::baseItemIdOf, BlockOps::resourceTypeIdsOf, BlockOps::rawTagsOf)) {
                Vector3i anchor = candidate.variant().anchorFromCell(candidate.cellIndex(), x, y, z);
                if (anchor.x == x && anchor.y == y && anchor.z == z) {
                    continue;
                }
                String tag = stashTagAt(chunkStore, anchor.x, anchor.y, anchor.z);
                String taggedPattern = StationCustodyClaim.patternIdOfTag(tag);
                if (taggedPattern == null || !taggedPattern.equalsIgnoreCase(candidate.compiled().id())) {
                    continue;
                }
                if (holdStands(candidate.compiled().hold(), StationCustodyClaim.patternVariantOfTag(tag),
                        anchor.x, anchor.y, anchor.z, reader, predicate)) {
                    continue;
                }
                revertAt(store, commandBuffer, world, worldUuid, chunkStore, candidate.compiled(),
                        anchor.x, anchor.y, anchor.z, breakerPlayerRef, breakerRef);
            }
        } catch (Throwable t) {
            Log.warn("STRUCTURE break scan failed at (" + x + ", " + y + ", " + z + "): " + t.getMessage());
        }
    }

    /**
     * The shape at {@code (ax, ay, az)} is gone: stop every session working the anchor
     * ({@code StopReason.STRUCTURE_LOST}, the present-player hand-back family), drop whatever the
     * stash still holds at the block once and remove it (the custody break funnel - which also
     * despawns the display props and de-indexes the block), play the {@code broken} moment, and
     * swap the anchor back to its revert block carrying its current rotation.
     */
    private void revertAt(@Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nonnull World world,
            @Nonnull UUID worldUuid, @Nonnull ChunkStore chunkStore, @Nonnull CompiledPattern cp,
            int ax, int ay, int az, @Nullable PlayerRef breakerPlayerRef,
            @Nullable Ref<EntityStore> breakerRef) {
        String anchorKey = StationAnchors.blockKey(worldUuid.toString(), ax, ay, az);
        StationService.getInstance().stopSessionsForStructureLost(anchorKey, store, commandBuffer);
        if (commandBuffer != null) {
            StationService.getInstance().onCustodyBlockBroken(store, commandBuffer, anchorKey, ax, ay, az);
        }
        // Whatever the funnel left behind loses its structure mark: a PATTERN-ONLY stash (which
        // resolves no custody claim for the funnel to drop) is removed outright, while a stash
        // still carrying a custody half keeps its materials record and only sheds the pattern
        // segment - never delete a record that might still be holding someone's items.
        stripStructureMark(chunkStore, ax, ay, az);
        pending.removeAt(worldUuid, ax, ay, az);
        playPatternMoment(world, breakerPlayerRef, breakerRef,
                cp.asset().moment(StructurePatternAsset.MOMENT_BROKEN), ax, ay, az);
        String revertBlock = cp.asset().effectiveRevertBlock();
        String currentId = BlockOps.blockItemIdAt(chunkStore, ax, ay, az);
        SwapDecision swap = swapFor(currentId != null ? BlockOps.baseItemIdOf(currentId) : null,
                revertBlock, BlockOps.rotationIndexAt(chunkStore, ax, ay, az));
        if (!swap.skip()) {
            if (swap.rotationIndex() != null) {
                BlockOps.setBlock(chunkStore, ax, ay, az, swap.blockItemId(), swap.rotationIndex());
            } else {
                BlockOps.setBlock(chunkStore, ax, ay, az, swap.blockItemId());
            }
        }
        Log.fine("STRUCTURE '" + cp.id() + "' broken - anchor at (" + ax + ", " + ay + ", " + az
                + ") reverted" + (swap.skip() ? " (no swap needed)" : " to '" + swap.blockItemId() + "'"));
    }

    // ==================== lifecycle ====================

    /** Drops a removed world's volatile pending candidates (decision: pending never persists). */
    public void onWorldRemoved(@Nonnull World world) {
        try {
            UUID worldUuid = world.getWorldConfig().getUuid();
            if (worldUuid != null) {
                pending.clearWorld(worldUuid);
            }
        } catch (Throwable t) {
            Log.fine("STRUCTURE world-removal sweep failed: " + t.getMessage());
        }
    }

    /** Test/diagnostic read: the live pending-candidate count for one world. */
    int pendingCount(@Nonnull UUID worldUuid) {
        return pending.size(worldUuid);
    }

    // ==================== helpers ====================

    /** The stash tag at a block, or null (no stash, unloaded section, or a failed read). */
    @Nullable
    private static String stashTagAt(@Nonnull ChunkStore chunkStore, int x, int y, int z) {
        try {
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return null;
            }
            BlockStash stash = BlockStashes.stashAt(chunkStore.getStore(), sectionRef, x, y, z);
            return stash != null ? stash.getTag() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Removes the stash at a block outright (the section marks itself dirty); fail-soft. */
    private static void removeStashDirect(@Nonnull ChunkStore chunkStore, int x, int y, int z) {
        try {
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            if (sectionRef != null && sectionRef.isValid()) {
                BlockStashes.removeStashAt(chunkStore.getStore(), sectionRef, x, y, z);
            }
        } catch (Throwable t) {
            Log.fine("STRUCTURE stash removal failed at (" + x + ", " + y + ", " + z + "): " + t.getMessage());
        }
    }

    /**
     * Takes the structure mark off whatever stash still stands at a reverted anchor: a
     * PATTERN-ONLY stash is removed outright (it records nothing but the mark), while a stash
     * still carrying a custody half only sheds its pattern segment - its materials record is not
     * this path's to delete. A markless or absent stash is a no-op.
     */
    private static void stripStructureMark(@Nonnull ChunkStore chunkStore, int x, int y, int z) {
        try {
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return;
            }
            Store<ChunkStore> accessor = chunkStore.getStore();
            BlockStash stash = BlockStashes.stashAt(accessor, sectionRef, x, y, z);
            String tag = stash != null ? stash.getTag() : null;
            if (StationCustodyClaim.patternIdOfTag(tag) == null) {
                return;
            }
            if (StationCustodyClaim.stationIdOfTag(tag) == null) {
                BlockStashes.removeStashAt(accessor, sectionRef, x, y, z);
            } else {
                stash.setTag(StationCustodyClaim.withoutPatternSegment(tag));
                BlockStashes.markDirty(accessor, sectionRef);
            }
        } catch (Throwable t) {
            Log.fine("STRUCTURE mark strip failed at (" + x + ", " + y + ", " + z + "): " + t.getMessage());
        }
    }

    /**
     * Plays one pattern moment's presentation at the anchor: sounds and particles positionally
     * (with the anchor block's own facing driving any {@code PositionOffset}), the shake on the
     * triggering player when one exists, and the two native-composition payloads on their entity
     * ref. Cues play at once - a structure moment has no session to queue a {@code DelayMs}
     * against, so an authored delay reads as zero (the same degrade a nonsense delay gets
     * everywhere). An applied {@code Effect} is untracked and lives out its own duration/TTL.
     * One immediate-playback core serves every sessionless moment in this engine
     * ({@link StationService#playPresentationAt}); this is its structure-moment call site.
     */
    private static void playPatternMoment(@Nonnull World world, @Nullable PlayerRef playerRef,
            @Nullable Ref<EntityStore> ref, @Nullable Presentation p, int x, int y, int z) {
        StationService.playPresentationAt(world, playerRef, ref, p, x, y, z);
    }

    private static void toast(@Nullable PlayerRef playerRef, @Nonnull Message message) {
        if (playerRef != null) {
            StationService.toast(playerRef, message);
        }
    }
}
