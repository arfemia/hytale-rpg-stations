package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.recipe.NativeRecipe;
import com.ziggfreed.common.recipe.RecipeMaterial;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE cores of the two FALLBACK ROUTES ({@code Recipe.Fallback}): what a placed piece no
 * authored or derived row covers comes apart into. Built PER PIECE at selection and placement
 * time, never cached in the catalog, because the answer depends on the very stack in the socket.
 *
 * <ul>
 *   <li><b>Crafting share</b> ({@link #craftingShareRow}): the piece's OWN crafting recipe, each
 *       exact-item input line at {@code floor(Quantity x Share / OutputQuantity)} where
 *       {@code OutputQuantity} is how many pieces that recipe makes at once (the stated rounding
 *       rule, {@link #shareOf}), lines that round to nothing dropped, a recipe with no line left
 *       meaning the route does not apply. A family or tag line names no item to give back and is
 *       dropped.</li>
 *   <li><b>Essence only</b> ({@link #essenceOnlyRow}): the piece is consumed and nothing is
 *       produced; the action's {@code Bonus} rolls are its whole payout.</li>
 * </ul>
 *
 * <p><b>ONE route per piece</b> ({@link #routeFor}): the routes are tried in that order and the
 * FIRST that applies is the only row the piece is offered, and a pile is offered ONE row, its
 * oldest falling-back piece's ({@link #offeredRows}, the very array selection hands the runnable
 * scan). The runnable check that follows then answers for that row alone, so a crafting-share row
 * whose outputs do not fit the worker's inventory answers NO_ROOM, and the essence-only row (whose
 * empty output always fits) never rescues it: a full inventory can never turn a share-eligible
 * piece into an essence-only consumption.
 *
 * <p>Both are scoped by the gear filter ({@link #inFilter}: the shared matcher under its ONE
 * {@link StationCustody#accepts} rule, {@code Except} hole included) and by the metadata guard,
 * which the live caller applies to the real stack ({@link StationMetadataGuard#accepts}).
 */
public final class StationFallbackRoutes {

    private StationFallbackRoutes() {
    }

    /**
     * Does the fallback's gear filter accept this material? An absent filter accepts everything;
     * an authored one answers under the shared matcher's one rule, so a catch-all filter with an
     * {@code Except} accepts everything but the hole. No {@code Fallback} group at all accepts
     * nothing, since there is no route to fall back to.
     */
    static boolean inFilter(@Nullable StationAsset.Fallback fallback, @Nullable String itemId,
            @Nullable String[] resourceTypeIds, @Nullable Map<String, String[]> tags, @Nullable String function) {
        return fallback != null
                && StationCustody.accepts(fallback.getInput(), itemId, resourceTypeIds, tags, function);
    }

    /**
     * The crafting-share row for {@code itemId}, or null when the route does not apply: no share
     * authored, no own recipe, or every line rounds to nothing.
     */
    @Nullable
    static StationAsset.Conversion craftingShareRow(@Nullable StationAsset.Fallback fallback,
            @Nonnull String itemId, @Nullable NativeRecipe ownRecipe) {
        if (fallback == null || !fallback.hasCraftingShare() || ownRecipe == null) {
            return null;
        }
        Ingredient[] outputs = shareOf(ownRecipe.inputs(), fallback.getCraftingShare().effectiveShare(),
                outputQuantityOf(ownRecipe));
        if (outputs == null) {
            return null;
        }
        return StationAsset.Conversion.craftingShareRow(Ingredient.item(itemId, 1), outputs);
    }

    /**
     * PURE: the stated rounding rule. The recipe's inputs make {@code outputQuantity} pieces at
     * once, and ONE piece is being unmade, so each exact-item line becomes
     * {@code floor(quantity x share / outputQuantity)}: a batch recipe (one bar making four darts)
     * pays a quarter of the bar's share per dart, never the whole batch's. A line that rounds to
     * nothing is dropped, and so is a family or tag line; null when nothing survives. An
     * {@code outputQuantity} below 1 reads as 1 (a recipe always makes at least the piece).
     */
    @Nullable
    static Ingredient[] shareOf(@Nonnull List<RecipeMaterial> inputs, double share, int outputQuantity) {
        if (share <= 0.0 || !Double.isFinite(share)) {
            return null;
        }
        int perPiece = Math.max(1, outputQuantity);
        List<Ingredient> out = new ArrayList<>(inputs.size());
        for (RecipeMaterial line : inputs) {
            if (line == null || line.itemId() == null) {
                continue;
            }
            int quantity = (int) Math.floor(line.quantity() * share / perPiece);
            if (quantity <= 0) {
                continue;
            }
            out.add(Ingredient.item(line.itemId(), quantity));
        }
        return out.isEmpty() ? null : out.toArray(new Ingredient[0]);
    }

    /**
     * PURE: how many pieces {@code recipe} makes per craft: its primary output's quantity, else its
     * first output's, else 1. What {@link #shareOf} divides each input line by.
     */
    static int outputQuantityOf(@Nonnull NativeRecipe recipe) {
        RecipeMaterial primary = recipe.primaryOutput();
        if (primary == null && !recipe.outputs().isEmpty()) {
            primary = recipe.outputs().get(0);
        }
        return primary != null && primary.quantity() > 0 ? primary.quantity() : 1;
    }

    /** The essence-only row for {@code itemId}, or null when the route is off. */
    @Nullable
    static StationAsset.Conversion essenceOnlyRow(@Nullable StationAsset.Fallback fallback, @Nonnull String itemId) {
        if (fallback == null || !fallback.hasEssenceOnly()) {
            return null;
        }
        return StationAsset.Conversion.essenceOnlyRow(Ingredient.item(itemId, 1));
    }

    /**
     * PURE: the ONE row the fallback offers for {@code itemId}, the first route that applies in
     * route order: the crafting share (tier 2) when the piece's own recipe leaves it a line, else
     * the essence-only row (tier 3) when that route is on. Null when neither applies. A piece is
     * offered ONE row, never a list to scan through, so the runnable check that follows answers
     * for that row alone: a share row with no inventory room answers NO_ROOM, and the essence-only
     * row never stands behind it to be reached instead (see the class javadoc). The filter and the
     * metadata guard are the caller's to check first.
     */
    @Nullable
    static StationAsset.Conversion routeFor(@Nullable StationAsset.Fallback fallback, @Nonnull String itemId,
            @Nullable NativeRecipe ownRecipe) {
        StationAsset.Conversion share = craftingShareRow(fallback, itemId, ownRecipe);
        return share != null ? share : essenceOnlyRow(fallback, itemId);
    }

    /**
     * PURE: the rows the fallback OFFERS a socket pile, the exact array the runnable scan is handed
     * ({@code StationService#selectConversion}): at most ONE row, {@link #routeFor} over the OLDEST
     * counted piece in {@code pile} that passes {@code inFilter} and has a route at all, and an
     * empty array when no piece there falls back. Never a list of candidate rows: a scan over two
     * could reach the essence-only row after a crafting-share row found no room, which is the
     * NO_ROOM decision this array's length carries. {@code inFilter} is the gear filter
     * ({@link #inFilter} with the live family, tag and function reads) and {@code ownRecipeOf}
     * the piece's own crafting recipe, both supplied by the live caller.
     */
    @Nonnull
    static StationAsset.Conversion[] offeredRows(@Nullable StationAsset.Fallback fallback,
            @Nullable Map<String, Integer> pile, @Nonnull Predicate<String> inFilter,
            @Nonnull Function<String, NativeRecipe> ownRecipeOf) {
        if (fallback == null || pile == null) {
            return new StationAsset.Conversion[0];
        }
        for (Map.Entry<String, Integer> entry : pile.entrySet()) {
            String itemId = entry.getKey();
            Integer count = entry.getValue();
            if (itemId == null || itemId.isBlank() || count == null || count <= 0 || !inFilter.test(itemId)) {
                continue;
            }
            StationAsset.Conversion route = routeFor(fallback, itemId, ownRecipeOf.apply(itemId));
            if (route != null) {
                return new StationAsset.Conversion[] {route};
            }
        }
        return new StationAsset.Conversion[0];
    }

    /**
     * Does ANY fallback route apply to {@code itemId} at all: the crafting share with a recipe that
     * leaves at least one line, or the essence-only route being on? What placement acceptance asks
     * for a piece no row covers (the filter and the guard having passed).
     */
    static boolean anyRouteApplies(@Nullable StationAsset.Fallback fallback, @Nonnull String itemId,
            @Nullable NativeRecipe ownRecipe) {
        return routeFor(fallback, itemId, ownRecipe) != null;
    }
}
