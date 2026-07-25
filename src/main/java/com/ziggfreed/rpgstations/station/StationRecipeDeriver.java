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
import com.ziggfreed.rpgstations.asset.Ingredient;
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
     * Derive one Conversion per candidate that MATCHES the spec (category intersect OR bench-id
     * match, then filtered by the declared recipe kinds) and whose recipe has EXACTLY ONE input.
     * Deterministic order (sorted by output item id). Pure.
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
        int outputPerInput = spec.getOutputPerInput() != null && spec.getOutputPerInput() > 0
                ? spec.getOutputPerInput() : 1;
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
            if (cand.inputs == null || cand.inputs.size() != 1) {
                Log.fine("STATION FromCrafting skips '" + cand.itemId + "': native recipe has "
                        + (cand.inputs == null ? 0 : cand.inputs.size()) + " inputs (need exactly 1)");
                continue;
            }
            Ingredient nativeInput = cand.inputs.get(0);
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
            Ingredient input = isResource
                    ? Ingredient.resource(nativeInput.getResourceTypeId(), inQty)
                    : Ingredient.item(nativeInput.getItemId(), inQty);
            Ingredient output = Ingredient.item(cand.itemId, outputPerInput);
            Long durationMs = nativeDurationMs(nativeTime, cand.timeSeconds);
            // Selection wave (decision 56): stamp the derived conversion with its native source
            // category so a multi-output station can group the picker by it. The do-not-rework
            // guard on this deriver lifts for exactly this addition.
            String category = deriveSourceCategory(cand, wantCategories, wantBenches, catMatch);
            derived.add(StationAsset.Conversion.of(input, output, durationMs, category));
        }
        derived.sort(Comparator.comparing(c -> c.getOutput().getItemId(), String.CASE_INSENSITIVE_ORDER));
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
                    MaterialQuantity[] mqs = recipe.getInput();
                    if (mqs != null) {
                        for (MaterialQuantity mq : mqs) {
                            if (mq == null) {
                                continue;
                            }
                            inputs.add(Ingredient.of(mq.getItemId(), mq.getResourceTypeId(),
                                    mq.getQuantity()));
                        }
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
