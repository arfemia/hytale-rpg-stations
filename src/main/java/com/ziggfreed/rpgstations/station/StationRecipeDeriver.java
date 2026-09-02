package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Derives station Convert conversions from the LIVE native crafting recipes
 * ({@link StationAsset.FromCrafting}), so a station that refines a whole native category
 * needs ZERO hardcoded per-item conversions.
 *
 * <p><b>Two layers, one seam:</b> the PURE core ({@link #resolve} / {@link #deriveFromCrafting})
 * takes an injected {@link CraftingCandidate} collection so it is unit-testable without the
 * live {@code Item} asset map; the thin live adapter ({@link #liveCandidates}) walks
 * {@code Item.getAssetMap()} once.
 *
 * <p><b>OutputQuantity caveat:</b> the native {@code CraftingRecipe.primaryOutputQuantity} is a
 * protected field with no public getter and is absent from the recipe's network packet, so it cannot
 * be read at this seam. A derived conversion therefore carries a quantity of 1, which is the verified
 * native yield for every wood plank/decorative/ornate recipe (all 11 species). Retuning a station's
 * yield is {@code Recipe.Yield}'s job ({@link StationYield}), NOT this deriver's: a yield that keys
 * off the worker's held tool has to resolve per cycle, and the retired {@code FromCrafting
 * .OutputPerInput} leaf could only bake one number in at fold time.
 */
public final class StationRecipeDeriver {

    /**
     * The output quantity a derived conversion carries. The native per-recipe quantity is unreadable
     * at this seam (see the class javadoc), and 1 is its verified value for every recipe family the
     * shipped content derives; a station retunes yield through {@code Recipe.Yield} instead.
     */
    static final int NATIVE_OUTPUT_QUANTITY = 1;

    private StationRecipeDeriver() {
    }

    /**
     * A normalized read of one craftable item for the pure derivation core: the item's own id,
     * every native bench-requirement category on its recipe (flattened), the native bench
     * requirement ids + kinds (for the {@code Benches}/{@code Types} routes, seam wave decision
     * 51c), the recipe's native {@code TimeSeconds} (for the {@code NativeTime} pacing transform,
     * decision 52), and its recipe inputs.
     */
    public static final class CraftingCandidate {
        @Nonnull final String itemId;
        @Nonnull final List<String> categories;
        @Nonnull final List<String> benchIds;
        @Nonnull final List<String> types;
        final float timeSeconds;
        @Nonnull final List<Ingredient> inputs;

        /** Full constructor (seam wave): carries the bench-id/type/time reads the derived-recipe routes need. */
        public CraftingCandidate(@Nonnull String itemId, @Nonnull List<String> categories,
                @Nonnull List<String> benchIds, @Nonnull List<String> types, float timeSeconds,
                @Nonnull List<Ingredient> inputs) {
            this.itemId = itemId;
            this.categories = categories;
            this.benchIds = benchIds;
            this.types = types;
            this.timeSeconds = timeSeconds;
            this.inputs = inputs;
        }

        /** Categories-only constructor (pre-seam-wave shape) - benchIds/types empty, timeSeconds 0. */
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
     * usable input. Deterministic order (sorted by output item id). Pure.
     *
     * <p><b>Multi-input (decision 73):</b> a native recipe's WHOLE {@code Input} array derives into
     * the conversion's own {@code Ingredient[]} input, so a multi-material native recipe is a real
     * derived conversion rather than a skipped candidate. A candidate is skipped only when it has NO
     * inputs at all, or when one of them names neither an {@code ItemId} nor a {@code ResourceTypeId}.
     *
     * <p><b>Match rule (seam wave decision 51c):</b> a candidate matches when its
     * {@code categories} intersect the spec's {@code Categories} (case-insensitive) OR its
     * {@code benchIds} include one of the spec's {@code Benches} (case-insensitive) - the two
     * routes are additive, so a station may scope by native category, by native bench id, or both.
     * The match is then filtered by {@code Types}: absent/empty derives BOTH kinds, else only a
     * candidate whose recipe kind is in the declared set survives.
     *
     * <p><b>Native-time pacing (decision 52):</b> when the spec authors a {@code NativeTime} group,
     * each derived conversion carries a baked {@code DurationMs} = {@code Scale * TimeSeconds*1000 +
     * OffsetMs} (the linear transform over the recipe's own native time); with no {@code NativeTime}
     * group the derived conversion carries a {@code null} {@code DurationMs} and the engine falls to
     * {@code Work.CycleMs} - so a station that authors {@code Categories} alone (the shipped sawmill)
     * derives byte-identically to before.
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
        List<StationAsset.Conversion> derived = new ArrayList<>();
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
            // Decision 73: a native recipe's WHOLE Input array derives (the single-input restriction
            // is gone) - Conversion.Input is the same Ingredient[] shape, so "2 planks + 1 nail"
            // derives as one multi-input conversion instead of being skipped.
            if (cand.inputs == null || cand.inputs.isEmpty()) {
                Log.fine("STATION FromCrafting skips '" + cand.itemId + "': native recipe has no inputs");
                continue;
            }
            List<Ingredient> inputs = new ArrayList<>(cand.inputs.size());
            boolean everyInputUsable = true;
            for (Ingredient nativeInput : cand.inputs) {
                // A candidate input must carry ONE real route (ItemId, ResourceTypeId, or the Tags
                // route the live adapter resolves from a native ItemTag). A route-less input here is
                // an unusable native reference, never a derived match-any row.
                if (nativeInput == null || nativeInput.routeCount() == 0) {
                    Log.fine("STATION FromCrafting skips '" + cand.itemId
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
            Ingredient[] output = {Ingredient.item(cand.itemId, NATIVE_OUTPUT_QUANTITY)};
            Long durationMs = nativeDurationMs(nativeTime, cand.timeSeconds);
            // Selection wave (decision 56): stamp the derived conversion with its native source
            // category so a multi-output station can group the picker by it. The do-not-rework
            // guard on this deriver lifts for exactly this addition.
            String category = deriveSourceCategory(cand, wantCategories, wantBenches, catMatch);
            // Set-recipe wave: every derived row runs at Conversion.DERIVED_TIER (1), so a
            // hand-authored row at the reader-default tier 0 outranks derivation with no authoring.
            derived.add(StationAsset.Conversion.derivedRow(inputs.toArray(new Ingredient[0]), output,
                    durationMs, category));
        }
        derived.sort(Comparator.comparing(c -> c.getOutput()[0].getItemId(), String.CASE_INSENSITIVE_ORDER));
        if (derived.isEmpty()) {
            Log.warn("STATION FromCrafting matched no craftable items for Categories "
                    + Arrays.toString(wantCategories) + " / Benches " + Arrays.toString(wantBenches)
                    + "; deriving zero conversions");
        }
        return derived;
    }

    /**
     * PURE: the baked per-conversion {@code DurationMs} for the {@code NativeTime} linear transform
     * (decision 52, {@code y = Scale * (TimeSeconds in ms) + OffsetMs}), or {@code null} when no
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

    // ==================== Live adapter (Item asset map) ====================

    /**
     * Extract a {@link CraftingCandidate} from every live {@code Item} that carries a native
     * crafting recipe. Never throws; returns an empty list if the asset map is unreadable
     * (e.g. a unit JVM).
     *
     * <p><b>The native {@code ItemTag} input route:</b> a recipe input selecting by item tag keeps
     * only its registered tag INDEX at this seam ({@code MaterialQuantity} exposes no tag-name
     * getter), and the registry maps name to index one way only - so {@link #liveTagNamesByIndex}
     * rebuilds the reverse map from the item assets themselves: every raw tag KEY an item carries
     * (the engine expands families, values and {@code family=value} pairs all into keys) is
     * resolved through {@code AssetRegistry.getTagIndex} once per fold. A recipe tag that no item
     * carries resolves to no name; the candidate is then skipped with ONE fold WARN naming its
     * output item, never silently.
     */
    @Nonnull
    public static List<CraftingCandidate> liveCandidates() {
        List<CraftingCandidate> out = new ArrayList<>();
        try {
            Map<Integer, String> tagNames = liveTagNamesByIndex();
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
                    List<String> benchIds = new ArrayList<>();
                    List<String> types = new ArrayList<>();
                    BenchRequirement[] benches = recipe.getBenchRequirement();
                    if (benches != null) {
                        for (BenchRequirement bench : benches) {
                            if (bench == null) {
                                continue;
                            }
                            if (bench.id != null && !bench.id.isBlank()) {
                                benchIds.add(bench.id);
                            }
                            if (bench.type != null) {
                                types.add(bench.type.name());
                            }
                            if (bench.categories != null) {
                                for (String category : bench.categories) {
                                    if (category != null && !category.isBlank()) {
                                        categories.add(category);
                                    }
                                }
                            }
                        }
                    }
                    // A candidate is derivable when the station can scope to it by native category
                    // OR by native bench id; skip only when it offers neither route.
                    if (categories.isEmpty() && benchIds.isEmpty()) {
                        continue;
                    }
                    float timeSeconds = recipe.getTimeSeconds();
                    List<Ingredient> inputs = new ArrayList<>();
                    boolean everyInputResolvable = true;
                    MaterialQuantity[] mqs = recipe.getInput();
                    if (mqs != null) {
                        for (MaterialQuantity mq : mqs) {
                            if (mq == null) {
                                continue;
                            }
                            // Native consumption precedence: exact ItemId, then the ItemTag, then
                            // the resource family.
                            if (mq.getItemId() != null) {
                                inputs.add(Ingredient.item(mq.getItemId(), mq.getQuantity()));
                            } else if (mq.getTagIndex() != AssetRegistry.TAG_NOT_FOUND) {
                                String tagName = tagNames.get(mq.getTagIndex());
                                if (tagName == null) {
                                    Log.warn("STATION FromCrafting skips '" + item.getId()
                                            + "': its native recipe selects an ItemTag (index "
                                            + mq.getTagIndex() + ") that no loaded item carries, so"
                                            + " the tag name cannot be resolved");
                                    everyInputResolvable = false;
                                    break;
                                }
                                inputs.add(Ingredient.tagged(
                                        Map.of(tagName, new String[0]), mq.getQuantity()));
                            } else {
                                inputs.add(Ingredient.resource(mq.getResourceTypeId(), mq.getQuantity()));
                            }
                        }
                    }
                    if (!everyInputResolvable) {
                        continue;
                    }
                    out.add(new CraftingCandidate(item.getId(), categories, benchIds, types,
                            timeSeconds, inputs));
                }
            }
        } catch (Throwable t) {
            Log.warn("STATION could not enumerate the Item asset map for FromCrafting: " + t.getMessage());
        }
        return out;
    }

    /**
     * The reverse tag map (registered index -&gt; tag name), rebuilt per fold from every loaded
     * item's raw tag keys. Complete for every tag a recipe can meaningfully select: a recipe tag
     * matching NO item's expanded keys would consume nothing natively either.
     */
    @Nonnull
    private static Map<Integer, String> liveTagNamesByIndex() {
        Map<Integer, String> names = new HashMap<>();
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            if (item == null || item.getData() == null) {
                continue;
            }
            Map<String, String[]> raw = item.getData().getRawTags();
            if (raw == null) {
                continue;
            }
            for (String key : raw.keySet()) {
                if (key == null) {
                    continue;
                }
                int index = AssetRegistry.getTagIndex(key);
                if (index != AssetRegistry.TAG_NOT_FOUND) {
                    names.putIfAbsent(index, key);
                }
            }
        }
        return names;
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
     * PURE (selection wave, decision 56): the source-category tag stamped onto a derived
     * conversion. Precedence: (1) a category-route match stamps the MATCHED native category (the
     * first of the candidate's own categories that intersects the wanted set - the meaningful
     * grouping key the picker shows); (2) failing that, if the candidate carries ANY native
     * category, its first one (the recipe's own source category, even on a bench-route match);
     * (3) only "when no category exists" does a bench-route match stamp the matched BENCH id.
     * {@code null} only when a candidate matched with neither a category nor a resolvable bench id
     * (unreachable given the caller already matched, but null-safe). Deterministic + testable
     * without a live item map.
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
     * declared kinds (the pre-seam-wave categories-only shape) always passes so the shipped sawmill
     * derives unchanged.
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
