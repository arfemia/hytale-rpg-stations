package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.codec.Vec3i;
import com.ziggfreed.common.world.pattern.BlockPattern;
import com.ziggfreed.common.world.pattern.PatternCell;
import com.ziggfreed.common.world.pattern.PatternIndex;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;
import com.ziggfreed.rpgstations.station.PatternCells.CellMatcher;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The RUNTIME AUTHORITY for structure patterns: raw {@link StructurePatternAsset}s folded from
 * {@code Server/RpgStations/Patterns/*.json}, COMPILED into the two
 * {@link BlockPattern} forms every runtime read walks:
 *
 * <ul>
 *   <li><b>DETECT</b> - the authored cells verbatim, walked when a placement might have completed
 *       the shape (the anchor cell tests as its own authored block, which is what stands there
 *       BEFORE activation).</li>
 *   <li><b>HOLD</b> - the standing-build re-check, walked when a break might have destroyed the
 *       shape: the anchor cell's matcher is replaced by the ACTIVATED station block
 *       ({@code Activate.Block}, state variants folding onto it), and any cell whose offset
 *       coincides with a {@code Block}-route socket {@code At} of the activated station's actions
 *       is EXCLUDED - a pot placed onto its socket, or the swap itself, must never read as the
 *       shape breaking.</li>
 * </ul>
 *
 * <p>Every compile is republished as one immutable snapshot (compiled list sorted by pattern id,
 * the placement {@link PatternIndex} seeded from every DETECT cell authoring an exact
 * {@code ItemId}), so world-thread readers see a consistent whole and iteration order is the ONE
 * deterministic candidate order: pattern id, then variant index, then cell index.
 *
 * <p>{@link #rebuild()} re-runs at every pattern fold AND once post-load (station/extension layers
 * can settle after the pattern layer, and the Block-socket exclusion reads the station catalog).
 */
public final class PatternCatalog {

    private static final PatternCatalog INSTANCE = new PatternCatalog();

    /** One compiled pattern: the asset, its DETECT + HOLD forms, and the station its activation block derives. */
    record CompiledPattern(@Nonnull StructurePatternAsset asset, @Nonnull String id,
            @Nullable String stationId, @Nonnull BlockPattern<CellMatcher> detect,
            @Nonnull BlockPattern<CellMatcher> hold) {
    }

    /** One anchor-relative cell offset (whole blocks), the exclusion-set element for HOLD compiles. */
    record CellOffset(int x, int y, int z) {
    }

    /** The immutable published compile result world-thread readers walk. */
    private record Snapshot(@Nonnull List<CompiledPattern> compiled,
            @Nonnull Map<String, CompiledPattern> byId,
            @Nonnull Map<BlockPattern<CellMatcher>, CompiledPattern> byDetect,
            @Nonnull PatternIndex<CellMatcher> index) {
        static final Snapshot EMPTY = new Snapshot(List.of(), Map.of(), Map.of(), new PatternIndex<>());
    }

    private final ConcurrentHashMap<String, StructurePatternAsset> patterns = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    private PatternCatalog() {
    }

    @Nonnull
    public static PatternCatalog getInstance() {
        return INSTANCE;
    }

    /**
     * Replace (when {@code replace}) or add to the catalog with {@code layer} (already keyed
     * lowercase by the caller), then recompile and republish the snapshot.
     */
    public void fold(@Nonnull Map<String, StructurePatternAsset> layer, boolean replace) {
        if (replace) {
            patterns.clear();
        }
        patterns.putAll(layer);
        rebuild();
    }

    /**
     * Recompile every folded pattern against the CURRENT station catalog (the Block-socket
     * exclusion set and the derived station id both read it) and republish the snapshot. Safe to
     * re-run at any time; fully try-guarded per pattern, so one malformed pattern skips with a
     * warn instead of taking the whole compile down.
     */
    public void rebuild() {
        Map<String, StructurePatternAsset> sorted = new TreeMap<>(patterns);
        List<CompiledPattern> compiled = new ArrayList<>(sorted.size());
        Map<String, CompiledPattern> byId = new ConcurrentHashMap<>();
        Map<BlockPattern<CellMatcher>, CompiledPattern> byDetect = new java.util.IdentityHashMap<>();
        PatternIndex<CellMatcher> index = new PatternIndex<>();
        for (Map.Entry<String, StructurePatternAsset> e : sorted.entrySet()) {
            try {
                StructurePatternAsset asset = e.getValue();
                if (asset == null || asset.getCells() == null || asset.getCells().length == 0) {
                    continue;
                }
                String activateBlock = asset.getActivate() != null ? asset.getActivate().getBlock() : null;
                String stationId = stationIdForActivateBlock(activateBlock);
                CompiledPattern cp = compile(e.getKey(), asset, blockSocketOffsetsFor(stationId), stationId);
                compiled.add(cp);
                byId.put(cp.id(), cp);
                byDetect.put(cp.detect(), cp);
                seedIndex(index, cp);
            } catch (Throwable t) {
                Log.warn("PATTERN compile skipped '" + e.getKey() + "': " + t.getMessage());
            }
        }
        snapshot = new Snapshot(List.copyOf(compiled), byId, byDetect, index);
        if (!compiled.isEmpty()) {
            Log.fine("PATTERN catalog recompiled: " + compiled.size() + " pattern(s), index radius "
                    + index.maxBoundingRadius());
        }
    }

    /**
     * PURE compile of one pattern into its DETECT + HOLD forms. {@code excludedHoldOffsets} are
     * the ANCHOR-RELATIVE cell offsets HOLD must not test (the activated station's Block-socket
     * {@code At} cells); the anchor cell itself is never excluded. Unit-testable with no catalogs.
     */
    @Nonnull
    static CompiledPattern compile(@Nonnull String id, @Nonnull StructurePatternAsset asset,
            @Nonnull Set<CellOffset> excludedHoldOffsets, @Nullable String stationId) {
        StructurePatternAsset.Cell[] cells = asset.getCells();
        if (cells == null || cells.length == 0) {
            throw new IllegalArgumentException("pattern '" + id + "' authors no cells");
        }
        int anchorIndex = asset.anchorCellIndex();
        StructurePatternAsset.Cell anchorCell = cells[anchorIndex];
        int ax = anchorCell != null ? anchorCell.offsetX() : 0;
        int ay = anchorCell != null ? anchorCell.offsetY() : 0;
        int az = anchorCell != null ? anchorCell.offsetZ() : 0;
        boolean rotate = asset.getRotate() == null || asset.getRotate().effectiveYaw90();
        boolean mirror = asset.getRotate() != null && asset.getRotate().effectiveMirror();

        List<PatternCell<CellMatcher>> detectCells = new ArrayList<>(cells.length);
        for (StructurePatternAsset.Cell cell : cells) {
            StructurePatternAsset.Cell c = cell != null ? cell : StructurePatternAsset.Cell.of(null, null, null, null);
            detectCells.add(new PatternCell<>(c.offsetX(), c.offsetY(), c.offsetZ(), CellMatcher.of(c)));
        }
        BlockPattern<CellMatcher> detect = BlockPattern.compile(detectCells, anchorIndex, rotate, mirror);

        String activateBlock = asset.getActivate() != null ? asset.getActivate().getBlock() : null;
        List<PatternCell<CellMatcher>> holdCells = new ArrayList<>(cells.length);
        int holdAnchorIndex = -1;
        for (int i = 0; i < cells.length; i++) {
            StructurePatternAsset.Cell c = cells[i] != null ? cells[i]
                    : StructurePatternAsset.Cell.of(null, null, null, null);
            CellOffset rel = new CellOffset(c.offsetX() - ax, c.offsetY() - ay, c.offsetZ() - az);
            if (i == anchorIndex) {
                holdAnchorIndex = holdCells.size();
                CellMatcher anchorMatcher = activateBlock != null && !activateBlock.isBlank()
                        ? CellMatcher.exact(activateBlock)
                        : CellMatcher.of(c);
                holdCells.add(new PatternCell<>(c.offsetX(), c.offsetY(), c.offsetZ(), anchorMatcher));
                continue;
            }
            if (excludedHoldOffsets.contains(rel)) {
                continue;
            }
            holdCells.add(new PatternCell<>(c.offsetX(), c.offsetY(), c.offsetZ(), CellMatcher.of(c)));
        }
        BlockPattern<CellMatcher> hold = BlockPattern.compile(holdCells, holdAnchorIndex, rotate, mirror);
        return new CompiledPattern(asset, id.toLowerCase(Locale.ROOT), stationId, detect, hold);
    }

    /**
     * Registers every DETECT cell authoring an exact {@code ItemId} under that id (lowercased),
     * in (variant, cell) order - the placement probe's candidate order within one pattern.
     */
    private static void seedIndex(@Nonnull PatternIndex<CellMatcher> index, @Nonnull CompiledPattern cp) {
        int variants = cp.detect().variants().size();
        for (int v = 0; v < variants; v++) {
            for (int c = 0; c < cp.detect().cellCount(); c++) {
                String exactId = cp.detect().payload(c).itemId();
                if (exactId != null && !exactId.isBlank()) {
                    index.add(exactId.toLowerCase(Locale.ROOT), cp.detect(), v, c);
                }
            }
        }
    }

    /**
     * The station id the activation block derives through the {@code rpg_station_use} discovery
     * index (the same asset-derived index anchor discovery reads); null when the block resolves no
     * station (the pattern still activates - it swaps the block and stamps the stash - but no
     * Block-socket exclusion applies and toasts fall back to the pattern's own identity).
     */
    @Nullable
    private static String stationIdForActivateBlock(@Nullable String activateBlockId) {
        if (activateBlockId == null || activateBlockId.isBlank()) {
            return null;
        }
        try {
            return StationService.getInstance().stationIdForBlockItem(activateBlockId);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Every {@code Block}-route socket {@code At} offset of the activated station's actions, as
     * anchor-relative cell offsets (a Block socket's {@code At} is authored relative to the
     * station block, which IS the anchor once activated; its live facing composition rotates with
     * the block exactly as a matched variant rotates the cells, so the authored frames line up).
     * Empty when the station cannot be resolved.
     */
    @Nonnull
    private static Set<CellOffset> blockSocketOffsetsFor(@Nullable String stationId) {
        Set<CellOffset> out = new HashSet<>();
        if (stationId == null || stationId.isBlank()) {
            return out;
        }
        try {
            StationAsset station = StationCatalog.getInstance().getStation(stationId);
            if (station == null) {
                return out;
            }
            ActionDef[] actions = ActionResolver.effectiveActions(station);
            if (actions == null) {
                return out;
            }
            for (int i = 0; i < actions.length; i++) {
                String actionId = ActionResolver.effectiveActionId(actions[i], i);
                Custody custody = ActionResolver.resolve(station, actionId).getCustody();
                if (custody == null) {
                    continue;
                }
                for (Custody.ResolvedSocket socket : custody.effectiveSockets()) {
                    if (socket.itemRoute()) {
                        continue;
                    }
                    Vec3i at = socket.blockAt();
                    if (at != null) {
                        out.add(new CellOffset(at.effectiveX(), at.effectiveY(), at.effectiveZ()));
                    }
                }
            }
        } catch (Throwable t) {
            Log.fine("PATTERN Block-socket offsets unresolved for station '" + stationId + "': " + t.getMessage());
        }
        return out;
    }

    /** True when no pattern is folded - the cheap early-out every place/break hook checks first. */
    public boolean isEmpty() {
        return snapshot.compiled().isEmpty();
    }

    /**
     * A read-only, id-sorted snapshot of every FOLDED pattern asset (a cell-less pattern that the
     * compile skipped is still included - a consumer lint may well want to see it) - the source
     * the api's {@code patterns()} view is built from.
     */
    @Nonnull
    public Map<String, StructurePatternAsset> all() {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(patterns));
    }

    /** The compiled patterns, sorted by pattern id (the deterministic candidate order). */
    @Nonnull
    List<CompiledPattern> compiled() {
        return snapshot.compiled();
    }

    /** One compiled pattern by (lowercased) id, or null. */
    @Nullable
    CompiledPattern byId(@Nullable String patternId) {
        return patternId != null ? snapshot.byId().get(patternId.toLowerCase(Locale.ROOT)) : null;
    }

    /**
     * The compiled record a {@link PatternIndex.Candidate}'s pattern object belongs to (identity
     * lookup into the same snapshot the index came from), or null for a pattern from an older
     * snapshot (a re-fold raced the probe; the candidate is simply skipped).
     */
    @Nullable
    CompiledPattern compiledForDetect(@Nullable BlockPattern<CellMatcher> detect) {
        return detect != null ? snapshot.byDetect().get(detect) : null;
    }

    /** The placement index (exact-id DETECT cells only), from the current snapshot. */
    @Nonnull
    PatternIndex<CellMatcher> index() {
        return snapshot.index();
    }

    /** The widest bounding radius over every compiled pattern (the pending-anchor proximity check's reach). */
    public int maxBoundingRadius() {
        return snapshot.index().maxBoundingRadius();
    }

    /** Test hook: drop every folded pattern and republish the empty snapshot. */
    void clearForTest() {
        patterns.clear();
        snapshot = Snapshot.EMPTY;
    }
}
