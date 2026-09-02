package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.match.ItemMatch;
import com.ziggfreed.common.world.BlockOps;
import com.ziggfreed.common.world.pattern.CellPredicate;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;

/**
 * The compiled per-cell matcher a {@link com.ziggfreed.common.world.pattern.BlockPattern} walk
 * tests world blocks against, plus the PURE matching core. A matcher is compiled once per
 * authored {@link StructurePatternAsset.Cell} (the {@code Block} routes flattened out of
 * {@link ActionInput}, or the {@code Empty} sentinel) and shared by every variant.
 *
 * <p><b>Identity is the block's ITEM identity, base-normalized.</b> A state variant (lit/unlit,
 * loaded/empty) registers as its own block type, so the matcher normalizes what it read to the
 * base block first ({@code baseIdOf}) and resolves tags/resource families off that base's
 * containing item - two readings of one block always compare equal. The resolvers are injected so
 * the core is unit-testable with fixture data; {@link #livePredicate()} wires the real
 * {@link BlockOps} reads.
 */
final class PatternCells {

    /** The engine's own empty-block key, the answer a {@code BlockReader} gives for air. */
    static final String EMPTY_KEY = "Empty";

    private PatternCells() {
    }

    /**
     * One compiled cell matcher: {@code empty} = the cell must hold air; otherwise ANY authored
     * route satisfied matches (the {@code ActionInput} convention), and NO authored route is a
     * catch-all matching any real block.
     */
    record CellMatcher(@Nullable String itemId, @Nullable String resourceTypeId,
            @Nullable Map<String, String[]> tags, boolean empty) {

        /** The must-be-air matcher. */
        @Nonnull
        static CellMatcher air() {
            return new CellMatcher(null, null, null, true);
        }

        /** An exact-id matcher (the HOLD form's anchor cell). */
        @Nonnull
        static CellMatcher exact(@Nonnull String itemId) {
            return new CellMatcher(itemId, null, null, false);
        }

        /** The matcher for one authored cell; an {@code Empty} cell compiles to {@link #air()}. */
        @Nonnull
        static CellMatcher of(@Nonnull StructurePatternAsset.Cell cell) {
            if (cell.isEmptyCell()) {
                return air();
            }
            ActionInput block = cell.getBlock();
            if (block == null) {
                // A route-less cell (warned at decode): compiles to a matcher that matches
                // nothing, so a malformed cell can never accidentally complete a shape.
                return new CellMatcher(null, null, null, false);
            }
            return new CellMatcher(block.getItemId(), block.getResourceTypeId(), block.getTags(), false);
        }

        /** True when at least one block route is authored (a routeless non-air matcher matches nothing). */
        boolean hasAnyRoute() {
            return (itemId != null && !itemId.isBlank())
                    || (resourceTypeId != null && !resourceTypeId.isBlank())
                    || (tags != null && !tags.isEmpty());
        }
    }

    /**
     * PURE: does the block id a reader answered satisfy this matcher? {@code blockItemId} is the
     * raw read (possibly a state-variant id; air arrives as {@value #EMPTY_KEY}, never null - an
     * unreadable position fails the walk before this is consulted).
     */
    static boolean matches(@Nonnull CellMatcher m, @Nonnull String blockItemId,
            @Nonnull UnaryOperator<String> baseIdOf,
            @Nonnull Function<String, List<String>> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        boolean isAir = EMPTY_KEY.equalsIgnoreCase(blockItemId) || blockItemId.isBlank();
        if (m.empty()) {
            return isAir;
        }
        if (isAir) {
            return false;
        }
        if (!m.hasAnyRoute()) {
            // A malformed both/neither cell never matches (decode-warned); a deliberate
            // catch-all "any block" cell is not authorable, matching blockSocketMatches' rule
            // that a shape names what it is made of.
            return false;
        }
        String base = baseIdOf.apply(blockItemId);
        String wantItem = m.itemId();
        if (wantItem != null && !wantItem.isBlank()
                && (wantItem.equalsIgnoreCase(base) || wantItem.equalsIgnoreCase(blockItemId))) {
            return true;
        }
        String wantFamily = m.resourceTypeId();
        if (wantFamily != null && !wantFamily.isBlank()) {
            List<String> families = resourceTypesOf.apply(base);
            if (families != null) {
                for (String family : families) {
                    if (wantFamily.equalsIgnoreCase(family)) {
                        return true;
                    }
                }
            }
        }
        Map<String, String[]> wantTags = m.tags();
        if (wantTags != null && !wantTags.isEmpty()) {
            // The shared tag semantics every matcher in this mod speaks (values ANY-of plus the
            // empty-value-list key-presence form), so a pattern cell's tag route can never drift
            // from an ingredient's or an action selector's.
            return ItemMatch.tags(wantTags, tagsOf.apply(base));
        }
        return false;
    }

    /** {@link #matches} over injected resolvers, as the walk's {@link CellPredicate}. */
    @Nonnull
    static CellPredicate<CellMatcher> predicate(@Nonnull UnaryOperator<String> baseIdOf,
            @Nonnull Function<String, List<String>> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        return (payload, blockItemId) -> matches(payload, blockItemId, baseIdOf, resourceTypesOf, tagsOf);
    }

    /** The live predicate over the real asset-map identity reads. WORLD-THREAD ONLY, like the walk itself. */
    @Nonnull
    static CellPredicate<CellMatcher> livePredicate() {
        return predicate(BlockOps::baseItemIdOf, BlockOps::resourceTypeIdsOf, BlockOps::rawTagsOf);
    }
}
