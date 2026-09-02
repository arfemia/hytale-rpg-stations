package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.common.match.ItemMatch;

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
     * The SOCKET-aware placement quantity: how much of a {@code heldCount}-sized stack one press
     * moves into a socket whose pile holds {@code pileTotal} while the whole block holds
     * {@code blockTotal}. The result is the smallest of: the press size ({@code placePerPress},
     * null = the whole held stack), the held count, the socket's remaining room
     * ({@code socketMaxQuantity - pileTotal}, already the min-of-caps per
     * {@link Custody.ResolvedSocket#maxQuantity()}), and the block's remaining room
     * ({@code custodyMaxQuantity - blockTotal} - the per-block total the custody-level cap holds
     * whatever the per-socket caps sum to). Never negative.
     */
    static int placeableQuantity(int pileTotal, int blockTotal, int heldCount,
            int socketMaxQuantity, int custodyMaxQuantity, @Nullable Integer placePerPress) {
        if (heldCount <= 0 || socketMaxQuantity <= 0 || custodyMaxQuantity <= 0) {
            return 0;
        }
        int press = placePerPress != null && placePerPress > 0 ? Math.min(placePerPress, heldCount) : heldCount;
        int socketRoom = socketMaxQuantity - pileTotal;
        int blockRoom = custodyMaxQuantity - blockTotal;
        return Math.max(0, Math.min(press, Math.min(socketRoom, blockRoom)));
    }

    /**
     * Peek: the total quantity in {@code claim}'s {@value StationCustodyClaim#MAIN_PILE} pile
     * matching {@code itemId} (exact) or {@code resourceTypeId} (family, tested per tallied item
     * id via {@code resourceTypesOf}), WITHOUT mutating. {@code claim} null (nothing placed yet)
     * is 0. The socket-addressed form is {@link #availableInPile} over
     * {@code claim.items(socketId)}.
     */
    static int available(@Nullable StationCustodyClaim claim, @Nullable String itemId,
            @Nullable String resourceTypeId, @Nonnull Function<String, String[]> resourceTypesOf) {
        return availableInPile(claim != null ? claim.items() : null, itemId, resourceTypeId, resourceTypesOf);
    }

    /** {@link #available(StationCustodyClaim, String, String, Function)} over ONE pile's live tally. */
    static int availableInPile(@Nullable Map<String, Integer> items, @Nullable String itemId,
            @Nullable String resourceTypeId, @Nonnull Function<String, String[]> resourceTypesOf) {
        return availableInPile(items,
                entryId -> matchesEntry(entryId, itemId, resourceTypeId, resourceTypesOf));
    }

    /**
     * The predicate core of {@link #availableInPile(Map, String, String, Function)}: total quantity
     * of the pile entries {@code matches} accepts. The id/family form above and the full
     * ingredient-route form ({@link #ingredientEntryMatcher}, which also speaks the Tags and
     * match-any routes) both count through here.
     */
    static int availableInPile(@Nullable Map<String, Integer> items, @Nonnull Predicate<String> matches) {
        if (items == null) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getValue() != null && matches.test(e.getKey())) {
                total += e.getValue();
            }
        }
        return total;
    }

    /**
     * Drain up to {@code quantity} of {@code itemId}/{@code resourceTypeId} from {@code claim}'s
     * {@value StationCustodyClaim#MAIN_PILE} pile, oldest-placed-first
     * ({@link StationCustodyClaim}'s insertion order); zeroed entries are removed (no dangling
     * zero-quantity items). {@code drainedOut}, when non-null, accumulates the REAL item ids
     * actually removed (for the session item ledger - mirrors
     * {@code StationService#tallyResourceConsumption}'s "tally the real drained ids" convention).
     * Returns the amount actually drained (0..quantity; less than {@code quantity} means the
     * claim ran short - the caller stops the session {@code OUT_OF_INPUTS}, never partial-consumes
     * a cycle). The socket-addressed form is {@link #drainFromPile} over {@code claim.items(socketId)} - one pile per call, so a drain can never cross a
     * socket boundary.
     */
    static int drain(@Nullable StationCustodyClaim claim, @Nullable String itemId, @Nullable String resourceTypeId,
            int quantity, @Nonnull Function<String, String[]> resourceTypesOf,
            @Nullable Map<String, Integer> drainedOut) {
        return drainFromPile(claim != null ? claim.items() : null, itemId, resourceTypeId, quantity,
                resourceTypesOf, drainedOut);
    }

    /** {@link #drain(StationCustodyClaim, String, String, int, Function, Map)} over ONE pile's live tally. */
    static int drainFromPile(@Nullable Map<String, Integer> items, @Nullable String itemId, @Nullable String resourceTypeId,
            int quantity, @Nonnull Function<String, String[]> resourceTypesOf,
            @Nullable Map<String, Integer> drainedOut) {
        return drainFromPile(items,
                entryId -> matchesEntry(entryId, itemId, resourceTypeId, resourceTypesOf),
                quantity, drainedOut);
    }

    /**
     * The predicate core of {@link #drainFromPile(Map, String, String, int, Function, Map)}:
     * oldest-placed-first over the entries {@code matches} accepts, same removal/tally contract.
     * The full ingredient-route form drains through here with {@link #ingredientEntryMatcher}.
     */
    static int drainFromPile(@Nullable Map<String, Integer> items, @Nonnull Predicate<String> matches,
            int quantity, @Nullable Map<String, Integer> drainedOut) {
        if (items == null || quantity <= 0) {
            return 0;
        }
        int remaining = quantity;
        Iterator<Map.Entry<String, Integer>> it = items.entrySet().iterator();
        while (it.hasNext() && remaining > 0) {
            Map.Entry<String, Integer> e = it.next();
            Integer have = e.getValue();
            if (have == null || have <= 0 || !matches.test(e.getKey())) {
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
            return ItemMatch.itemId(wantItemId, entryItemId);
        }
        return ItemMatch.resourceFamily(wantResourceTypeId, resourceTypesOf.apply(entryItemId));
    }

    /**
     * The FULL ingredient-route pile-entry matcher: which tallied item ids one {@link Ingredient}
     * accepts, all four routes - exact {@code ItemId}, {@code ResourceTypeId} family,
     * {@code Tags}, and the route-less MATCH-ANY (which accepts every entry). The identity
     * resolvers are injected so the availability count, the exact-set check and the drain all
     * answer through ONE matcher and stay unit-testable without a live asset map. Route pick when
     * several are somehow authored (validator-flagged content) keeps the shipped order: family,
     * then exact id, then tags.
     */
    @Nonnull
    static Predicate<String> ingredientEntryMatcher(@Nonnull Ingredient in,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        if (in.hasResourceRoute()) {
            return entryId -> ItemMatch.resourceFamily(in.getResourceTypeId(), resourceTypesOf.apply(entryId));
        }
        if (in.hasItemRoute()) {
            return entryId -> ItemMatch.itemId(in.getItemId(), entryId);
        }
        if (in.hasTagsRoute()) {
            return entryId -> ItemMatch.tags(in.getTags(), tagsOf.apply(entryId));
        }
        return entryId -> true; // match-any: the "whatever is in the pot" row
    }

    /**
     * PURE (the {@code IsExactSet} knob): do the pile(s) this conversion's inputs draw from hold
     * NOTHING beyond those inputs? Inputs are grouped by their resolved socket
     * ({@link #socketIdFor}: the entry's own {@code Socket}, else the first Item socket), and for
     * each drawn pile the check is twofold: the pile's TOTAL quantity equals the summed needs
     * addressed to it (no extra quantity), and every item id present matches at least one of the
     * inputs drawing from it (no foreign material). Piles no input draws from are never consulted -
     * extras elsewhere never block. Availability (each input individually satisfied) is the
     * caller's own preceding check, not repeated here.
     */
    static boolean exactSetSatisfied(@Nonnull StationAsset.Conversion c,
            @Nonnull Function<String, Map<String, Integer>> pileOf,
            @Nonnull List<Custody.ResolvedSocket> sockets,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        Ingredient[] inputs = c.getInput();
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        Map<String, List<Ingredient>> bySocket = new LinkedHashMap<>();
        Map<String, Integer> needBySocket = new LinkedHashMap<>();
        for (Ingredient in : inputs) {
            if (in == null) {
                continue;
            }
            String socketId = socketIdFor(in.getSocket(), null, sockets);
            bySocket.computeIfAbsent(socketId, k -> new ArrayList<>()).add(in);
            needBySocket.merge(socketId, in.effectiveQuantity(), Integer::sum);
        }
        for (Map.Entry<String, List<Ingredient>> drawn : bySocket.entrySet()) {
            Map<String, Integer> pile = pileOf.apply(drawn.getKey());
            int total = 0;
            if (pile != null) {
                for (Integer q : pile.values()) {
                    total += q != null ? q : 0;
                }
            }
            if (total != needBySocket.getOrDefault(drawn.getKey(), 0)) {
                return false;
            }
            if (pile == null) {
                continue; // total 0 == need 0: an all-match-any zero-need group, vacuously exact
            }
            for (String entryId : pile.keySet()) {
                boolean matched = false;
                for (Ingredient in : drawn.getValue()) {
                    if (ingredientEntryMatcher(in, resourceTypesOf, tagsOf).test(entryId)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
        }
        return true;
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
        return pileAcceptsFamily(singleFamily, claim != null ? claim.items() : null,
                candidateItemId, candidateResourceTypeIds, resourceTypesOf);
    }

    /**
     * {@link #acceptsFamily(boolean, StationCustodyClaim, String, String[], Function)} over ONE
     * pile's tally - the per-socket form (decision 89: {@code SingleFamily} is scoped to a
     * socket's own pile, so the meat rack locking to beef never stops the herb basket taking
     * thyme).
     */
    static boolean pileAcceptsFamily(boolean singleFamily, @Nullable Map<String, Integer> items,
            @Nullable String candidateItemId, @Nullable String[] candidateResourceTypeIds,
            @Nonnull Function<String, String[]> resourceTypesOf) {
        if (!singleFamily || items == null || items.isEmpty()) {
            return true;
        }
        String lockedItemId = items.keySet().iterator().next();
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
        if (ItemMatch.any(matcher.getItemId(), matcher.getTags(), matcher.getResourceTypeId(),
                heldItemId, heldTags, heldResourceTypeIds)) {
            return true;
        }
        String wantFunction = matcher.getFunction();
        return wantFunction != null && !wantFunction.isBlank() && wantFunction.equalsIgnoreCase(heldFunction);
    }

    /**
     * The no-explicit-{@code Input} fallback: does {@code heldItemId}/{@code heldResourceTypeIds}
     * satisfy ANY resolved {@code Recipe.Conversions} entry's input (the sawmill's "logs by
     * ResourceTypeId family" - zero extra authoring on top of the existing {@code Recipe} group)?
     * The tag-less overload for callers with no resolved tag map; the engine passes the held
     * item's live raw tags through the 4-arg form so a {@code Tags}-route input accepts too.
     */
    static boolean matchesAnyConversionInput(@Nonnull StationAsset.Conversion[] conversions,
            @Nullable String heldItemId, @Nullable String[] heldResourceTypeIds) {
        return matchesAnyConversionInput(conversions, heldItemId, heldResourceTypeIds, null);
    }

    /** {@link #matchesAnyConversionInput(StationAsset.Conversion[], String, String[])} with the held item's raw tags. */
    static boolean matchesAnyConversionInput(@Nonnull StationAsset.Conversion[] conversions,
            @Nullable String heldItemId, @Nullable String[] heldResourceTypeIds,
            @Nullable Map<String, String[]> heldTags) {
        for (StationAsset.Conversion c : conversions) {
            if (matchesConversionInput(c, heldItemId, heldResourceTypeIds, heldTags)) {
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
        return matchesConversionInput(conversion, heldItemId, heldResourceTypeIds, null);
    }

    /** {@link #matchesConversionInput(StationAsset.Conversion, String, String[])} with the held item's raw tags. */
    static boolean matchesConversionInput(@Nullable StationAsset.Conversion conversion,
            @Nullable String heldItemId, @Nullable String[] heldResourceTypeIds,
            @Nullable Map<String, String[]> heldTags) {
        if (conversion == null || conversion.getInput() == null) {
            return false;
        }
        for (Ingredient in : conversion.getInput()) {
            if (matchesIngredient(in, heldItemId, heldResourceTypeIds, heldTags)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PURE: does {@code heldItemId}/{@code heldResourceTypeIds} satisfy ONE {@link Ingredient}? The
     * tag-less overload; a {@code Tags}-route ingredient never matches through it (the caller has
     * no tag identity to test), and a route-less MATCH-ANY ingredient matches everything.
     */
    static boolean matchesIngredient(@Nullable Ingredient in, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds) {
        return matchesIngredient(in, heldItemId, heldResourceTypeIds, null);
    }

    /**
     * PURE: does a held/placed material satisfy ONE {@link Ingredient}? An ingredient authors at
     * most one of {@code ResourceTypeId} (a native family - the sawmill's "any Trunk of this
     * species"), {@code ItemId} (exact) or {@code Tags} (native item tags); NONE authored is the
     * match-any input, which accepts everything. The family route wins when several are somehow
     * present (validator-flagged content), matching the pre-array loop byte for byte; the
     * comparing itself is ziggfreed-common's {@link ItemMatch}.
     */
    static boolean matchesIngredient(@Nullable Ingredient in, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds, @Nullable Map<String, String[]> heldTags) {
        if (in == null) {
            return false;
        }
        if (in.hasResourceRoute()) {
            return ItemMatch.resourceFamily(in.getResourceTypeId(), heldResourceTypeIds);
        }
        if (in.hasItemRoute()) {
            return ItemMatch.itemId(in.getItemId(), heldItemId);
        }
        if (in.hasTagsRoute()) {
            return ItemMatch.tags(in.getTags(), heldTags);
        }
        return true; // match-any
    }

    // ==================== per-socket ownership + sharing (decision 82: one owner per pile) ====================

    /**
     * PURE: may {@code player} add material to a socket's pile? A NON-EMPTY pile belongs to
     * exactly one player and only that player tops it up - {@code Share.Place} never opens
     * co-mingling. An EMPTY (or absent) pile is open to the stash's own owner always, and to
     * anyone else only under {@code Share.Place} (the first contributor then owns it until it
     * drains empty again). A non-empty pile that recorded no owner of its own falls back to the
     * stash owner; a stash with no owner at all (a fresh block) is open - placing is what creates
     * it.
     */
    static boolean canPlace(boolean sharePlace, @Nullable UUID stashOwner, @Nullable UUID pileOwner,
            boolean pileEmpty, @Nonnull UUID player) {
        if (!pileEmpty) {
            UUID owner = pileOwner != null ? pileOwner : stashOwner;
            return owner == null || player.equals(owner);
        }
        if (stashOwner == null || player.equals(stashOwner)) {
            return true;
        }
        return sharePlace;
    }

    /**
     * PURE: may {@code player} engage work that would consume from a socket's pile?
     * {@code owner} is the pile's EFFECTIVE owner (its own recorded one, else the stash's). An
     * empty pile gates nothing (there is nothing foreign to consume); a foreign non-empty pile
     * needs {@code Share.Use}.
     */
    static boolean canUse(boolean shareUse, @Nullable UUID owner, boolean pileEmpty, @Nonnull UUID player) {
        return pileEmpty || owner == null || player.equals(owner) || shareUse;
    }

    /**
     * PURE: may {@code player} take a socket's pile back out (press-F retrieval on its display
     * prop)? {@code owner} is the pile's effective owner; {@code Share.Reclaim} relaxes the
     * owner-only rule for that socket.
     */
    static boolean canReclaim(boolean shareReclaim, @Nullable UUID owner, @Nonnull UUID player) {
        return owner == null || player.equals(owner) || shareReclaim;
    }

    // ==================== placement routing (authored socket order) ====================

    /** Why a press placed nothing, most specific first - drives the keyed refusal toast. */
    enum PlacementDenial {
        /** A socket accepted the material but its pile belongs to someone else (or Share.Place denied a fresh pile). */
        NOT_SHARED,
        /** A socket accepted the material but has no room left (its own cap, or the block total). */
        FULL,
        /** No Item socket accepts this material at all. */
        WRONG_INPUT
    }

    /** The routing answer: the receiving socket + quantity, or the most specific denial seen. */
    record PlacementRoute(@Nullable Custody.ResolvedSocket socket, int quantity,
            @Nullable PlacementDenial denial) {

        boolean placed() {
            return socket != null && quantity > 0;
        }
    }

    /**
     * PURE placement routing (decision 91/92): offer a held stack to {@code sockets} in AUTHORED
     * ORDER and return the FIRST Item socket that (a) accepts the material ({@code matches} - the
     * caller injects the live matcher: the socket's own {@code Match} when authored, else the
     * derived-from-recipe acceptance), (b) passes its pile's single-family lock, (c) passes the
     * per-pile ownership/share rule ({@link #canPlace}), and (d) has capacity left
     * ({@link #placeableQuantity(int, int, int, int, int, Integer)}'s min of press size, socket
     * room and block room). When nothing places, the returned denial is the most SPECIFIC reason
     * any socket got close: a share refusal outranks a full socket outranks nothing-matched.
     * Block-route sockets never receive placements and are skipped.
     */
    @Nonnull
    static PlacementRoute routePlacement(@Nonnull List<Custody.ResolvedSocket> sockets,
            @Nullable StationCustodyClaim claim, @Nonnull UUID player,
            @Nullable String heldItemId, int heldCount, @Nullable String[] heldResourceTypeIds,
            int custodyMaxQuantity,
            @Nonnull Predicate<Custody.ResolvedSocket> matches,
            @Nonnull Function<String, String[]> resourceTypesOf) {
        PlacementDenial denial = null;
        int blockTotal = claim != null ? claim.totalQuantity() : 0;
        for (Custody.ResolvedSocket socket : sockets) {
            if (!socket.itemRoute()) {
                continue;
            }
            Map<String, Integer> pileItems = claim != null ? claim.items(socket.id()) : null;
            if (!matches.test(socket)
                    || !pileAcceptsFamily(socket.singleFamily(), pileItems, heldItemId, heldResourceTypeIds,
                            resourceTypesOf)) {
                denial = mostSpecific(denial, PlacementDenial.WRONG_INPUT);
                continue;
            }
            UUID stashOwner = claim != null ? claim.ownerId : null;
            UUID pileOwner = claim != null ? claim.pileOwner(socket.id()) : null;
            boolean pileEmpty = pileItems == null || pileItems.isEmpty();
            if (!canPlace(socket.sharePlace(), stashOwner, pileOwner, pileEmpty, player)) {
                denial = mostSpecific(denial, PlacementDenial.NOT_SHARED);
                continue;
            }
            int pileTotal = claim != null ? claim.totalQuantity(socket.id()) : 0;
            int quantity = placeableQuantity(pileTotal, blockTotal, heldCount,
                    socket.maxQuantity(), custodyMaxQuantity, socket.placePerPress());
            if (quantity <= 0) {
                denial = mostSpecific(denial, PlacementDenial.FULL);
                continue;
            }
            return new PlacementRoute(socket, quantity, null);
        }
        return new PlacementRoute(null, 0, denial);
    }

    /** The more specific of two denial reasons (enum order IS the precedence order). */
    @Nullable
    private static PlacementDenial mostSpecific(@Nullable PlacementDenial current, @Nonnull PlacementDenial seen) {
        if (current == null) {
            return seen;
        }
        return seen.ordinal() < current.ordinal() ? seen : current;
    }

    // ==================== block sockets (the world block IS the state) ====================

    /**
     * PURE: the world position a Block socket's {@code At} offset addresses from the station block
     * at {@code (x, y, z)}, composed with the station block's own facing exactly as
     * {@code Custody.Display} offsets are ({@link StationBlockFacing#rotateOffset}: authored
     * {@code +Z} = the block's front, {@code Y} vertical). Block yaw is a discrete quarter turn,
     * so the rotated components round back onto exact cells.
     */
    @Nonnull
    static int[] blockSocketTarget(int x, int y, int z, int atX, int atY, int atZ, double blockYawRadians) {
        double[] rotated = StationBlockFacing.rotateOffset(atX, atY, atZ, blockYawRadians);
        return new int[] {
                x + (int) Math.round(rotated[0]),
                y + (int) Math.round(rotated[1]),
                z + (int) Math.round(rotated[2])};
    }

    /**
     * PURE: does the block standing at a socket's target cell satisfy the socket's {@code Match}?
     * {@code baseItemId} is the world block's BASE item id (a state variant already normalized to
     * the block that authored the family); null, blank, or the engine's empty key means no block
     * stands there and nothing matches - a catch-all {@code Match} still needs a real block. The
     * identity resolvers are injected ({@code resourceTypesOf}/{@code tagsOf} answer for the base
     * id) so this stays testable without a live asset map; the match itself is the SAME any-route
     * {@link #matchesInput} every other {@code ActionInput} site uses (the Function route reads
     * null - a block has no held-item function).
     */
    static boolean blockSocketMatches(@Nullable String baseItemId, @Nullable ActionInput match,
            @Nonnull Function<String, String[]> resourceTypesOf,
            @Nonnull Function<String, Map<String, String[]>> tagsOf) {
        if (baseItemId == null || baseItemId.isBlank() || "Empty".equalsIgnoreCase(baseItemId)) {
            return false;
        }
        if (match == null || match.isCatchAll()) {
            return true;
        }
        return matchesInput(match, baseItemId, resourceTypesOf.apply(baseItemId), tagsOf.apply(baseItemId), null);
    }

    // ==================== socket addressing defaults ====================

    /**
     * PURE: the socket id a custody-routed Consume/Produce entry resolves to - the entry's own
     * {@code Socket} when authored, else the phase's group-level one, else the FIRST authored Item
     * socket ({@value StationCustodyClaim#MAIN_PILE} for a degenerate custody). Lowercased, the
     * one socket-id case rule.
     */
    @Nonnull
    static String socketIdFor(@Nullable String entrySocket, @Nullable String groupSocket,
            @Nonnull List<Custody.ResolvedSocket> sockets) {
        String authored = entrySocket != null && !entrySocket.isBlank() ? entrySocket : groupSocket;
        if (authored != null && !authored.isBlank()) {
            return authored.toLowerCase(Locale.ROOT);
        }
        return firstItemSocketId(sockets);
    }

    /** PURE: the first authored Item socket's id, else {@value StationCustodyClaim#MAIN_PILE}. */
    @Nonnull
    static String firstItemSocketId(@Nonnull List<Custody.ResolvedSocket> sockets) {
        for (Custody.ResolvedSocket socket : sockets) {
            if (socket.itemRoute()) {
                return socket.id();
            }
        }
        return StationCustodyClaim.MAIN_PILE;
    }
}
