package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.recipe.NativeRecipe;
import com.ziggfreed.common.recipe.RecipeBench;
import com.ziggfreed.common.recipe.RecipeCatalog;
import com.ziggfreed.common.recipe.RecipeIndex;
import com.ziggfreed.common.recipe.RecipeMaterial;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Derives station Convert conversions from the engine's OWN native recipes
 * ({@link StationAsset.FromCrafting}), so a station that follows a whole native bench or category
 * needs ZERO hardcoded per-item conversions: the Sawmill follows the plank categories, a
 * salvage-shaped station follows the {@code Salvagebench}, and a third party whose content ships a
 * native recipe at that bench is covered the moment its pack loads.
 *
 * <p><b>Two layers, one seam:</b> the PURE core ({@link #resolve} / {@link #deriveFromCrafting})
 * takes an injected {@link CraftingCandidate} collection so it is unit-testable without a live
 * server; the thin live adapter ({@link #liveCandidates}) reads the shared recipe index
 * ({@link RecipeIndex#live()}), which covers STANDALONE recipe files (vanilla's {@code Salvage_*}
 * set) as well as the recipes authored inside items, at full native quantities on both sides.
 * {@link #candidatesOf} is the pure half of that adapter, so a test hands it a hand-built
 * {@link RecipeCatalog} and walks the identical code.
 *
 * <p><b>A derived row carries the recipe's FULL output list at native quantities.</b> An iron
 * sword's salvage recipe gives back two ore, a hide and a scrap, and the row says exactly that.
 * Retuning what a station yields is {@code Recipe.Yield}'s job ({@link StationYield}), never this
 * deriver's.
 */
public final class StationRecipeDeriver {

    private StationRecipeDeriver() {
    }

    /**
     * A normalized read of one native recipe for the pure derivation core: the recipe's own id (the
     * deterministic tie-break when two recipes make the same item), its primary output item id, every
     * native bench-requirement category on it (flattened), the native bench requirement ids + kinds
     * (for the {@code Benches}/{@code Types} routes), the recipe's native {@code TimeSeconds} (for the
     * {@code NativeTime} pacing transform), its inputs and its FULL outputs.
     */
    public static final class CraftingCandidate {
        @Nonnull final String recipeId;
        @Nonnull final String itemId;
        @Nonnull final List<String> categories;
        @Nonnull final List<String> benchIds;
        @Nonnull final List<String> types;
        final float timeSeconds;
        @Nonnull final List<Ingredient> inputs;
        @Nonnull final List<Ingredient> outputs;

        /**
         * The full shape: every read the derivation routes need, the recipe's whole output list
         * included.
         */
        public CraftingCandidate(@Nonnull String recipeId, @Nonnull String itemId, @Nonnull List<String> categories,
                @Nonnull List<String> benchIds, @Nonnull List<String> types, float timeSeconds,
                @Nonnull List<Ingredient> inputs, @Nonnull List<Ingredient> outputs) {
            this.recipeId = recipeId;
            this.itemId = itemId;
            this.categories = categories;
            this.benchIds = benchIds;
            this.types = types;
            this.timeSeconds = timeSeconds;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        /**
         * The one-output shape: a recipe making one of {@code itemId}, identified by that item (the
         * shape of every recipe authored inside an item, whose id the engine derives from the item's).
         */
        public CraftingCandidate(@Nonnull String itemId, @Nonnull List<String> categories,
                @Nonnull List<String> benchIds, @Nonnull List<String> types, float timeSeconds,
                @Nonnull List<Ingredient> inputs) {
            this(itemId, itemId, categories, benchIds, types, timeSeconds, inputs,
                    List.of(Ingredient.item(itemId, 1)));
        }

        /** Categories-only constructor: benchIds/types empty, timeSeconds 0, one output of one. */
        public CraftingCandidate(@Nonnull String itemId, @Nonnull List<String> categories,
                @Nonnull List<Ingredient> inputs) {
            this(itemId, categories, List.of(), List.of(), 0f, inputs);
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
                String ref = inputRef(c.primaryInput());
                if (ref != null) {
                    authoredInputRefs.add(ref);
                }
            }
        }
        StationAsset.FromCrafting spec = recipe != null ? recipe.getFromCrafting() : null;
        if (spec != null) {
            for (StationAsset.Conversion derived : deriveFromCrafting(spec, candidates)) {
                String ref = inputRef(derived.primaryInput());
                if (ref != null && authoredInputRefs.contains(ref)) {
                    continue; // an authored conversion with the same input ref wins
                }
                out.add(derived);
            }
        }
        return out.toArray(new StationAsset.Conversion[0]);
    }

    /**
     * Derive one Conversion per candidate that MATCHES the spec (category intersect OR bench-id
     * match, then filtered by the declared recipe kinds) and whose recipe carries at least one
     * usable input and one output. Deterministic order: sorted by primary output item id, then by
     * recipe id, so two recipes making the same item keep one order on every boot. Pure.
     *
     * <p><b>Multi-input:</b> a native recipe's WHOLE {@code Input} array derives into the
     * conversion's own {@code Ingredient[]} input, so a multi-material native recipe is a real
     * derived conversion rather than a skipped candidate. A candidate is skipped only when it has NO
     * inputs at all, or when one of them names neither an {@code ItemId}, a resolvable tag nor a
     * {@code ResourceTypeId}.
     *
     * <p><b>Multi-output:</b> the recipe's WHOLE output list derives at native quantities (a
     * salvage recipe's ore, hide and scrap are three output entries of one row); a candidate with
     * no output at all makes nothing and is skipped.
     *
     * <p><b>Match rule:</b> a candidate matches when its {@code categories} intersect the spec's
     * {@code Categories} (case-insensitive) OR its {@code benchIds} include one of the spec's
     * {@code Benches} (case-insensitive) - the two routes are additive, so a station may scope by
     * native category, by native bench id, or both. The match is then filtered by {@code Types}:
     * absent/empty derives BOTH kinds, else only a candidate whose recipe kind is in the declared
     * set survives.
     *
     * <p><b>Native-time pacing:</b> when the spec authors a {@code NativeTime} group, each derived
     * conversion carries a baked {@code DurationMs} = {@code Scale * TimeSeconds*1000 + OffsetMs}
     * (the linear transform over the recipe's own native time); with no {@code NativeTime} group
     * the derived conversion carries a {@code null} {@code DurationMs} and the engine falls to
     * {@code Work.CycleMs} - so a station that authors {@code Categories} alone (the shipped
     * Sawmill) derives byte-identically to before.
     */
    @Nonnull
    public static List<StationAsset.Conversion> deriveFromCrafting(@Nonnull StationAsset.FromCrafting spec,
            @Nonnull Collection<CraftingCandidate> candidates) {
        String[] wantCategories = spec.getCategories();
        String[] wantBenches = spec.getBenches();
        boolean hasCategories = wantCategories != null && wantCategories.length > 0;
        boolean hasBenches = wantBenches != null && wantBenches.length > 0;
        if (!hasCategories && !hasBenches) {
            Log.warn("STATION FromCrafting has neither Categories nor Benches; deriving zero conversions");
            return List.of();
        }
        String[] wantTypes = spec.getTypes();
        StationAsset.FromCrafting.NativeTime nativeTime = spec.getNativeTime();
        List<Derived> derived = new ArrayList<>();
        for (CraftingCandidate cand : candidates) {
            if (cand == null || cand.itemId == null || cand.itemId.isBlank()) {
                continue;
            }
            boolean catMatch = hasCategories && categoriesIntersect(cand.categories, wantCategories);
            boolean benchMatch = hasBenches && stringsIntersect(cand.benchIds, wantBenches);
            if (!catMatch && !benchMatch) {
                continue;
            }
            if (!typesMatch(cand.types, wantTypes)) {
                continue;
            }
            if (cand.inputs == null || cand.inputs.isEmpty()) {
                Log.fine("STATION FromCrafting skips '" + cand.recipeId + "': native recipe has no inputs");
                continue;
            }
            List<Ingredient> inputs = new ArrayList<>(cand.inputs.size());
            boolean everyInputUsable = true;
            for (Ingredient nativeInput : cand.inputs) {
                // A candidate input must carry ONE real route (ItemId, ResourceTypeId, or the Tags
                // route the live adapter resolves from a native ItemTag). A route-less input here is
                // an unusable native reference, never a derived match-any row.
                if (nativeInput == null || nativeInput.routeCount() == 0) {
                    Log.fine("STATION FromCrafting skips '" + cand.recipeId
                            + "': a native input has no usable ItemId / ItemTag / ResourceTypeId route");
                    everyInputUsable = false;
                    break;
                }
                int inQty = nativeInput.getQuantity() != null && nativeInput.getQuantity() > 0
                        ? nativeInput.getQuantity() : 1;
                if (nativeInput.hasResourceRoute()) {
                    inputs.add(Ingredient.resource(nativeInput.getResourceTypeId(), inQty));
                } else if (nativeInput.hasItemRoute()) {
                    inputs.add(Ingredient.item(nativeInput.getItemId(), inQty));
                } else {
                    inputs.add(Ingredient.tagged(nativeInput.getTags(), inQty));
                }
            }
            if (!everyInputUsable) {
                continue;
            }
            Ingredient[] output = derivedOutputs(cand);
            if (output == null) {
                Log.fine("STATION FromCrafting skips '" + cand.recipeId + "': native recipe has no output");
                continue;
            }
            Long durationMs = nativeDurationMs(nativeTime, cand.timeSeconds);
            // The derived row is stamped with its native source category so a multi-output station
            // can group the picker by it.
            String category = deriveSourceCategory(cand, wantCategories, wantBenches, catMatch);
            // Every derived row runs at Conversion.DERIVED_TIER (1), so a hand-authored row at the
            // reader-default tier 0 outranks derivation with no authoring.
            derived.add(new Derived(cand.recipeId, StationAsset.Conversion.derivedRow(
                    inputs.toArray(new Ingredient[0]), output, durationMs, category)));
        }
        derived.sort(Comparator.comparing((Derived d) -> d.row.getOutput()[0].getItemId(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(d -> d.recipeId, String.CASE_INSENSITIVE_ORDER));
        if (derived.isEmpty()) {
            Log.warn("STATION FromCrafting matched no native recipes for Categories "
                    + Arrays.toString(wantCategories) + " / Benches " + Arrays.toString(wantBenches)
                    + "; deriving zero conversions");
        }
        List<StationAsset.Conversion> rows = new ArrayList<>(derived.size());
        for (Derived d : derived) {
            rows.add(d.row);
        }
        return rows;
    }

    /** One derived row beside the recipe id that breaks a same-output sort tie. */
    private record Derived(@Nonnull String recipeId, @Nonnull StationAsset.Conversion row) {
    }

    /**
     * The row's outputs: the candidate's FULL native output list, every entry at its authored
     * quantity (reader-defaulted to 1). {@code null} when the recipe makes nothing.
     */
    @Nullable
    private static Ingredient[] derivedOutputs(@Nonnull CraftingCandidate cand) {
        if (cand.outputs == null || cand.outputs.isEmpty()) {
            return null;
        }
        List<Ingredient> out = new ArrayList<>(cand.outputs.size());
        for (Ingredient nativeOutput : cand.outputs) {
            if (nativeOutput == null || !nativeOutput.hasItemRoute()) {
                continue;
            }
            out.add(Ingredient.item(nativeOutput.getItemId(), nativeOutput.effectiveQuantity()));
        }
        return out.isEmpty() ? null : out.toArray(new Ingredient[0]);
    }

    /**
     * PURE: the baked per-conversion {@code DurationMs} for the {@code NativeTime} linear transform
     * ({@code y = Scale * (TimeSeconds in ms) + OffsetMs}), or {@code null} when no
     * {@code NativeTime} group is authored (the derived conversion then falls to {@code Work.CycleMs}).
     * Reader-defaulted {@code Scale}/{@code OffsetMs} so even an empty {@code NativeTime: {}} stretches
     * native time rather than leaving it instant.
     */
    @Nullable
    static Long nativeDurationMs(@Nullable StationAsset.FromCrafting.NativeTime nativeTime, float timeSeconds) {
        if (nativeTime == null) {
            return null;
        }
        double ms = nativeTime.effectiveScale() * Math.max(0f, timeSeconds) * 1000.0 + nativeTime.effectiveOffsetMs();
        return Math.round(ms);
    }

    // ==================== Live adapter (the shared recipe index) ====================

    /**
     * Every candidate the shared recipe index holds right now ({@link RecipeIndex#live()}): one per
     * native recipe, standalone files and item-authored recipes alike. Never throws; an index that
     * cannot be read yet (a unit JVM) answers an empty list.
     */
    @Nonnull
    public static List<CraftingCandidate> liveCandidates() {
        try {
            return candidatesOf(RecipeIndex.live().catalog());
        } catch (Throwable t) {
            Log.warn("STATION could not read the recipe index for FromCrafting: " + t.getMessage());
            return List.of();
        }
    }

    /**
     * PURE: one {@link CraftingCandidate} per recipe of {@code catalog} that can be scoped to at all
     * (a native category or a bench id), in the catalog's own order. A recipe whose input line keeps
     * no route (a native tag no loaded item carries, so nothing could satisfy it natively either) is
     * skipped with ONE warn naming the recipe; a recipe with no input, or no output, is skipped
     * quietly, since the derivation core would skip it anyway.
     */
    @Nonnull
    public static List<CraftingCandidate> candidatesOf(@Nonnull RecipeCatalog catalog) {
        List<CraftingCandidate> out = new ArrayList<>();
        for (NativeRecipe recipe : catalog.all()) {
            if (recipe == null || recipe.id().isEmpty()) {
                continue;
            }
            List<String> categories = new ArrayList<>();
            List<String> benchIds = new ArrayList<>();
            List<String> types = new ArrayList<>();
            for (RecipeBench bench : recipe.benches()) {
                if (!bench.id().isEmpty()) {
                    benchIds.add(bench.id());
                }
                if (bench.type() != null) {
                    types.add(bench.type());
                }
                categories.addAll(bench.categories());
            }
            // A candidate is derivable when the station can scope to it by native category OR by
            // native bench id; skip only when it offers neither route.
            if (categories.isEmpty() && benchIds.isEmpty()) {
                continue;
            }
            List<Ingredient> inputs = new ArrayList<>(recipe.inputs().size());
            boolean everyInputResolvable = true;
            for (RecipeMaterial line : recipe.inputs()) {
                if (!line.hasRoute()) {
                    Log.warn("STATION FromCrafting skips recipe '" + recipe.id()
                            + "': a native input names a tag no loaded item carries, so nothing can satisfy it");
                    everyInputResolvable = false;
                    break;
                }
                inputs.add(ingredientOf(line));
            }
            if (!everyInputResolvable) {
                continue;
            }
            List<Ingredient> outputs = new ArrayList<>(recipe.outputs().size());
            for (RecipeMaterial line : recipe.outputs()) {
                if (line.itemId() != null) {
                    outputs.add(Ingredient.item(line.itemId(), line.quantity()));
                }
            }
            String primaryId = recipe.primaryOutput() != null && recipe.primaryOutput().itemId() != null
                    ? recipe.primaryOutput().itemId()
                    : outputs.isEmpty() ? null : outputs.get(0).getItemId();
            if (primaryId == null) {
                continue;
            }
            out.add(new CraftingCandidate(recipe.id(), primaryId, categories, benchIds, types,
                    recipe.timeSeconds(), inputs, outputs));
        }
        return out;
    }

    /** One native recipe line as the {@link Ingredient} the derivation core reads, in the engine's own route order. */
    @Nonnull
    private static Ingredient ingredientOf(@Nonnull RecipeMaterial line) {
        if (line.itemId() != null) {
            return Ingredient.item(line.itemId(), line.quantity());
        }
        if (line.tag() != null) {
            return Ingredient.tagged(Map.of(line.tag(), new String[0]), line.quantity());
        }
        return Ingredient.resource(line.resourceTypeId(), line.quantity());
    }

    // ==================== Helpers ====================

    /** The canonical (lowercased) input ref of an ingredient: ResourceTypeId, else ItemId, else null. */
    @Nullable
    private static String inputRef(@Nullable Ingredient ingredient) {
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
        return stringsIntersect(have, wanted);
    }

    /**
     * PURE: the source-category tag stamped onto a derived conversion. Precedence: (1) a
     * category-route match stamps the MATCHED native category (the first of the candidate's own
     * categories that intersects the wanted set - the meaningful grouping key the picker shows);
     * (2) failing that, if the candidate carries ANY native category, its first one (the recipe's
     * own source category, even on a bench-route match); (3) only when no category exists does a
     * bench-route match stamp the matched BENCH id. {@code null} only when a candidate matched with
     * neither a category nor a resolvable bench id (unreachable given the caller already matched,
     * but null-safe). Deterministic + testable without a live item map.
     */
    @Nullable
    static String deriveSourceCategory(@Nonnull CraftingCandidate cand, @Nullable String[] wantCategories,
            @Nullable String[] wantBenches, boolean catMatch) {
        if (catMatch) {
            String matched = firstIntersecting(cand.categories, wantCategories);
            if (matched != null) {
                return matched;
            }
        }
        if (!cand.categories.isEmpty()) {
            return cand.categories.get(0);
        }
        return firstIntersecting(cand.benchIds, wantBenches);
    }

    /** The FIRST value in {@code have} equal (case-insensitive) to any value in {@code wanted}; null if none. */
    @Nullable
    private static String firstIntersecting(@Nonnull List<String> have, @Nullable String[] wanted) {
        if (wanted == null) {
            return null;
        }
        for (String h : have) {
            if (h == null || h.isBlank()) {
                continue;
            }
            for (String w : wanted) {
                if (w != null && w.equalsIgnoreCase(h)) {
                    return h;
                }
            }
        }
        return null;
    }

    /** True when any value in {@code have} equals (case-insensitive) any value in {@code wanted}. */
    private static boolean stringsIntersect(@Nonnull List<String> have, @Nullable String[] wanted) {
        if (wanted == null) {
            return false;
        }
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

    /**
     * True when a candidate's recipe {@code kinds} are allowed by the spec's {@code Types} filter:
     * a null/empty {@code wantTypes} allows BOTH kinds (no filter); otherwise the candidate must
     * carry at least one recipe kind named in the set (case-insensitive). A candidate with no
     * declared kinds (the categories-only shape) always passes so the shipped Sawmill derives
     * unchanged.
     */
    static boolean typesMatch(@Nonnull List<String> kinds, @Nullable String[] wantTypes) {
        if (wantTypes == null || wantTypes.length == 0) {
            return true;
        }
        if (kinds.isEmpty()) {
            return true;
        }
        return stringsIntersect(kinds, wantTypes);
    }
}
