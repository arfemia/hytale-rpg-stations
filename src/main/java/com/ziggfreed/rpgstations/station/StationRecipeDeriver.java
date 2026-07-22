package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Derives station Convert conversions from the LIVE native crafting recipes
 * ({@link StationAsset.FromCrafting}), so a station that refines a whole native category
 * needs ZERO hardcoded per-item conversions. Ported verbatim from the MMO's
 * {@code station.StationRecipeDeriver} (RPG Stations extraction leg 2), {@code SafeLog}
 * severed to RpgStations' own {@code util.Log}.
 *
 * <p><b>Two layers, one seam:</b> the PURE core ({@link #resolve} / {@link #deriveFromCrafting})
 * takes an injected {@link CraftingCandidate} collection so it is unit-testable without the
 * live {@code Item} asset map; the thin live adapter ({@link #liveCandidates}) walks
 * {@code Item.getAssetMap()} once.
 *
 * <p><b>OutputQuantity caveat:</b> the native {@code CraftingRecipe.primaryOutputQuantity} is
 * inaccessible to a plugin and is verified to be 1 for the wood-plank families, so
 * {@code OutputPerInput} IS the derived output quantity.
 */
public final class StationRecipeDeriver {

    private StationRecipeDeriver() {
    }

    /**
     * A normalized read of one craftable item for the pure derivation core: the item's own id,
     * every native bench-requirement category on its recipe (flattened), and its recipe inputs.
     */
    public static final class CraftingCandidate {
        @Nonnull final String itemId;
        @Nonnull final List<String> categories;
        @Nonnull final List<StationAsset.Ingredient> inputs;

        public CraftingCandidate(@Nonnull String itemId, @Nonnull List<String> categories,
                @Nonnull List<StationAsset.Ingredient> inputs) {
            this.itemId = itemId;
            this.categories = categories;
            this.inputs = inputs;
        }
    }

    // ==================== Pure core (unit-testable) ====================

    /**
     * The station's EFFECTIVE conversions: authored {@code Conversions} FIRST (an authored
     * entry whose input ref matches a derived one OVERRIDES the derived entry), then the
     * {@code FromCrafting}-derived conversions in deterministic order. Pure.
     */
    @Nonnull
    public static StationAsset.Conversion[] resolve(@Nullable StationAsset.Recipe recipe,
            @Nonnull Collection<CraftingCandidate> candidates) {
        List<StationAsset.Conversion> out = new ArrayList<>();
        Set<String> authoredInputRefs = new HashSet<>();
        if (recipe != null && recipe.getConversions() != null) {
            for (StationAsset.Conversion c : recipe.getConversions()) {
                if (c == null) {
                    continue;
                }
                out.add(c);
                String ref = inputRef(c.getInput());
                if (ref != null) {
                    authoredInputRefs.add(ref);
                }
            }
        }
        StationAsset.FromCrafting spec = recipe != null ? recipe.getFromCrafting() : null;
        if (spec != null) {
            for (StationAsset.Conversion derived : deriveFromCrafting(spec, candidates)) {
                String ref = inputRef(derived.getInput());
                if (ref != null && authoredInputRefs.contains(ref)) {
                    continue; // an authored conversion with the same input ref wins
                }
                out.add(derived);
            }
        }
        return out.toArray(new StationAsset.Conversion[0]);
    }

    /**
     * Derive one Conversion per candidate whose {@code categories} intersect the spec's
     * {@code Categories} (case-insensitive) and whose recipe has EXACTLY ONE input.
     * Deterministic order (sorted by output item id). Pure.
     */
    @Nonnull
    public static List<StationAsset.Conversion> deriveFromCrafting(@Nonnull StationAsset.FromCrafting spec,
            @Nonnull Collection<CraftingCandidate> candidates) {
        String[] wantCategories = spec.getCategories();
        if (wantCategories == null || wantCategories.length == 0) {
            Log.warn("STATION FromCrafting has no Categories; deriving zero conversions");
            return List.of();
        }
        int outputPerInput = spec.getOutputPerInput() != null && spec.getOutputPerInput() > 0
                ? spec.getOutputPerInput() : 1;
        List<StationAsset.Conversion> derived = new ArrayList<>();
        for (CraftingCandidate cand : candidates) {
            if (cand == null || cand.itemId == null || cand.itemId.isBlank()) {
                continue;
            }
            if (!categoriesIntersect(cand.categories, wantCategories)) {
                continue;
            }
            if (cand.inputs == null || cand.inputs.size() != 1) {
                Log.fine("STATION FromCrafting skips '" + cand.itemId + "': native recipe has "
                        + (cand.inputs == null ? 0 : cand.inputs.size()) + " inputs (need exactly 1)");
                continue;
            }
            StationAsset.Ingredient nativeInput = cand.inputs.get(0);
            String ref = inputRef(nativeInput);
            if (nativeInput == null || ref == null) {
                Log.fine("STATION FromCrafting skips '" + cand.itemId
                        + "': native input has neither ItemId nor ResourceTypeId");
                continue;
            }
            int inQty = nativeInput.getQuantity() != null && nativeInput.getQuantity() > 0
                    ? nativeInput.getQuantity() : 1;
            boolean isResource = nativeInput.getResourceTypeId() != null
                    && !nativeInput.getResourceTypeId().isBlank();
            StationAsset.Ingredient input = isResource
                    ? StationAsset.Ingredient.resource(nativeInput.getResourceTypeId(), inQty)
                    : StationAsset.Ingredient.item(nativeInput.getItemId(), inQty);
            StationAsset.Ingredient output = StationAsset.Ingredient.item(cand.itemId, outputPerInput);
            derived.add(StationAsset.Conversion.of(input, output));
        }
        derived.sort(Comparator.comparing(c -> c.getOutput().getItemId(), String.CASE_INSENSITIVE_ORDER));
        if (derived.isEmpty()) {
            Log.warn("STATION FromCrafting matched no craftable items for Categories "
                    + Arrays.toString(wantCategories) + "; deriving zero conversions");
        }
        return derived;
    }

    // ==================== Live adapter (Item asset map) ====================

    /**
     * Extract a {@link CraftingCandidate} from every live {@code Item} that carries a native
     * crafting recipe. Never throws; returns an empty list if the asset map is unreadable
     * (e.g. a unit JVM).
     */
    @Nonnull
    public static List<CraftingCandidate> liveCandidates() {
        List<CraftingCandidate> out = new ArrayList<>();
        try {
            for (Item item : Item.getAssetMap().getAssetMap().values()) {
                if (item == null || item.getId() == null || !item.hasRecipesToGenerate()) {
                    continue;
                }
                List<CraftingRecipe> recipes = new ArrayList<>(1);
                item.collectRecipesToGenerate(recipes);
                for (CraftingRecipe recipe : recipes) {
                    if (recipe == null) {
                        continue;
                    }
                    List<String> categories = new ArrayList<>();
                    BenchRequirement[] benches = recipe.getBenchRequirement();
                    if (benches != null) {
                        for (BenchRequirement bench : benches) {
                            if (bench == null || bench.categories == null) {
                                continue;
                            }
                            for (String category : bench.categories) {
                                if (category != null && !category.isBlank()) {
                                    categories.add(category);
                                }
                            }
                        }
                    }
                    if (categories.isEmpty()) {
                        continue;
                    }
                    List<StationAsset.Ingredient> inputs = new ArrayList<>();
                    MaterialQuantity[] mqs = recipe.getInput();
                    if (mqs != null) {
                        for (MaterialQuantity mq : mqs) {
                            if (mq == null) {
                                continue;
                            }
                            inputs.add(StationAsset.Ingredient.of(mq.getItemId(), mq.getResourceTypeId(),
                                    mq.getQuantity()));
                        }
                    }
                    out.add(new CraftingCandidate(item.getId(), categories, inputs));
                }
            }
        } catch (Throwable t) {
            Log.warn("STATION could not enumerate the Item asset map for FromCrafting: " + t.getMessage());
        }
        return out;
    }

    // ==================== Helpers ====================

    /** The canonical (lowercased) input ref of an ingredient: ResourceTypeId, else ItemId, else null. */
    @Nullable
    private static String inputRef(@Nullable StationAsset.Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        String resource = ingredient.getResourceTypeId();
        if (resource != null && !resource.isBlank()) {
            return resource.toLowerCase(Locale.ROOT);
        }
        String item = ingredient.getItemId();
        if (item != null && !item.isBlank()) {
            return item.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** True when any of the item's categories equals (case-insensitive) any wanted category. */
    private static boolean categoriesIntersect(@Nonnull List<String> have, @Nonnull String[] wanted) {
        for (String w : wanted) {
            if (w == null || w.isBlank()) {
                continue;
            }
            for (String h : have) {
                if (h != null && w.equalsIgnoreCase(h)) {
                    return true;
                }
            }
        }
        return false;
    }
}
