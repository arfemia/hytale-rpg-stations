package com.ziggfreed.rpgstations.station;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * PURE, unit-testable decision cores for placed-input custody (design section 9.4, phase-2 leg
 * C): the placement-quantity math, the claim drain/peek engine (family-matched via an injected
 * live-resolver, mirroring {@code StationToolScaling}'s injected-shape pattern so nothing here
 * constructs a live {@code Item}/{@code AssetBuilderCodec}-backed engine type), the
 * placement-acceptance matchers, and the auto-return branch decision. Zero engine/store touch.
 */
final class StationCustody {

    private StationCustody() {
    }

    /**
     * How much of a {@code heldCount}-sized stack can move into a claim currently holding
     * {@code currentTotal}, capped at {@code maxQuantity}. Never negative, never more than either
     * the held count or the remaining headroom.
     */
    static int placeableQuantity(int currentTotal, int heldCount, int maxQuantity) {
        if (heldCount <= 0 || maxQuantity <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(heldCount, maxQuantity - currentTotal));
    }

    /**
     * Peek: the total quantity in {@code claim} matching {@code itemId} (exact) or
     * {@code resourceTypeId} (family, tested per tallied item id via {@code resourceTypesOf}),
     * WITHOUT mutating. {@code claim} null (nothing placed yet) is 0.
     */
    static int available(@Nullable StationCustodyClaim claim, @Nullable String itemId,
            @Nullable String resourceTypeId, @Nonnull Function<String, String[]> resourceTypesOf) {
        if (claim == null) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<String, Integer> e : claim.items().entrySet()) {
            if (matchesEntry(e.getKey(), itemId, resourceTypeId, resourceTypesOf) && e.getValue() != null) {
                total += e.getValue();
            }
        }
        return total;
    }

    /**
     * Drain up to {@code quantity} of {@code itemId}/{@code resourceTypeId} from {@code claim},
     * oldest-placed-first ({@link StationCustodyClaim}'s insertion order); zeroed entries are
     * removed (no dangling zero-quantity items). {@code drainedOut}, when non-null, accumulates
     * the REAL item ids actually removed (for the session item ledger - mirrors
     * {@code StationService#tallyResourceConsumption}'s "tally the real drained ids" convention).
     * Returns the amount actually drained (0..quantity; less than {@code quantity} means the
     * claim ran short - the caller stops the session {@code OUT_OF_INPUTS}, never partial-consumes
     * a cycle).
     */
    static int drain(@Nullable StationCustodyClaim claim, @Nullable String itemId, @Nullable String resourceTypeId,
            int quantity, @Nonnull Function<String, String[]> resourceTypesOf,
            @Nullable Map<String, Integer> drainedOut) {
        if (claim == null || quantity <= 0) {
            return 0;
        }
        int remaining = quantity;
        Iterator<Map.Entry<String, Integer>> it = claim.items().entrySet().iterator();
        while (it.hasNext() && remaining > 0) {
            Map.Entry<String, Integer> e = it.next();
            Integer have = e.getValue();
            if (have == null || have <= 0 || !matchesEntry(e.getKey(), itemId, resourceTypeId, resourceTypesOf)) {
                continue;
            }
            int take = Math.min(have, remaining);
            remaining -= take;
            if (drainedOut != null) {
                drainedOut.merge(e.getKey(), take, Integer::sum);
            }
            if (take >= have) {
                it.remove();
            } else {
                e.setValue(have - take);
            }
        }
        return quantity - remaining;
    }

    private static boolean matchesEntry(@Nonnull String entryItemId, @Nullable String wantItemId,
            @Nullable String wantResourceTypeId, @Nonnull Function<String, String[]> resourceTypesOf) {
        if (wantItemId != null && !wantItemId.isBlank()) {
            return wantItemId.equalsIgnoreCase(entryItemId);
        }
        if (wantResourceTypeId != null && !wantResourceTypeId.isBlank()) {
            String[] types = resourceTypesOf.apply(entryItemId);
            if (types == null) {
                return false;
            }
            for (String t : types) {
                if (wantResourceTypeId.equalsIgnoreCase(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The explicit {@link Custody#getInput()} placement matcher: {@code ActionInput}'s
     * ItemId/ResourceTypeId/Tags/Function routes (match = ANY route satisfied, the {@code Tool}/
     * {@code ActionInput} convention). SMOKE-FIX S4: the {@code Function} route now matches
     * (previously "deferred to phase-2 leg E" per stale javadoc, but leg E's own
     * {@code ActionResolver.matches}/{@code matchesAnyResourceType} DID land it for ACTION
     * SELECTION - this custody PLACEMENT matcher was simply never updated to match, so the
     * anvil's {@code enhance} action's {@code Custody.Input:{"Function":"Weapon"}} never accepted
     * a held weapon for placement even though holding one correctly SELECTED the enhance action).
     */
    static boolean matchesInput(@Nonnull ActionInput matcher, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        String wantItem = matcher.getItemId();
        if (wantItem != null && !wantItem.isBlank() && wantItem.equalsIgnoreCase(heldItemId)) {
            return true;
        }
        String wantResource = matcher.getResourceTypeId();
        if (wantResource != null && !wantResource.isBlank() && heldResourceTypeIds != null) {
            for (String t : heldResourceTypeIds) {
                if (wantResource.equalsIgnoreCase(t)) {
                    return true;
                }
            }
        }
        String wantFunction = matcher.getFunction();
        if (wantFunction != null && !wantFunction.isBlank() && wantFunction.equalsIgnoreCase(heldFunction)) {
            return true;
        }
        Map<String, String[]> wantTags = matcher.getTags();
        if (wantTags != null && !wantTags.isEmpty() && heldTags != null && !heldTags.isEmpty()) {
            for (Map.Entry<String, String[]> req : wantTags.entrySet()) {
                String[] have = heldTags.get(req.getKey());
                if (have == null || req.getValue() == null) {
                    continue;
                }
                for (String want : req.getValue()) {
                    for (String h : have) {
                        if (want != null && want.equalsIgnoreCase(h)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The no-explicit-{@code Input} fallback: does {@code heldItemId}/{@code heldResourceTypeIds}
     * satisfy ANY resolved {@code Recipe.Conversions} entry's input (the sawmill's "logs by
     * ResourceTypeId family" - zero extra authoring on top of the existing {@code Recipe} group)?
     */
    static boolean matchesAnyConversionInput(@Nonnull StationAsset.Conversion[] conversions,
            @Nullable String heldItemId, @Nullable String[] heldResourceTypeIds) {
        for (StationAsset.Conversion c : conversions) {
            if (c == null || c.getInput() == null) {
                continue;
            }
            StationAsset.Ingredient in = c.getInput();
            String resourceId = in.getResourceTypeId();
            if (resourceId != null && !resourceId.isBlank()) {
                if (heldResourceTypeIds != null) {
                    for (String t : heldResourceTypeIds) {
                        if (resourceId.equalsIgnoreCase(t)) {
                            return true;
                        }
                    }
                }
                continue;
            }
            String itemId = in.getItemId();
            if (itemId != null && !itemId.isBlank() && itemId.equalsIgnoreCase(heldItemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The auto-return branch decision (design 9.4: "return to inventory; if unreachable, drop at
     * block"): return to inventory only when the owner is reachable (a live store/ref resolved)
     * AND their inventory has room for the whole claim; otherwise drop everything at the block -
     * never a partial add (that would split the claim between two destinations, a dupe-adjacent
     * shape). Pure so every combination is exhaustively unit-tested without a live server.
     */
    static boolean shouldReturnToInventory(boolean ownerReachable, boolean hasInventoryRoom) {
        return ownerReachable && hasInventoryRoom;
    }
}
