package com.ziggfreed.rpgstations.api;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A read-only projection of ONE folded multiblock structure pattern, handed to a consumer through
 * {@link RpgStationsApi#patterns()}. It exposes the pattern's REFERENCE STRUCTURE - which blocks
 * the shape names, where its cells sit, what its anchor becomes - so a mod can lint its own
 * content against the shapes this engine recognizes (a placement guide, a conflicting-pattern
 * check, a "does my block appear in any pattern" scan) without reaching into the live engine
 * catalog. No live world handles: every accessor returns a plain immutable snapshot taken at
 * query time, and a consumer may retain whatever it likes.
 *
 * <p>An interface rather than a record on purpose: it is a view likely to grow, and a new
 * default-bodied accessor is the one post-freeze addition shape the growth policy allows (see
 * {@code api/CLAUDE.md}).
 */
public interface PatternView {

    /** {@link CellView#route()}: the cell matches one exact block item id; the value is that id. */
    String ROUTE_ITEM_ID = "ItemId";

    /** {@link CellView#route()}: the cell matches a native resource-type family; the value is the family id. */
    String ROUTE_RESOURCE_TYPE = "ResourceTypeId";

    /**
     * {@link CellView#route()}: the cell matches by item tags; the value is a readable summary,
     * one {@code key} or {@code key=v1|v2} term per tag entry, comma-joined in authored order.
     */
    String ROUTE_TAGS = "Tags";

    /** {@link CellView#route()}: the cell must hold AIR; the value is null. */
    String ROUTE_EMPTY = "Empty";

    /**
     * {@link CellView#route()}: the cell authors no usable matcher (a malformed both-or-neither
     * cell, decode-warned engine-side) - it matches nothing and the shape can never complete.
     */
    String ROUTE_NONE = "None";

    /** The pattern's (lowercased) id. */
    @Nonnull
    String id();

    /**
     * The block item id the anchor is swapped to when a completed build activates, or {@code null}
     * when the pattern authors none (an inert pattern, warned engine-side).
     */
    @Nullable
    String activateBlock();

    /**
     * The EFFECTIVE block item id a broken build reverts its anchor to: the authored revert block,
     * else the anchor cell's own exact block id; {@code null} when neither exists (the revert then
     * leaves whatever stands there).
     */
    @Nullable
    String revertBlock();

    /** Whether the shape is recognized in all four yaw quarter-turn orientations (reader-default true). */
    boolean rotateYaw90();

    /** Whether the X-mirrored form of each orientation is also recognized (reader-default false). */
    boolean rotateMirror();

    /** How many cells the shape authors (0 for a cell-less pattern, which can never be built). */
    int cellCount();

    /** The cells in authored order, offsets normalized anchor-relative (the anchor cell reads (0,0,0)). */
    @Nonnull
    List<CellView> cells();

    /**
     * One cell of the shape: its anchor-relative offset, whether it is the anchor, and a summary
     * of what must stand there - the matcher's route (one of the {@code ROUTE_*} constants) plus
     * that route's value. A cell may legally author several block routes (any satisfied route
     * matches); this summary reports the DOMINANT one, in {@link #ROUTE_ITEM_ID} then
     * {@link #ROUTE_RESOURCE_TYPE} then {@link #ROUTE_TAGS} order.
     */
    record CellView(int offsetX, int offsetY, int offsetZ, boolean anchor,
            @Nonnull String route, @Nullable String value) {
    }
}
