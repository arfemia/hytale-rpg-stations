package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An interactive work station (diegetic work loop), loaded from a pack's
 * {@code Server/RpgStations/Stations/*.json}. Ported from the MMO Skill Tree's
 * {@code asset.type.StationAsset} (RPG Stations extraction phase 1, leg 2 - engine move);
 * see the design doc's section 4.4 for the phase-1 schema deltas from the MMO original
 * (this class's {@link #requires} is RpgStations' OWN {@link Requires} codec, severing the
 * MMO's {@code content.gate.Requirements} dependency). Leg 3 lands the second schema delta:
 * {@code Luck} is REPLACED by {@link #loot} (design section 4.4.3/4.5), the conditional-lootable
 * {@code Loot: { Tables, Rolls }} group over the shared {@link Roll} codec.
 *
 * <p><b>Pattern A - full structured asset, the runtime authority.</b> {@link #CODEC} is the
 * single decode schema for this type; every decoded instance folds into
 * {@code StationCatalog} (defaults &lt; pack). Every top-level field, and every leaf of every
 * nested group codec, is registered via {@code appendInherited} (never plain {@code append}),
 * so native {@code Parent} partial-overlay works.
 */
public final class StationAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, StationAsset>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private Identity identity;
    @Nullable private Work work;
    @Nullable private Recipe recipe;
    @Nullable private Hold hold;
    @Nullable private Tool tool;
    @Nullable private Loot loot;
    @Nullable private Camera camera;
    @Nullable private Animation animation;
    /** The CYCLE-complete presentation moment (sound/particles at the block per finished cycle). */
    @Nullable private Presentation presentation;
    /**
     * The SESSION-COMPLETION presentation moment: the "work complete" beat, played by
     * {@code StationService#stop} for a NON-SILENT stop with at least one completed cycle.
     */
    @Nullable private Presentation completion;
    @Nullable private Requires requires;
    /**
     * Session-scoped placed-input custody (design section 9.4, phase-2 leg C): absent means the
     * classic direct-inventory Consume/Produce flow (phase-1 behavior byte-parity); authored opts
     * the state-dependent F interaction in - see {@code station.StationService#toggle}.
     */
    @Nullable private Custody custody;
    /** Named cosmetic flair overrides, keyed by flair id. */
    @Nullable private Map<String, Flair> flairs;
    /**
     * Multi-action stations (design section 9.1): named, ordered whole-group overrides of this
     * asset's own groups. {@code null}/empty means the phase-1 single implicit {@code "work"}
     * action built from THIS asset's own groups - see {@code station.ActionResolver}. Native
     * {@code Parent} inheritance composes at this WHOLE-MAP level (inherit-on-omit, own-wins-on-
     * author), same as {@link #flairs}.
     */
    @Nullable private Map<String, ActionDef> actions;

    public static final AssetBuilderCodec<String, StationAsset> CODEC = AssetBuilderCodec.builder(
                    StationAsset.class,
                    StationAsset::new,
                    Codec.STRING,
                    // CANONICALIZE the id to lowercase AT DECODE: the engine's asset key is the
                    // verbatim PascalCase FILENAME, while every consumer (lang keys, the catalog
                    // map, the interaction's Station param) is authored lowercase.
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(java.util.Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .appendInherited(new KeyedCodec<>("Identity", Identity.CODEC, false),
                    (a, v) -> a.identity = v, a -> a.identity, (a, parent) -> a.identity = parent.identity)
            .add()
            .appendInherited(new KeyedCodec<>("Work", Work.CODEC, false),
                    (a, v) -> a.work = v, a -> a.work, (a, parent) -> a.work = parent.work)
            .add()
            .appendInherited(new KeyedCodec<>("Recipe", Recipe.CODEC, false),
                    (a, v) -> a.recipe = v, a -> a.recipe, (a, parent) -> a.recipe = parent.recipe)
            .add()
            .appendInherited(new KeyedCodec<>("Hold", Hold.CODEC, false),
                    (a, v) -> a.hold = v, a -> a.hold, (a, parent) -> a.hold = parent.hold)
            .add()
            .appendInherited(new KeyedCodec<>("Tool", Tool.CODEC, false),
                    (a, v) -> a.tool = v, a -> a.tool, (a, parent) -> a.tool = parent.tool)
            .add()
            .appendInherited(new KeyedCodec<>("Loot", Loot.CODEC, false),
                    (a, v) -> a.loot = v, a -> a.loot, (a, parent) -> a.loot = parent.loot)
            .add()
            .appendInherited(new KeyedCodec<>("Camera", Camera.CODEC, false),
                    (a, v) -> a.camera = v, a -> a.camera, (a, parent) -> a.camera = parent.camera)
            .add()
            .appendInherited(new KeyedCodec<>("Animation", Animation.CODEC, false),
                    (a, v) -> a.animation = v, a -> a.animation, (a, parent) -> a.animation = parent.animation)
            .add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (a, v) -> a.presentation = v, a -> a.presentation, (a, parent) -> a.presentation = parent.presentation)
            .add()
            .appendInherited(new KeyedCodec<>("Completion", Presentation.CODEC, false),
                    (a, v) -> a.completion = v, a -> a.completion, (a, parent) -> a.completion = parent.completion)
            .add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (a, v) -> a.requires = v, a -> a.requires, (a, parent) -> a.requires = parent.requires)
            .add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (a, v) -> a.custody = v, a -> a.custody, (a, parent) -> a.custody = parent.custody)
            .add()
            .appendInherited(new KeyedCodec<>("Flairs",
                            new MapCodec<>(Flair.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.flairs = v, a -> a.flairs, (a, parent) -> a.flairs = parent.flairs)
            .add()
            .appendInherited(new KeyedCodec<>("Actions",
                            new MapCodec<>(ActionDef.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.actions = v, a -> a.actions, (a, parent) -> a.actions = parent.actions)
            .add()
            .build();

    public StationAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static StationAsset of(@Nonnull String id, @Nullable Identity identity, @Nullable Work work,
            @Nullable Recipe recipe, @Nullable Hold hold, @Nullable Tool tool, @Nullable Camera camera,
            @Nullable Animation animation, @Nullable Presentation presentation, @Nullable Requires requires) {
        return of(id, identity, work, recipe, hold, tool, camera, animation, presentation, requires, null);
    }

    /** Java-side construction path with the optional {@link Loot} override. */
    @Nonnull
    public static StationAsset of(@Nonnull String id, @Nullable Identity identity, @Nullable Work work,
            @Nullable Recipe recipe, @Nullable Hold hold, @Nullable Tool tool, @Nullable Camera camera,
            @Nullable Animation animation, @Nullable Presentation presentation,
            @Nullable Requires requires, @Nullable Loot loot) {
        return of(id, identity, work, recipe, hold, tool, camera, animation, presentation, requires, loot, null);
    }

    /** Java-side construction path with the optional {@link Loot} AND {@link Flair} map override. */
    @Nonnull
    public static StationAsset of(@Nonnull String id, @Nullable Identity identity, @Nullable Work work,
            @Nullable Recipe recipe, @Nullable Hold hold, @Nullable Tool tool, @Nullable Camera camera,
            @Nullable Animation animation, @Nullable Presentation presentation,
            @Nullable Requires requires, @Nullable Loot loot, @Nullable Map<String, Flair> flairs) {
        StationAsset a = new StationAsset();
        a.id = id;
        a.identity = identity;
        a.work = work;
        a.recipe = recipe;
        a.hold = hold;
        a.tool = tool;
        a.loot = loot;
        a.camera = camera;
        a.animation = animation;
        a.presentation = presentation;
        a.requires = requires;
        a.flairs = flairs;
        return a;
    }

    /** Java-side construction path with the optional {@link Loot}, {@link Flair} map, AND {@link #completion}. */
    @Nonnull
    public static StationAsset of(@Nonnull String id, @Nullable Identity identity, @Nullable Work work,
            @Nullable Recipe recipe, @Nullable Hold hold, @Nullable Tool tool, @Nullable Camera camera,
            @Nullable Animation animation, @Nullable Presentation presentation,
            @Nullable Requires requires, @Nullable Loot loot, @Nullable Map<String, Flair> flairs,
            @Nullable Presentation completion) {
        StationAsset a = of(id, identity, work, recipe, hold, tool, camera, animation, presentation, requires,
                loot, flairs);
        a.completion = completion;
        return a;
    }

    /** The CANONICAL lowercase station id, normalized at decode from the engine's PascalCase filename key. */
    @Override
    public String getId() {
        return id;
    }

    /**
     * The PascalCase, underscore-preserving filename a station id decodes to (the CODEC's
     * {@code toLowerCase} transform run backwards). Used by tests to locate a shipped
     * {@code Server/RpgStations/Stations/<Name>.json} by id.
     */
    @Nonnull
    public static String filenameFor(@Nonnull String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder(id.length());
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('_');
            }
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    @Nullable
    public Identity getIdentity() {
        return identity;
    }

    @Nullable
    public Work getWork() {
        return work;
    }

    @Nullable
    public Recipe getRecipe() {
        return recipe;
    }

    @Nullable
    public Hold getHold() {
        return hold;
    }

    @Nullable
    public Tool getTool() {
        return tool;
    }

    @Nullable
    public Loot getLoot() {
        return loot;
    }

    @Nullable
    public Camera getCamera() {
        return camera;
    }

    @Nullable
    public Animation getAnimation() {
        return animation;
    }

    /** The CYCLE-complete presentation moment (sound/particles at the block per finished cycle). */
    @Nullable
    public Presentation getPresentation() {
        return presentation;
    }

    /** The SESSION-COMPLETION presentation moment; null = silent (no completion flourish). */
    @Nullable
    public Presentation getCompletion() {
        return completion;
    }

    @Nullable
    public Requires getRequires() {
        return requires;
    }

    /** Session-scoped placed-input custody (design 9.4); null = classic direct-inventory flow. */
    @Nullable
    public Custody getCustody() {
        return custody;
    }

    /** Named cosmetic flair overrides, keyed by flair id; null = none authored. */
    @Nullable
    public Map<String, Flair> getFlairs() {
        return flairs;
    }

    /**
     * Named, authored-order action overrides (design section 9.1); {@code null}/empty means the
     * single implicit {@code "work"} action - see {@code station.ActionResolver#actionIds}.
     */
    @Nullable
    public Map<String, ActionDef> getActions() {
        return actions;
    }

    /** Java-side test/fixture helper; not part of any codec fold. */
    @Nonnull
    public StationAsset withActions(@Nullable Map<String, ActionDef> actions) {
        this.actions = actions;
        return this;
    }

    /** Java-side test/fixture helper; not part of any codec fold. */
    @Nonnull
    public StationAsset withCustody(@Nullable Custody custody) {
        this.custody = custody;
        return this;
    }

    // ==================== Nested groups (nullable leaves) ====================

    /** Display keys and icon (an item id, per the ability-icon convention). */
    public static final class Identity {
        @Nullable protected String nameKey;
        @Nullable protected String descKey;
        @Nullable protected String icon;

        public static final BuilderCodec<Identity> CODEC = BuilderCodec.builder(Identity.class, Identity::new)
                .appendInherited(new KeyedCodec<>("NameKey", Codec.STRING, false),
                        (o, v) -> o.nameKey = v, o -> o.nameKey, (o, p) -> o.nameKey = p.nameKey).add()
                .appendInherited(new KeyedCodec<>("DescKey", Codec.STRING, false),
                        (o, v) -> o.descKey = v, o -> o.descKey, (o, p) -> o.descKey = p.descKey).add()
                .appendInherited(new KeyedCodec<>("Icon", Codec.STRING, false),
                        (o, v) -> o.icon = v, o -> o.icon, (o, p) -> o.icon = p.icon).add()
                .build();

        @Nonnull
        public static Identity of(@Nullable String nameKey, @Nullable String descKey, @Nullable String icon) {
            Identity i = new Identity();
            i.nameKey = nameKey;
            i.descKey = descKey;
            i.icon = icon;
            return i;
        }

        @Nullable
        public String getNameKey() {
            return nameKey;
        }

        @Nullable
        public String getDescKey() {
            return descKey;
        }

        @Nullable
        public String getIcon() {
            return icon;
        }
    }

    /**
     * The work-loop cadence and bounds. Reader defaults ({@code StationService}):
     * {@code CycleMs} 5000, {@code MaxDurationMs} 600000, {@code MaxMoveMeters} 1.5,
     * {@code Exclusive} true. {@code MaxMoveMeters} is an EXIT trigger, NOT an anti-idle guard.
     */
    public static final class Work {
        @Nullable protected Long cycleMs;
        @Nullable protected Long maxDurationMs;
        @Nullable protected Double maxMoveMeters;
        @Nullable protected Boolean exclusive;
        @Nullable protected WorkXp[] xp;
        @Nullable protected Idle idle;
        @Nullable protected Boolean repeat;

        public static final BuilderCodec<Work> CODEC = BuilderCodec.builder(Work.class, Work::new)
                .appendInherited(new KeyedCodec<>("CycleMs", Codec.LONG, false),
                        (o, v) -> o.cycleMs = v, o -> o.cycleMs, (o, p) -> o.cycleMs = p.cycleMs).add()
                .appendInherited(new KeyedCodec<>("MaxDurationMs", Codec.LONG, false),
                        (o, v) -> o.maxDurationMs = v, o -> o.maxDurationMs,
                        (o, p) -> o.maxDurationMs = p.maxDurationMs).add()
                .appendInherited(new KeyedCodec<>("MaxMoveMeters", Codec.DOUBLE, false),
                        (o, v) -> o.maxMoveMeters = v, o -> o.maxMoveMeters,
                        (o, p) -> o.maxMoveMeters = p.maxMoveMeters).add()
                .appendInherited(new KeyedCodec<>("Exclusive", Codec.BOOLEAN, false),
                        (o, v) -> o.exclusive = v, o -> o.exclusive, (o, p) -> o.exclusive = p.exclusive).add()
                .appendInherited(new KeyedCodec<>("Xp", new ArrayCodec<>(WorkXp.CODEC, WorkXp[]::new), false),
                        (o, v) -> o.xp = v, o -> o.xp, (o, p) -> o.xp = p.xp).add()
                .appendInherited(new KeyedCodec<>("Idle", Idle.CODEC, false),
                        (o, v) -> o.idle = v, o -> o.idle, (o, p) -> o.idle = p.idle).add()
                .appendInherited(new KeyedCodec<>("Repeat", Codec.BOOLEAN, false),
                        (o, v) -> o.repeat = v, o -> o.repeat, (o, p) -> o.repeat = p.repeat).add()
                .build();

        @Nonnull
        public static Work of(@Nullable Long cycleMs, @Nullable Long maxDurationMs,
                @Nullable Double maxMoveMeters, @Nullable Boolean exclusive, @Nullable WorkXp[] xp) {
            return of(cycleMs, maxDurationMs, maxMoveMeters, exclusive, xp, null);
        }

        @Nonnull
        public static Work of(@Nullable Long cycleMs, @Nullable Long maxDurationMs,
                @Nullable Double maxMoveMeters, @Nullable Boolean exclusive, @Nullable WorkXp[] xp,
                @Nullable Idle idle) {
            Work w = new Work();
            w.cycleMs = cycleMs;
            w.maxDurationMs = maxDurationMs;
            w.maxMoveMeters = maxMoveMeters;
            w.exclusive = exclusive;
            w.xp = xp;
            w.idle = idle;
            return w;
        }

        @Nullable
        public Long getCycleMs() {
            return cycleMs;
        }

        @Nullable
        public Long getMaxDurationMs() {
            return maxDurationMs;
        }

        @Nullable
        public Double getMaxMoveMeters() {
            return maxMoveMeters;
        }

        @Nullable
        public Boolean getExclusive() {
            return exclusive;
        }

        @Nullable
        public WorkXp[] getXp() {
            return xp;
        }

        @Nullable
        public Idle getIdle() {
            return idle;
        }

        /**
         * Whether the program (implicit or authored {@code Steps}) re-runs per {@link #cycleMs}
         * cadence (design section 9.3 - {@code true}, the default when null, is "the classic
         * loop"), or a single completed program run completes the whole SESSION ({@code false} -
         * the ritual shape, e.g. the anvil's Enhance action). Read by
         * {@code station.step.StationStepKernel}'s program-completion handling, never by the pure
         * step engine itself.
         */
        @Nullable
        public Boolean getRepeat() {
            return repeat;
        }

        /** {@link #repeat}, reader-defaulted to {@code true} (the classic loop) when null. */
        public boolean effectiveRepeat() {
            return repeat == null || repeat;
        }

        /**
         * Opt-in no-material idle practice: when the station has no runnable conversion AND
         * {@link #enabled}, the session grants tiny fractional XP-ask forwarding instead of
         * stopping. Default OFF. {@link #cycleMs} reader-defaults to 3x the effective
         * {@code Work.CycleMs}, floored at 2x it; {@link #xpFraction} reader-defaults to 0.1,
         * clamped to {@code [0, 1]}.
         */
        public static final class Idle {
            @Nullable protected Boolean enabled;
            @Nullable protected Long cycleMs;
            @Nullable protected Double xpFraction;

            public static final BuilderCodec<Idle> CODEC = BuilderCodec.builder(Idle.class, Idle::new)
                    .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                            (o, v) -> o.enabled = v, o -> o.enabled, (o, p) -> o.enabled = p.enabled).add()
                    .appendInherited(new KeyedCodec<>("CycleMs", Codec.LONG, false),
                            (o, v) -> o.cycleMs = v, o -> o.cycleMs, (o, p) -> o.cycleMs = p.cycleMs).add()
                    .appendInherited(new KeyedCodec<>("XpFraction", Codec.DOUBLE, false),
                            (o, v) -> o.xpFraction = v, o -> o.xpFraction, (o, p) -> o.xpFraction = p.xpFraction).add()
                    .build();

            @Nonnull
            public static Idle of(@Nullable Boolean enabled, @Nullable Long cycleMs, @Nullable Double xpFraction) {
                Idle i = new Idle();
                i.enabled = enabled;
                i.cycleMs = cycleMs;
                i.xpFraction = xpFraction;
                return i;
            }

            @Nullable
            public Boolean getEnabled() {
                return enabled;
            }

            @Nullable
            public Long getCycleMs() {
                return cycleMs;
            }

            @Nullable
            public Double getXpFraction() {
                return xpFraction;
            }
        }
    }

    /**
     * One fixed per-CYCLE progression declaration. The engine never interprets this itself
     * (design section 4.4.1) - it forwards the ask + the resolved tool multiplier on the
     * cycle-completed event; whichever progression mod is present (or none) decides what to
     * do with it.
     */
    public static final class WorkXp {
        @Nullable protected String skill;
        @Nullable protected Double perCycle;

        public static final BuilderCodec<WorkXp> CODEC = BuilderCodec.builder(WorkXp.class, WorkXp::new)
                .appendInherited(new KeyedCodec<>("Skill", Codec.STRING, false),
                        (o, v) -> o.skill = v, o -> o.skill, (o, p) -> o.skill = p.skill).add()
                .appendInherited(new KeyedCodec<>("PerCycle", Codec.DOUBLE, false),
                        (o, v) -> o.perCycle = v, o -> o.perCycle, (o, p) -> o.perCycle = p.perCycle).add()
                .build();

        @Nonnull
        public static WorkXp of(@Nullable String skill, @Nullable Double perCycle) {
            WorkXp x = new WorkXp();
            x.skill = skill;
            x.perCycle = perCycle;
            return x;
        }

        @Nullable
        public String getSkill() {
            return skill;
        }

        @Nullable
        public Double getPerCycle() {
            return perCycle;
        }
    }

    /**
     * The Convert recipe. Its EFFECTIVE conversions ({@code StationCatalog.resolvedConversions})
     * are authored {@link #conversions} FIRST, then any {@link FromCrafting}-derived conversions.
     */
    public static final class Recipe {
        @Nullable protected Conversion[] conversions;
        @Nullable protected FromCrafting fromCrafting;

        public static final BuilderCodec<Recipe> CODEC = BuilderCodec.builder(Recipe.class, Recipe::new)
                .appendInherited(new KeyedCodec<>("Conversions",
                                new ArrayCodec<>(Conversion.CODEC, Conversion[]::new), false),
                        (o, v) -> o.conversions = v, o -> o.conversions,
                        (o, p) -> o.conversions = p.conversions).add()
                .appendInherited(new KeyedCodec<>("FromCrafting", FromCrafting.CODEC, false),
                        (o, v) -> o.fromCrafting = v, o -> o.fromCrafting,
                        (o, p) -> o.fromCrafting = p.fromCrafting).add()
                .build();

        @Nonnull
        public static Recipe of(@Nullable Conversion[] conversions) {
            return of(conversions, null);
        }

        @Nonnull
        public static Recipe of(@Nullable Conversion[] conversions, @Nullable FromCrafting fromCrafting) {
            Recipe r = new Recipe();
            r.conversions = conversions;
            r.fromCrafting = fromCrafting;
            return r;
        }

        @Nullable
        public Conversion[] getConversions() {
            return conversions;
        }

        @Nullable
        public FromCrafting getFromCrafting() {
            return fromCrafting;
        }
    }

    /**
     * Derive-from-native rule: one Conversion is derived per LIVE {@code Item} whose native
     * bench-requirement categories intersect {@link #categories} and whose native recipe has
     * EXACTLY ONE input. {@link #outputPerInput} (default 1) is the OPTIONAL yield multiplier.
     */
    public static final class FromCrafting {
        @Nullable protected String[] categories;
        @Nullable protected Integer outputPerInput;

        public static final BuilderCodec<FromCrafting> CODEC = BuilderCodec.builder(FromCrafting.class, FromCrafting::new)
                .appendInherited(new KeyedCodec<>("Categories", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.categories = v, o -> o.categories, (o, p) -> o.categories = p.categories).add()
                .appendInherited(new KeyedCodec<>("OutputPerInput", Codec.INTEGER, false),
                        (o, v) -> o.outputPerInput = v, o -> o.outputPerInput,
                        (o, p) -> o.outputPerInput = p.outputPerInput).add()
                .build();

        @Nonnull
        public static FromCrafting of(@Nullable String[] categories, @Nullable Integer outputPerInput) {
            FromCrafting f = new FromCrafting();
            f.categories = categories;
            f.outputPerInput = outputPerInput;
            return f;
        }

        @Nullable
        public String[] getCategories() {
            return categories;
        }

        @Nullable
        public Integer getOutputPerInput() {
            return outputPerInput;
        }
    }

    /**
     * One native-shaped input-to-output conversion. {@code Input} is a native
     * {@link Ingredient} (exactly one of {@code ItemId}/{@code ResourceTypeId}); {@code Output}
     * is always an exact {@code ItemId} ingredient.
     */
    public static final class Conversion {
        @Nullable protected Ingredient input;
        @Nullable protected Ingredient output;

        public static final BuilderCodec<Conversion> CODEC = BuilderCodec.builder(Conversion.class, Conversion::new)
                .appendInherited(new KeyedCodec<>("Input", Ingredient.CODEC, false),
                        (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input).add()
                .appendInherited(new KeyedCodec<>("Output", Ingredient.CODEC, false),
                        (o, v) -> o.output = v, o -> o.output, (o, p) -> o.output = p.output).add()
                .build();

        @Nonnull
        public static Conversion of(@Nullable Ingredient input, @Nullable Ingredient output) {
            Conversion c = new Conversion();
            c.input = input;
            c.output = output;
            return c;
        }

        @Nullable
        public Ingredient getInput() {
            return input;
        }

        @Nullable
        public Ingredient getOutput() {
            return output;
        }
    }

    /**
     * A native-shaped recipe ingredient mirroring vanilla {@code MaterialQuantity}. An INPUT
     * sets exactly one of {@link #itemId} or {@link #resourceTypeId} (a native
     * {@code Item.ResourceTypes} family - the "any log" route); an OUTPUT sets only {@code ItemId}.
     */
    public static final class Ingredient {
        @Nullable protected String itemId;
        @Nullable protected String resourceTypeId;
        @Nullable protected Integer quantity;

        public static final BuilderCodec<Ingredient> CODEC = BuilderCodec.builder(Ingredient.class, Ingredient::new)
                .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                        (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId).add()
                .appendInherited(new KeyedCodec<>("ResourceTypeId", Codec.STRING, false),
                        (o, v) -> o.resourceTypeId = v, o -> o.resourceTypeId,
                        (o, p) -> o.resourceTypeId = p.resourceTypeId).add()
                .appendInherited(new KeyedCodec<>("Quantity", Codec.INTEGER, false),
                        (o, v) -> o.quantity = v, o -> o.quantity, (o, p) -> o.quantity = p.quantity).add()
                .build();

        @Nonnull
        public static Ingredient of(@Nullable String itemId, @Nullable String resourceTypeId,
                @Nullable Integer quantity) {
            Ingredient i = new Ingredient();
            i.itemId = itemId;
            i.resourceTypeId = resourceTypeId;
            i.quantity = quantity;
            return i;
        }

        /** Convenience: an exact-item ingredient ({@code ItemId}). */
        @Nonnull
        public static Ingredient item(@Nullable String itemId, @Nullable Integer quantity) {
            return of(itemId, null, quantity);
        }

        /** Convenience: a native resource-type family ingredient ({@code ResourceTypeId}); INPUT only. */
        @Nonnull
        public static Ingredient resource(@Nullable String resourceTypeId, @Nullable Integer quantity) {
            return of(null, resourceTypeId, quantity);
        }

        @Nullable
        public String getItemId() {
            return itemId;
        }

        @Nullable
        public String getResourceTypeId() {
            return resourceTypeId;
        }

        @Nullable
        public Integer getQuantity() {
            return quantity;
        }
    }

    /**
     * The movement-hold layer: a short-TTL self-targeted {@code EntityEffect} re-applied every
     * heartbeat (decay-as-release). Reader defaults: {@code MovementLock} true, {@code EffectId}
     * {@code "RPG_Station_Hold"}, {@code InterruptOnDamage} true.
     *
     * <p>{@link #mount}: the mount knob family (design section 9.2, phase 2 leg D) - an
     * alternate hold strategy trading the packet-camera hunt for the engine's own native mount
     * mechanics. When {@link #mount} is authored, {@link #movementLock}/{@link #effectId} are
     * IGNORED for the BLOCK surface (the mount itself is the lock) but stay meaningful for the
     * ENTITY surface's default (non-{@code Steerable}) case - see {@link Mount} for the full
     * per-surface breakdown; {@link #interruptOnDamage} stays live either way.
     */
    public static final class Hold {
        @Nullable protected Boolean movementLock;
        @Nullable protected String effectId;
        @Nullable protected Boolean interruptOnDamage;
        @Nullable protected Mount mount;

        public static final BuilderCodec<Hold> CODEC = BuilderCodec.builder(Hold.class, Hold::new)
                .appendInherited(new KeyedCodec<>("MovementLock", Codec.BOOLEAN, false),
                        (o, v) -> o.movementLock = v, o -> o.movementLock,
                        (o, p) -> o.movementLock = p.movementLock).add()
                .appendInherited(new KeyedCodec<>("EffectId", Codec.STRING, false),
                        (o, v) -> o.effectId = v, o -> o.effectId, (o, p) -> o.effectId = p.effectId).add()
                .appendInherited(new KeyedCodec<>("InterruptOnDamage", Codec.BOOLEAN, false),
                        (o, v) -> o.interruptOnDamage = v, o -> o.interruptOnDamage,
                        (o, p) -> o.interruptOnDamage = p.interruptOnDamage).add()
                .appendInherited(new KeyedCodec<>("Mount", Mount.CODEC, false),
                        (o, v) -> o.mount = v, o -> o.mount, (o, p) -> o.mount = p.mount).add()
                .build();

        @Nonnull
        public static Hold of(@Nullable Boolean movementLock, @Nullable String effectId,
                @Nullable Boolean interruptOnDamage) {
            return of(movementLock, effectId, interruptOnDamage, null);
        }

        @Nonnull
        public static Hold of(@Nullable Boolean movementLock, @Nullable String effectId,
                @Nullable Boolean interruptOnDamage, @Nullable Mount mount) {
            Hold h = new Hold();
            h.movementLock = movementLock;
            h.effectId = effectId;
            h.interruptOnDamage = interruptOnDamage;
            h.mount = mount;
            return h;
        }

        @Nullable
        public Boolean getMovementLock() {
            return movementLock;
        }

        @Nullable
        public String getEffectId() {
            return effectId;
        }

        @Nullable
        public Boolean getInterruptOnDamage() {
            return interruptOnDamage;
        }

        @Nullable
        public Mount getMount() {
            return mount;
        }

        /**
         * The mount knob family (design section 9.2, phase 2 leg D). REPLACES the phase-1
         * {@code Hold.Seat.Enabled} flag (unreleased rename, no back-compat alias - the pack's
         * own copy of the sawmill moves in lockstep, see {@code station/CLAUDE.md}).
         *
         * <p><b>{@link #surface} is a UNION DISCRIMINATOR, not a mode</b> (critique m3's bless,
         * recorded here per the binding fix's "write the one-line rationale into the codec
         * javadoc and the router" instruction): {@code "Block"} and {@code "Entity"} route to two
         * STRUCTURALLY DIFFERENT engine mechanisms (native {@code BlockMountAPI.mountOnBlock} vs
         * a plugin-spawned anchor entity + a directly-attached {@code MountedComponent}), each
         * with its OWN sub-knob set and its own steering/drift risk profile - the same shape as
         * {@code EffectStep.Type}, never a bundled mode collapsing independent switches into one
         * enum. Absent {@link #surface} on an authored {@code Mount} group defaults to
         * {@code "Block"} (the phase-1 single-surface behavior, now expressed as this
         * discriminator's default arm rather than a separate boolean flag).
         *
         * <ul>
         *   <li><b>{@code "Block"}</b> - today's seat mount, UNCHANGED (the regression anchor):
         *   {@code station.StationMountController.mount} via native {@code BlockMountAPI}. The
         *   target block must author {@code BlockType.Seats[]}. {@link #entity} is not read.
         *   <li><b>{@code "Entity"}</b> - the STANDING work mount (design 9.2's "furniture /
         *   vehicle / mount that can show player NOT sitting"): {@code
         *   station.StationEntityMountController} spawns a minimal anchor entity at engage and
         *   attaches {@code MountedComponent(anchorRef, attachmentOffset,
         *   MountController.Minecart)} to the player directly (no interaction chain - the plugin
         *   attaches it itself). Because this path never populates the client's
         *   {@code MountedUpdate.Block} field (that leaf is BlockMount-exclusive), the mount mine
         *   infers the player renders STANDING by construction - in-game-unverifiable from server
         *   source alone, the maintainer's phase-2 smoke item. See {@link Entity} for its
         *   sub-knobs.
         * </ul>
         */
        public static final class Mount {
            @Nullable protected String surface;
            @Nullable protected Entity entity;

            public static final BuilderCodec<Mount> CODEC = BuilderCodec.builder(Mount.class, Mount::new)
                    .appendInherited(new KeyedCodec<>("Surface", Codec.STRING, false),
                            (o, v) -> o.surface = v, o -> o.surface, (o, p) -> o.surface = p.surface).add()
                    .appendInherited(new KeyedCodec<>("Entity", Entity.CODEC, false),
                            (o, v) -> o.entity = v, o -> o.entity, (o, p) -> o.entity = p.entity).add()
                    .build();

            @Nonnull
            public static Mount of(@Nullable String surface, @Nullable Entity entity) {
                Mount m = new Mount();
                m.surface = surface;
                m.entity = entity;
                return m;
            }

            @Nullable
            public String getSurface() {
                return surface;
            }

            @Nullable
            public Entity getEntity() {
                return entity;
            }

            /**
             * True when {@link #surface} is {@code "Entity"} (trimmed, case-insensitive);
             * everything else (null, blank, {@code "Block"}, or an unrecognized value - the
             * validator warns on the last case, never blocks) resolves to the Block route.
             */
            public boolean isEntitySurface() {
                return surface != null && "Entity".equalsIgnoreCase(surface.trim());
            }

            /**
             * The standing work mount's own sub-knobs (design 9.2); read ONLY when
             * {@link Mount#isEntitySurface()} - authoring this group under a Block surface is a
             * validator warning ({@code MOUNT_ENTITY_GROUP_IGNORED}), never an error.
             */
            public static final class Entity {
                @Nullable protected Offset offset;
                @Nullable protected Boolean dismountOnMove;
                @Nullable protected Boolean steerable;

                public static final BuilderCodec<Entity> CODEC = BuilderCodec.builder(Entity.class, Entity::new)
                        .appendInherited(new KeyedCodec<>("Offset", Offset.CODEC, false),
                                (o, v) -> o.offset = v, o -> o.offset, (o, p) -> o.offset = p.offset).add()
                        .appendInherited(new KeyedCodec<>("DismountOnMove", Codec.BOOLEAN, false),
                                (o, v) -> o.dismountOnMove = v, o -> o.dismountOnMove,
                                (o, p) -> o.dismountOnMove = p.dismountOnMove).add()
                        .appendInherited(new KeyedCodec<>("Steerable", Codec.BOOLEAN, false),
                                (o, v) -> o.steerable = v, o -> o.steerable,
                                (o, p) -> o.steerable = p.steerable).add()
                        .build();

                @Nonnull
                public static Entity of(@Nullable Offset offset, @Nullable Boolean dismountOnMove,
                        @Nullable Boolean steerable) {
                    Entity e = new Entity();
                    e.offset = offset;
                    e.dismountOnMove = dismountOnMove;
                    e.steerable = steerable;
                    return e;
                }

                @Nullable
                public Offset getOffset() {
                    return offset;
                }

                @Nullable
                public Boolean getDismountOnMove() {
                    return dismountOnMove;
                }

                @Nullable
                public Boolean getSteerable() {
                    return steerable;
                }

                /**
                 * {@link #dismountOnMove}, reader-defaulted to {@code true} when null: the
                 * heartbeat implements a walk-off check (no native auto-dismount exists for the
                 * entity-mount controller). {@code false} = hard-lock until crouch/re-press (the
                 * enchanting-circle look).
                 */
                public boolean effectiveDismountOnMove() {
                    return dismountOnMove == null || dismountOnMove;
                }

                /**
                 * {@link #steerable}, reader-defaulted to {@code false} when null: the default
                 * applies the hold effect + a per-heartbeat anchor snap-back to defeat the native
                 * WASD-steers-the-anchor behavior. {@code true} skips both (reserved for a future
                 * vehicle-like station; {@code station.StationValidator} flags it as untested).
                 */
                public boolean effectiveSteerable() {
                    return steerable != null && steerable;
                }

                /**
                 * The attachment-offset knob. CRITIQUE FIX (m7): the {@code MountedComponent}
                 * entity-mount constructor's matching parameter is a {@code Rotation3f}, NOT a
                 * {@code Vector3f}, despite reading like a plain positional offset (a native
                 * mislabeling the mount mine confirms - {@code attachmentOffset} is used as a
                 * spatial XYZ offset for entity mounts, never as an actual rotation). The
                 * conversion happens explicitly at the ONE ECS call site,
                 * {@code station.StationEntityMountController.attach} - see that class's javadoc.
                 */
                public static final class Offset {
                    @Nullable protected Double x;
                    @Nullable protected Double y;
                    @Nullable protected Double z;

                    public static final BuilderCodec<Offset> CODEC = BuilderCodec.builder(Offset.class, Offset::new)
                            .appendInherited(new KeyedCodec<>("X", Codec.DOUBLE, false),
                                    (o, v) -> o.x = v, o -> o.x, (o, p) -> o.x = p.x).add()
                            .appendInherited(new KeyedCodec<>("Y", Codec.DOUBLE, false),
                                    (o, v) -> o.y = v, o -> o.y, (o, p) -> o.y = p.y).add()
                            .appendInherited(new KeyedCodec<>("Z", Codec.DOUBLE, false),
                                    (o, v) -> o.z = v, o -> o.z, (o, p) -> o.z = p.z).add()
                            .build();

                    @Nonnull
                    public static Offset of(@Nullable Double x, @Nullable Double y, @Nullable Double z) {
                        Offset o = new Offset();
                        o.x = x;
                        o.y = y;
                        o.z = z;
                        return o;
                    }

                    @Nullable
                    public Double getX() {
                        return x;
                    }

                    @Nullable
                    public Double getY() {
                        return y;
                    }

                    @Nullable
                    public Double getZ() {
                        return z;
                    }
                }
            }
        }
    }

    /**
     * The held-tool gate: the player must be HOLDING a matching tool to start (and keep)
     * working. Three optional NATIVE match routes; match = ANY route satisfied.
     */
    public static final class Tool {
        @Nullable protected Map<String, String[]> tags;
        @Nullable protected Gather gather;
        @Nullable protected String[] ids;
        @Nullable protected XpScale xpScale;
        @Nullable protected Durability durability;

        public static final BuilderCodec<Tool> CODEC = BuilderCodec.builder(Tool.class, Tool::new)
                .appendInherited(new KeyedCodec<>("Tags",
                                new MapCodec<>(new ArrayCodec<>(Codec.STRING, String[]::new), LinkedHashMap::new), false),
                        (o, v) -> o.tags = v, o -> o.tags, (o, p) -> o.tags = p.tags).add()
                .appendInherited(new KeyedCodec<>("Gather", Gather.CODEC, false),
                        (o, v) -> o.gather = v, o -> o.gather, (o, p) -> o.gather = p.gather).add()
                .appendInherited(new KeyedCodec<>("Ids", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.ids = v, o -> o.ids, (o, p) -> o.ids = p.ids).add()
                .appendInherited(new KeyedCodec<>("XpScale", XpScale.CODEC, false),
                        (o, v) -> o.xpScale = v, o -> o.xpScale, (o, p) -> o.xpScale = p.xpScale).add()
                .appendInherited(new KeyedCodec<>("Durability", Durability.CODEC, false),
                        (o, v) -> o.durability = v, o -> o.durability, (o, p) -> o.durability = p.durability).add()
                .build();

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids) {
            return of(tags, gather, ids, null);
        }

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids, @Nullable XpScale xpScale) {
            return of(tags, gather, ids, xpScale, null);
        }

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids, @Nullable XpScale xpScale, @Nullable Durability durability) {
            Tool t = new Tool();
            t.tags = tags;
            t.gather = gather;
            t.ids = ids;
            t.xpScale = xpScale;
            t.durability = durability;
            return t;
        }

        @Nullable
        public Map<String, String[]> getTags() {
            return tags;
        }

        @Nullable
        public Gather getGather() {
            return gather;
        }

        @Nullable
        public String[] getIds() {
            return ids;
        }

        @Nullable
        public XpScale getXpScale() {
            return xpScale;
        }

        @Nullable
        public Durability getDurability() {
            return durability;
        }

        /** The functional gather route: a {@code GatherType} plus a {@code MinPower} floor. */
        public static final class Gather {
            @Nullable protected String gatherType;
            @Nullable protected Double minPower;

            public static final BuilderCodec<Gather> CODEC = BuilderCodec.builder(Gather.class, Gather::new)
                    .appendInherited(new KeyedCodec<>("GatherType", Codec.STRING, false),
                            (o, v) -> o.gatherType = v, o -> o.gatherType, (o, p) -> o.gatherType = p.gatherType).add()
                    .appendInherited(new KeyedCodec<>("MinPower", Codec.DOUBLE, false),
                            (o, v) -> o.minPower = v, o -> o.minPower, (o, p) -> o.minPower = p.minPower).add()
                    .build();

            @Nonnull
            public static Gather of(@Nullable String gatherType, @Nullable Double minPower) {
                Gather g = new Gather();
                g.gatherType = gatherType;
                g.minPower = minPower;
                return g;
            }

            @Nullable
            public String getGatherType() {
                return gatherType;
            }

            @Nullable
            public Double getMinPower() {
                return minPower;
            }
        }

        /**
         * Tool-power XP scaling: cycle XP multiplies by
         * {@code clamp((heldPower / ReferencePower) ^ Exponent, MinMult, MaxMult)}. OMIT this
         * group and the multiplier stays 1.0. Reader defaults: {@link #exponent} 1.0,
         * {@link #minMult} 0.5, {@link #maxMult} 2.0.
         */
        public static final class XpScale {
            @Nullable protected String gatherType;
            @Nullable protected Double referencePower;
            @Nullable protected Double exponent;
            @Nullable protected Double minMult;
            @Nullable protected Double maxMult;

            public static final BuilderCodec<XpScale> CODEC = BuilderCodec.builder(XpScale.class, XpScale::new)
                    .appendInherited(new KeyedCodec<>("GatherType", Codec.STRING, false),
                            (o, v) -> o.gatherType = v, o -> o.gatherType, (o, p) -> o.gatherType = p.gatherType).add()
                    .appendInherited(new KeyedCodec<>("ReferencePower", Codec.DOUBLE, false),
                            (o, v) -> o.referencePower = v, o -> o.referencePower,
                            (o, p) -> o.referencePower = p.referencePower).add()
                    .appendInherited(new KeyedCodec<>("Exponent", Codec.DOUBLE, false),
                            (o, v) -> o.exponent = v, o -> o.exponent, (o, p) -> o.exponent = p.exponent).add()
                    .appendInherited(new KeyedCodec<>("MinMult", Codec.DOUBLE, false),
                            (o, v) -> o.minMult = v, o -> o.minMult, (o, p) -> o.minMult = p.minMult).add()
                    .appendInherited(new KeyedCodec<>("MaxMult", Codec.DOUBLE, false),
                            (o, v) -> o.maxMult = v, o -> o.maxMult, (o, p) -> o.maxMult = p.maxMult).add()
                    .build();

            @Nonnull
            public static XpScale of(@Nullable String gatherType, @Nullable Double referencePower,
                    @Nullable Double exponent, @Nullable Double minMult, @Nullable Double maxMult) {
                XpScale x = new XpScale();
                x.gatherType = gatherType;
                x.referencePower = referencePower;
                x.exponent = exponent;
                x.minMult = minMult;
                x.maxMult = maxMult;
                return x;
            }

            @Nullable
            public String getGatherType() {
                return gatherType;
            }

            @Nullable
            public Double getReferencePower() {
                return referencePower;
            }

            @Nullable
            public Double getExponent() {
                return exponent;
            }

            @Nullable
            public Double getMinMult() {
                return minMult;
            }

            @Nullable
            public Double getMaxMult() {
                return maxMult;
            }
        }

        /**
         * Opt-in held-tool durability drain (both leaves default OFF; either, both, or
         * neither may be authored).
         */
        public static final class Durability {
            @Nullable protected Integer perSwing;
            @Nullable protected Integer perCycle;

            public static final BuilderCodec<Durability> CODEC = BuilderCodec.builder(Durability.class, Durability::new)
                    .appendInherited(new KeyedCodec<>("PerSwing", Codec.INTEGER, false),
                            (o, v) -> o.perSwing = v, o -> o.perSwing, (o, p) -> o.perSwing = p.perSwing).add()
                    .appendInherited(new KeyedCodec<>("PerCycle", Codec.INTEGER, false),
                            (o, v) -> o.perCycle = v, o -> o.perCycle, (o, p) -> o.perCycle = p.perCycle).add()
                    .build();

            @Nonnull
            public static Durability of(@Nullable Integer perSwing, @Nullable Integer perCycle) {
                Durability d = new Durability();
                d.perSwing = perSwing;
                d.perCycle = perCycle;
                return d;
            }

            @Nullable
            public Integer getPerSwing() {
                return perSwing;
            }

            @Nullable
            public Integer getPerCycle() {
                return perCycle;
            }
        }
    }

    /**
     * The conditional-lootable declaration (design section 4.4.3/4.5, REPLACES the MMO's
     * {@code Luck} group this leg): references to shared {@link LootableAsset} tables and/or
     * inline {@link Roll}s, both optional and independently composable (a station may combine
     * any number of tables with its own inline rolls). See {@code loot.LootEngine} for
     * resolution + evaluation.
     */
    public static final class Loot {
        @Nullable protected String[] tables;
        @Nullable protected Roll[] rolls;

        public static final BuilderCodec<Loot> CODEC = BuilderCodec.builder(Loot.class, Loot::new)
                .appendInherited(new KeyedCodec<>("Tables", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.tables = v, o -> o.tables, (o, p) -> o.tables = p.tables).add()
                .appendInherited(new KeyedCodec<>("Rolls", new ArrayCodec<>(Roll.CODEC, Roll[]::new), false),
                        (o, v) -> o.rolls = v, o -> o.rolls, (o, p) -> o.rolls = p.rolls).add()
                .build();

        @Nonnull
        public static Loot of(@Nullable String[] tables, @Nullable Roll[] rolls) {
            Loot l = new Loot();
            l.tables = tables;
            l.rolls = rolls;
            return l;
        }

        /** Referenced {@link LootableAsset} ids (case-insensitive at resolve). */
        @Nullable
        public String[] getTables() {
            return tables;
        }

        /** Inline rolls authored directly on this station (in addition to any {@link #tables}). */
        @Nullable
        public Roll[] getRolls() {
            return rolls;
        }
    }

    /**
     * Camera pull while working. See {@code station/CLAUDE.md} for the FaceBlock hunt history.
     * {@code FaceBlockMode} is RENAMED {@code Recipe} this leg (design section 9.7; unreleased,
     * free rename per the design's own binding note - NO deprecated alias, straight rename, no
     * shipped JSON asset authors this key).
     */
    public static final class Camera {
        @Nullable protected String mode;
        @Nullable protected Boolean locked;
        @Nullable protected Boolean faceBlock;
        @Nullable protected String recipe;

        public static final BuilderCodec<Camera> CODEC = BuilderCodec.builder(Camera.class, Camera::new)
                .appendInherited(new KeyedCodec<>("Mode", Codec.STRING, false),
                        (o, v) -> o.mode = v, o -> o.mode, (o, p) -> o.mode = p.mode).add()
                .appendInherited(new KeyedCodec<>("Locked", Codec.BOOLEAN, false),
                        (o, v) -> o.locked = v, o -> o.locked, (o, p) -> o.locked = p.locked).add()
                .appendInherited(new KeyedCodec<>("FaceBlock", Codec.BOOLEAN, false),
                        (o, v) -> o.faceBlock = v, o -> o.faceBlock, (o, p) -> o.faceBlock = p.faceBlock).add()
                .appendInherited(new KeyedCodec<>("Recipe", Codec.STRING, false),
                        (o, v) -> o.recipe = v, o -> o.recipe,
                        (o, p) -> o.recipe = p.recipe).add()
                .build();

        @Nonnull
        public static Camera of(@Nullable String mode, @Nullable Boolean locked) {
            return of(mode, locked, null);
        }

        @Nonnull
        public static Camera of(@Nullable String mode, @Nullable Boolean locked, @Nullable Boolean faceBlock) {
            return of(mode, locked, faceBlock, null);
        }

        @Nonnull
        public static Camera of(@Nullable String mode, @Nullable Boolean locked, @Nullable Boolean faceBlock,
                @Nullable String recipe) {
            Camera c = new Camera();
            c.mode = mode;
            c.locked = locked;
            c.faceBlock = faceBlock;
            c.recipe = recipe;
            return c;
        }

        @Nullable
        public String getMode() {
            return mode;
        }

        @Nullable
        public Boolean getLocked() {
            return locked;
        }

        @Nullable
        public Boolean getFaceBlock() {
            return faceBlock;
        }

        /** The camera-preset recipe id ({@code StationCameraPreset}), design section 9.7's {@code Camera.Recipe}. */
        @Nullable
        public String getRecipe() {
            return recipe;
        }
    }

    /**
     * The work animation: a registered {@code EmoteAsset} id, plus the optional per-swing
     * cadence layer. The work emote must NOT loop client-side; the engine re-fires the clip
     * as a one-shot on every swing tick.
     */
    public static final class Animation {
        @Nullable protected String emoteId;
        @Nullable protected Swing swing;
        @Nullable protected String actionClip;

        public static final BuilderCodec<Animation> CODEC = BuilderCodec.builder(Animation.class, Animation::new)
                .appendInherited(new KeyedCodec<>("EmoteId", Codec.STRING, false),
                        (o, v) -> o.emoteId = v, o -> o.emoteId, (o, p) -> o.emoteId = p.emoteId).add()
                .appendInherited(new KeyedCodec<>("Swing", Swing.CODEC, false),
                        (o, v) -> o.swing = v, o -> o.swing, (o, p) -> o.swing = p.swing).add()
                .appendInherited(new KeyedCodec<>("ActionClip", Codec.STRING, false),
                        (o, v) -> o.actionClip = v, o -> o.actionClip, (o, p) -> o.actionClip = p.actionClip).add()
                .build();

        @Nonnull
        public static Animation of(@Nullable String emoteId) {
            return of(emoteId, null);
        }

        @Nonnull
        public static Animation of(@Nullable String emoteId, @Nullable Swing swing) {
            return of(emoteId, swing, null);
        }

        @Nonnull
        public static Animation of(@Nullable String emoteId, @Nullable Swing swing, @Nullable String actionClip) {
            Animation a = new Animation();
            a.emoteId = emoteId;
            a.swing = swing;
            a.actionClip = actionClip;
            return a;
        }

        @Nullable
        public String getEmoteId() {
            return emoteId;
        }

        @Nullable
        public Swing getSwing() {
            return swing;
        }

        /**
         * Optional Action-slot clip id override for a SEAT-MODE station's per-swing cue (the
         * seated-worker swing fix): fires on {@code AnimationSlot.Action} against the held
         * item's OWN {@code ItemPlayerAnimations} clip set instead of the {@link #emoteId} on
         * the {@code Emote} slot. Null/blank resolves to {@code StationHoldController
         * .DEFAULT_ACTION_CLIP} ({@code "Chop"}, the Hatchet family clip) at swing time.
         */
        @Nullable
        public String getActionClip() {
            return actionClip;
        }

        /**
         * Per-swing cadence + flair: a server-timed cue played at the block every
         * {@link #intervalMs} while WORKING. OMIT this group and the pre-round-2 behavior is
         * unchanged (no swing layer).
         */
        public static final class Swing {
            @Nullable protected Long intervalMs;
            @Nullable protected Presentation presentation;
            @Nullable protected Impact impact;

            public static final BuilderCodec<Swing> CODEC = BuilderCodec.builder(Swing.class, Swing::new)
                    .appendInherited(new KeyedCodec<>("IntervalMs", Codec.LONG, false),
                            (o, v) -> o.intervalMs = v, o -> o.intervalMs, (o, p) -> o.intervalMs = p.intervalMs).add()
                    .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                            (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation).add()
                    .appendInherited(new KeyedCodec<>("Impact", Impact.CODEC, false),
                            (o, v) -> o.impact = v, o -> o.impact, (o, p) -> o.impact = p.impact).add()
                    .build();

            @Nonnull
            public static Swing of(@Nullable Long intervalMs, @Nullable Presentation presentation) {
                return of(intervalMs, presentation, null);
            }

            @Nonnull
            public static Swing of(@Nullable Long intervalMs, @Nullable Presentation presentation,
                    @Nullable Impact impact) {
                Swing s = new Swing();
                s.intervalMs = intervalMs;
                s.presentation = presentation;
                s.impact = impact;
                return s;
            }

            @Nullable
            public Long getIntervalMs() {
                return intervalMs;
            }

            @Nullable
            public Presentation getPresentation() {
                return presentation;
            }

            @Nullable
            public Impact getImpact() {
                return impact;
            }

            /** The delayed impact cue. {@link #delayMs} null/nonpositive = fire with the swing. */
            public static final class Impact {
                @Nullable protected Long delayMs;
                @Nullable protected Presentation presentation;

                public static final BuilderCodec<Impact> CODEC = BuilderCodec.builder(Impact.class, Impact::new)
                        .appendInherited(new KeyedCodec<>("DelayMs", Codec.LONG, false),
                                (o, v) -> o.delayMs = v, o -> o.delayMs, (o, p) -> o.delayMs = p.delayMs).add()
                        .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                                (o, v) -> o.presentation = v, o -> o.presentation,
                                (o, p) -> o.presentation = p.presentation).add()
                        .build();

                @Nonnull
                public static Impact of(@Nullable Long delayMs, @Nullable Presentation presentation) {
                    Impact i = new Impact();
                    i.delayMs = delayMs;
                    i.presentation = presentation;
                    return i;
                }

                @Nullable
                public Long getDelayMs() {
                    return delayMs;
                }

                @Nullable
                public Presentation getPresentation() {
                    return presentation;
                }
            }
        }
    }

    /**
     * One NAMED cosmetic flair layer: a grantor (any run-a-command reward system) unlocks a
     * flair id for a player, and {@code StationFlairs.effective} overlays its non-null leaves
     * onto the station's base moment presentation, per LEAF, per MOMENT ID.
     *
     * <p><b>Leg F (design section 9.6):</b> the old fixed {@code Swing}/{@code Cycle}/
     * {@code RareFind}/{@code Completion} leaves are REPLACED by an open {@link #moments} map
     * keyed by an arbitrary STRING moment id (engine-emitted well-known ids
     * {@code cycle}/{@code swing}/{@code impact}/{@code rare_find}/{@code completion}, plus a
     * per-step {@code step:<actionId>:<stepId>} - see {@code station.StationFlairs}'s constants)
     * - unreleased, no back-compat alias, the same shape a standalone {@link FlairAsset} uses for
     * its own {@code Moments} leaf (shared vocabulary, one flair-content shape whether authored
     * inline or in a separate file).
     */
    public static final class Flair {
        @Nullable protected Map<String, Presentation> moments;

        public static final BuilderCodec<Flair> CODEC = BuilderCodec.builder(Flair.class, Flair::new)
                .appendInherited(new KeyedCodec<>("Moments",
                                new MapCodec<>(Presentation.CODEC, LinkedHashMap::new), false),
                        (o, v) -> o.moments = v, o -> o.moments, (o, p) -> o.moments = p.moments).add()
                .build();

        @Nonnull
        public static Flair of(@Nullable Map<String, Presentation> moments) {
            Flair f = new Flair();
            f.moments = moments;
            return f;
        }

        /** Moment id ({@code cycle}/{@code swing}/.../{@code step:<actionId>:<stepId>}) -> Presentation. */
        @Nullable
        public Map<String, Presentation> getMoments() {
            return moments;
        }
    }
}
