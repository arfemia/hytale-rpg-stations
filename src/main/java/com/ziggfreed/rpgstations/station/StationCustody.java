package com.ziggfreed.rpgstations.station;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.common.codec.TagMatch;

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
     * PURE (decision 74): does {@code Custody.SingleFamily} allow this material into the claim?
     *
     * <p>{@code singleFamily} false (the default) always allows - materials mix freely. When true,
     * an EMPTY claim allows anything the ordinary acceptance matcher passed, and a non-empty claim
     * locks to the FIRST-placed item ({@link StationCustodyClaim#items()} is insertion-ordered, so
     * the first key IS the first placement): the candidate is allowed only when it shares a resource
     * -type FAMILY with that item, or is literally the same item id. That is the exclusivity a
     * per-family quantity map could not express - "50 oak OR 50 pine, never 100 mixed" - and it
     * lifts by itself the moment the claim empties.
     *
     * <p>An item with NO resource types falls back to exact-id equality, so a family-less material
     * still locks its claim to itself rather than accidentally admitting everything.
     */
    static boolean acceptsFamily(boolean singleFamily, @Nullable StationCustodyClaim claim,
            @Nullable String candidateItemId, @Nullable String[] candidateResourceTypeIds,
            @Nonnull Function<String, String[]> resourceTypesOf) {
        if (!singleFamily || claim == null || claim.items().isEmpty()) {
            return true;
        }
        String lockedItemId = claim.items().keySet().iterator().next();
        if (lockedItemId.equalsIgnoreCase(candidateItemId)) {
            return true;
        }
        String[] lockedFamilies = resourceTypesOf.apply(lockedItemId);
        if (lockedFamilies == null || lockedFamilies.length == 0 || candidateResourceTypeIds == null) {
            return false;
        }
        for (String locked : lockedFamilies) {
            for (String candidate : candidateResourceTypeIds) {
                if (locked != null && locked.equalsIgnoreCase(candidate)) {
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
        return TagMatch.matches(matcher.getTags(), heldTags);
    }

    /**
     * The no-explicit-{@code Input} fallback: does {@code heldItemId}/{@code heldResourceTypeIds}
     * satisfy ANY resolved {@code Recipe.Conversions} entry's input (the sawmill's "logs by
     * ResourceTypeId family" - zero extra authoring on top of the existing {@code Recipe} group)?
     */
    static boolean matchesAnyConversionInput(@Nonnull StationAsset.Conversion[] conversions,
            @Nullable String heldItemId, @Nullable String[] heldResourceTypeIds) {
        for (StationAsset.Conversion c : conversions) {
            if (matchesConversionInput(c, heldItemId, heldResourceTypeIds)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The SINGLE-conversion half of {@link #matchesAnyConversionInput} (extracted for decision 66's
     * picker preview, which needs to know WHICH conversion a material satisfies rather than merely
     * whether any does): does {@code heldItemId}/{@code heldResourceTypeIds} satisfy ANY of this
     * conversion's inputs? A multi-input conversion accepts each of its materials into custody
     * independently (a station holding "2 planks + 1 nail" is loaded one material at a time), so the
     * match is ANY-of, never all-of. A null conversion or one with no input never matches.
     */
    static boolean matchesConversionInput(@Nullable StationAsset.Conversion conversion,
            @Nullable String heldItemId, @Nullable String[] heldResourceTypeIds) {
        if (conversion == null || conversion.getInput() == null) {
            return false;
        }
        for (Ingredient in : conversion.getInput()) {
            if (matchesIngredient(in, heldItemId, heldResourceTypeIds)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PURE: does {@code heldItemId}/{@code heldResourceTypeIds} satisfy ONE {@link Ingredient}? An
     * ingredient authors exactly one of {@code ResourceTypeId} (a native family - the sawmill's "any
     * Trunk of this species") or {@code ItemId} (exact); the family route wins when both are somehow
     * present, matching the pre-array loop byte for byte.
     */
    static boolean matchesIngredient(@Nullable Ingredient in, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds) {
        if (in == null) {
            return false;
        }
        String resourceId = in.getResourceTypeId();
        if (resourceId != null && !resourceId.isBlank()) {
            if (heldResourceTypeIds != null) {
                for (String t : heldResourceTypeIds) {
                    if (resourceId.equalsIgnoreCase(t)) {
                        return true;
                    }
                }
            }
            return false;
        }
        String itemId = in.getItemId();
        return itemId != null && !itemId.isBlank() && itemId.equalsIgnoreCase(heldItemId);
    }
}
