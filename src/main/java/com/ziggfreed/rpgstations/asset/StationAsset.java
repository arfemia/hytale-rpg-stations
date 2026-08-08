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
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditorSectionStart;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.common.codec.TagMatch;

/**
 * An interactive work station (diegetic work loop), loaded from a pack's
 * {@code Server/RpgStations/Stations/*.json}. {@link #requires} is this mod's own
 * {@link Requires} gate codec, and {@link #loot} is the conditional-lootable
 * {@code {Lootables, Rolls}} group over the shared {@link Roll} codec.
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
    /**
     * The conditional-lootable declaration - scope-2's unified {@link LootRef} ({@code Lootables}
     * + inline {@code Rolls}), replacing the old bespoke {@code Loot {Tables, Rolls}} group.
     */
    @Nullable private LootRef loot;
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
    /**
     * The puppet presentation route (round-4 design): mount the player, hide their body, and
     * spawn a skinned visual performing the work instead - see {@link Puppet}'s own javadoc for
     * the full knob family. Null = the classic in-body worker (this leg's default; opt-in).
     */
    @Nullable private Puppet puppet;
    /** Named cosmetic flair overrides, keyed by flair id. */
    @Nullable private Map<String, Flair> flairs;
    /**
     * The multi-output picker knob group (seam wave, decision 50); null = the default (locked
     * categories shown greyed). Whole-group overridable per {@link ActionDef}.
     */
    @Nullable private Picker picker;
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
            .documentation("Ignored - the station id comes from the asset filename, not this key. Kept as a schema field for editor display only.").add()
            .appendInherited(new KeyedCodec<>("Identity", Identity.CODEC, false),
                    (a, v) -> a.identity = v, a -> a.identity, (a, parent) -> a.identity = parent.identity)
            .documentation("Display name/description localization keys plus the icon item id, shown at the station's engage prompt and any station-listing UI.")
            .metadata(new UIEditorSectionStart("Identity")).add()
            .appendInherited(new KeyedCodec<>("Work", Work.CODEC, false),
                    (a, v) -> a.work = v, a -> a.work, (a, parent) -> a.work = parent.work)
            .documentation("The work-loop cadence, duration/exit bounds, per-cycle contributions, looping flag, and optional idle-practice mode.")
            .metadata(new UIEditorSectionStart("Work")).add()
            .appendInherited(new KeyedCodec<>("Recipe", Recipe.CODEC, false),
                    (a, v) -> a.recipe = v, a -> a.recipe, (a, parent) -> a.recipe = parent.recipe)
            .documentation("The Convert recipe: authored Conversions and/or a FromCrafting derivation from native recipes.")
            .metadata(new UIEditorSectionStart("Recipe")).add()
            .appendInherited(new KeyedCodec<>("Hold", Hold.CODEC, false),
                    (a, v) -> a.hold = v, a -> a.hold, (a, parent) -> a.hold = parent.hold)
            .documentation("The movement lock while working: the default self-effect hold, or the Mount knob family (seated/standing mount).")
            .metadata(new UIEditorSectionStart("Hold")).add()
            .appendInherited(new KeyedCodec<>("Tool", Tool.CODEC, false),
                    (a, v) -> a.tool = v, a -> a.tool, (a, parent) -> a.tool = parent.tool)
            .documentation("The held-tool gate (tag/gather/id match routes), tool-power contribution scaling, durability drain, and minimum-durability start gate.")
            .metadata(new UIEditorSectionStart("Tool")).add()
            .appendInherited(new KeyedCodec<>("Loot", LootRef.CODEC, false),
                    (a, v) -> a.loot = v, a -> a.loot, (a, parent) -> a.loot = parent.loot)
            .documentation("Conditional loot for the implicit action: a LootRef (referenced Lootables + inline Rolls).")
            .metadata(new UIEditorSectionStart("Loot")).add()
            .appendInherited(new KeyedCodec<>("Camera", Camera.CODEC, false),
                    (a, v) -> a.camera = v, a -> a.camera, (a, parent) -> a.camera = parent.camera)
            .documentation("The third-person camera pull while working, plus the optional fixed-look FaceBlock preset.")
            .metadata(new UIEditorSectionStart("Camera")).add()
            .appendInherited(new KeyedCodec<>("Animation", Animation.CODEC, false),
                    (a, v) -> a.animation = v, a -> a.animation, (a, parent) -> a.animation = parent.animation)
            .documentation("The work emote id plus the optional per-swing cadence/impact cue layer.")
            .metadata(new UIEditorSectionStart("Animation")).add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (a, v) -> a.presentation = v, a -> a.presentation, (a, parent) -> a.presentation = parent.presentation)
            .documentation("The CYCLE-complete presentation moment: sound/particles/animation cues fired at the block each finished cycle.")
            .metadata(new UIEditorSectionStart("Presentation")).add()
            .appendInherited(new KeyedCodec<>("Completion", Presentation.CODEC, false),
                    (a, v) -> a.completion = v, a -> a.completion, (a, parent) -> a.completion = parent.completion)
            .documentation("The SESSION-COMPLETION presentation moment, played on a non-silent stop with at least one completed cycle. Null = silent completion.")
            .metadata(new UIEditorSectionStart("Completion")).add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (a, v) -> a.requires = v, a -> a.requires, (a, parent) -> a.requires = parent.requires)
            .documentation("The start gate: an optional permission node plus factor Conditions, evaluated once at engage.")
            .metadata(new UIEditorSectionStart("Requires")).add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (a, v) -> a.custody = v, a -> a.custody, (a, parent) -> a.custody = parent.custody)
            .documentation("Session-scoped placed-input custody; null = the classic direct-inventory Consume/Produce flow.")
            .metadata(new UIEditorSectionStart("Custody")).add()
            .appendInherited(new KeyedCodec<>("Puppet", Puppet.CODEC, false),
                    (a, v) -> a.puppet = v, a -> a.puppet, (a, parent) -> a.puppet = parent.puppet)
            .documentation("The puppet presentation route: mount the player, hide their body, spawn a skinned visual performing the work. Null = the classic in-body worker.")
            .metadata(new UIEditorSectionStart("Puppet")).add()
            .appendInherited(new KeyedCodec<>("Flairs",
                            new MapCodec<>(Flair.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.flairs = v, a -> a.flairs, (a, parent) -> a.flairs = parent.flairs)
            .documentation("Named cosmetic flair overrides, keyed by flair id; each entry overlays its non-null Moments onto the base presentation when that flair is unlocked for the player.")
            .metadata(new UIEditorSectionStart("Flairs")).add()
            .appendInherited(new KeyedCodec<>("Actions",
                            new MapCodec<>(ActionDef.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.actions = v, a -> a.actions, (a, parent) -> a.actions = parent.actions)
            .documentation("Named, authored-order multi-action overrides; null/empty means the single implicit 'work' action built from this station's own groups.")
            .metadata(new UIEditorSectionStart("Actions")).add()
            .appendInherited(new KeyedCodec<>("Picker", Picker.CODEC, false),
                    (a, v) -> a.picker = v, a -> a.picker, (a, parent) -> a.picker = parent.picker)
            .documentation("The multi-output picker knob group (decision 50); default = locked categories shown greyed.")
            .metadata(new UIEditorSectionStart("Picker")).add()
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
            @Nullable Requires requires, @Nullable LootRef loot) {
        return of(id, identity, work, recipe, hold, tool, camera, animation, presentation, requires, loot, null);
    }

    /** Java-side construction path with the optional {@link Loot} AND {@link Flair} map override. */
    @Nonnull
    public static StationAsset of(@Nonnull String id, @Nullable Identity identity, @Nullable Work work,
            @Nullable Recipe recipe, @Nullable Hold hold, @Nullable Tool tool, @Nullable Camera camera,
            @Nullable Animation animation, @Nullable Presentation presentation,
            @Nullable Requires requires, @Nullable LootRef loot, @Nullable Map<String, Flair> flairs) {
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
            @Nullable Requires requires, @Nullable LootRef loot, @Nullable Map<String, Flair> flairs,
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

    /** The conditional-lootable declaration (scope-2 {@link LootRef}); null = none authored. */
    @Nullable
    public LootRef getLoot() {
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

    /** The puppet presentation route (round-4 design); null = the classic in-body worker. */
    @Nullable
    public Puppet getPuppet() {
        return puppet;
    }

    /** Named cosmetic flair overrides, keyed by flair id; null = none authored. */
    @Nullable
    public Map<String, Flair> getFlairs() {
        return flairs;
    }

    /** The multi-output picker knob group (decision 50); null = the default (locked categories shown greyed). */
    @Nullable
    public Picker getPicker() {
        return picker;
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

    /** Java-side test/fixture helper; not part of any codec fold. */
    @Nonnull
    public StationAsset withPuppet(@Nullable Puppet puppet) {
        this.puppet = puppet;
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
                        (o, v) -> o.nameKey = v, o -> o.nameKey, (o, p) -> o.nameKey = p.nameKey)
                .documentation("The localization key resolved client-side for the station's display name; null = no name shown.")
                .metadata(new UIEditor(new UIEditor.LocalizationKeyField("rpgstations.station.{assetId}.name", true))).add()
                .appendInherited(new KeyedCodec<>("DescKey", Codec.STRING, false),
                        (o, v) -> o.descKey = v, o -> o.descKey, (o, p) -> o.descKey = p.descKey)
                .documentation("The localization key resolved client-side for the station's description; null = no description shown.")
                .metadata(new UIEditor(new UIEditor.LocalizationKeyField("rpgstations.station.{assetId}.desc"))).add()
                .appendInherited(new KeyedCodec<>("Icon", Codec.STRING, false),
                        (o, v) -> o.icon = v, o -> o.icon, (o, p) -> o.icon = p.icon)
                .documentation("An item id whose icon represents this station in any station-listing UI (the ability-icon convention).")
                .metadata(new UIEditor(new UIEditor.Icon("{assetId}", 64, 64))).add()
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
        @Nullable protected Contribution[] perCycleContributions;
        @Nullable protected Idle idle;
        @Nullable protected Boolean looping;

        public static final BuilderCodec<Work> CODEC = BuilderCodec.builder(Work.class, Work::new)
                .appendInherited(new KeyedCodec<>("CycleMs", Codec.LONG, false),
                        (o, v) -> o.cycleMs = v, o -> o.cycleMs, (o, p) -> o.cycleMs = p.cycleMs)
                .documentation("Milliseconds per work cycle (one Convert transaction / one implicit-program run). Reader-defaults to 5000.")
                .addValidator(CodecWarnValidators.positive("Work.CycleMs should be positive; a station with a zero/negative cycle time may spin the work loop uncontrollably.")).add()
                .appendInherited(new KeyedCodec<>("MaxDurationMs", Codec.LONG, false),
                        (o, v) -> o.maxDurationMs = v, o -> o.maxDurationMs,
                        (o, p) -> o.maxDurationMs = p.maxDurationMs)
                .documentation("The hard session-duration cap in milliseconds; the heartbeat stops the session once elapsed. Reader-defaults to 600000 (10 minutes).")
                .addValidator(CodecWarnValidators.positive("Work.MaxDurationMs should be positive.")).add()
                .appendInherited(new KeyedCodec<>("MaxMoveMeters", Codec.DOUBLE, false),
                        (o, v) -> o.maxMoveMeters = v, o -> o.maxMoveMeters,
                        (o, p) -> o.maxMoveMeters = p.maxMoveMeters)
                .documentation("The walk-off EXIT trigger radius in meters from the engage position (not an anti-idle guard). Reader-defaults to 1.5.")
                .addValidator(CodecWarnValidators.positive("Work.MaxMoveMeters should be positive.")).add()
                .appendInherited(new KeyedCodec<>("Exclusive", Codec.BOOLEAN, false),
                        (o, v) -> o.exclusive = v, o -> o.exclusive, (o, p) -> o.exclusive = p.exclusive)
                .documentation("Whether the station block is exclusive to one worker at a time (a second player's engage is denied while occupied). Reader-defaults to true.").add()
                .appendInherited(new KeyedCodec<>("PerCycleContributions",
                                new ArrayCodec<>(Contribution.CODEC, Contribution[]::new), false),
                        (o, v) -> o.perCycleContributions = v, o -> o.perCycleContributions,
                        (o, p) -> o.perCycleContributions = p.perCycleContributions)
                .documentation("Amounts posted on every completed cycle, forwarded verbatim on the cycle-completed "
                        + "event; the engine never interprets a channel itself. SCALED: each Amount is multiplied by "
                        + "the resolved tool multiplier (Tool.PowerScale) and, on an idle cycle, pre-scaled by "
                        + "Work.Idle.Fraction. Contrast Roll.Grants.Contributions, which is one-shot and never "
                        + "scaled.").add()
                .appendInherited(new KeyedCodec<>("Idle", Idle.CODEC, false),
                        (o, v) -> o.idle = v, o -> o.idle, (o, p) -> o.idle = p.idle)
                .documentation("Opt-in no-material idle practice mode; null/absent Enabled = off (a NO_INPUTS start is denied).").add()
                .appendInherited(new KeyedCodec<>("Looping", Codec.BOOLEAN, false),
                        (o, v) -> o.looping = v, o -> o.looping, (o, p) -> o.looping = p.looping)
                .documentation("Does the program (implicit or authored Steps) re-run every CycleMs? Default true (the classic loop); false completes the whole session after one run (the ritual shape).").add()
                .build();

        @Nonnull
        public static Work of(@Nullable Long cycleMs, @Nullable Long maxDurationMs,
                @Nullable Double maxMoveMeters, @Nullable Boolean exclusive,
                @Nullable Contribution[] perCycleContributions) {
            return of(cycleMs, maxDurationMs, maxMoveMeters, exclusive, perCycleContributions, null);
        }

        @Nonnull
        public static Work of(@Nullable Long cycleMs, @Nullable Long maxDurationMs,
                @Nullable Double maxMoveMeters, @Nullable Boolean exclusive,
                @Nullable Contribution[] perCycleContributions, @Nullable Idle idle) {
            Work w = new Work();
            w.cycleMs = cycleMs;
            w.maxDurationMs = maxDurationMs;
            w.maxMoveMeters = maxMoveMeters;
            w.exclusive = exclusive;
            w.perCycleContributions = perCycleContributions;
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

        /**
         * The per-cycle {@link Contribution} entries, posted on EVERY completed cycle and SCALED
         * (tool multiplier, plus the idle fraction on an idle cycle) - see {@link Contribution}'s
         * own javadoc for the site-fixed scaling contract.
         */
        @Nullable
        public Contribution[] getPerCycleContributions() {
            return perCycleContributions;
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
         *
         * <p>Named {@code Looping} (the native boolean spelling for "this repeats forever") rather
         * than {@code Repeat}, which the engine reserves for an iteration COUNT - the meaning
         * {@code StationStep.Repeat} carries one nesting level down.
         */
        @Nullable
        public Boolean getLooping() {
            return looping;
        }

        /** {@link #looping}, reader-defaulted to {@code true} (the classic loop) when null. */
        public boolean effectiveLooping() {
            return looping == null || looping;
        }

        /**
         * Opt-in no-material idle practice: when the station has no runnable conversion AND
         * {@link #enabled}, the session keeps cycling and forwards a small FRACTION of the
         * authored per-cycle contributions instead of stopping (no conversion, no loot). Default
         * OFF. {@link #cycleMs} reader-defaults to 3x the effective {@code Work.CycleMs}, floored
         * at 2x it; {@link #fraction} reader-defaults to 0.1, clamped to {@code [0, 1]}.
         */
        public static final class Idle {
            @Nullable protected Boolean enabled;
            @Nullable protected Long cycleMs;
            @Nullable protected Double fraction;

            public static final BuilderCodec<Idle> CODEC = BuilderCodec.builder(Idle.class, Idle::new)
                    .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                            (o, v) -> o.enabled = v, o -> o.enabled, (o, p) -> o.enabled = p.enabled)
                    .documentation("Opts into no-material idle practice when no conversion is runnable. Default false (a NO_INPUTS start denies).").add()
                    .appendInherited(new KeyedCodec<>("CycleMs", Codec.LONG, false),
                            (o, v) -> o.cycleMs = v, o -> o.cycleMs, (o, p) -> o.cycleMs = p.cycleMs)
                    .documentation("Milliseconds per idle cycle. Reader-defaults to 3x the effective Work.CycleMs, floored at 2x it.")
                    .addValidator(CodecWarnValidators.positive("Work.Idle.CycleMs should be positive.")).add()
                    .appendInherited(new KeyedCodec<>("Fraction", Codec.DOUBLE, false),
                            (o, v) -> o.fraction = v, o -> o.fraction, (o, p) -> o.fraction = p.fraction)
                    .documentation("The fraction of a normal cycle's Work.PerCycleContributions amounts an idle cycle "
                            + "posts (tool multiplier forced to 1.0, no conversion/loot). Reader-defaults to 0.1, "
                            + "clamped to [0,1].")
                    .addValidator(CodecWarnValidators.nonNegative("Work.Idle.Fraction should not be negative.")).add()
                    .build();

            @Nonnull
            public static Idle of(@Nullable Boolean enabled, @Nullable Long cycleMs, @Nullable Double fraction) {
                Idle i = new Idle();
                i.enabled = enabled;
                i.cycleMs = cycleMs;
                i.fraction = fraction;
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

            /**
             * The fraction of a normal cycle's {@code Work.PerCycleContributions} amounts an idle
             * cycle posts; reader-defaulted to 0.1 and clamped to {@code [0, 1]} engine-side.
             */
            @Nullable
            public Double getFraction() {
                return fraction;
            }
        }
    }

    /**
     * The Convert recipe. Its EFFECTIVE conversions ({@code StationCatalog.resolvedConversions})
     * are authored {@link #conversions} FIRST, then any {@link FromCrafting}-derived conversions.
     */
    public static final class Recipe {
        @Nullable protected Conversion[] conversions;
        @Nullable protected FromCrafting fromCrafting;
        @Nullable protected Yield yield;

        public static final BuilderCodec<Recipe> CODEC = BuilderCodec.builder(Recipe.class, Recipe::new)
                .appendInherited(new KeyedCodec<>("Conversions",
                                new ArrayCodec<>(Conversion.CODEC, Conversion[]::new), false),
                        (o, v) -> o.conversions = v, o -> o.conversions,
                        (o, p) -> o.conversions = p.conversions)
                .documentation("Hand-authored input-to-output conversions, evaluated FIRST (before any FromCrafting-derived ones).").add()
                .appendInherited(new KeyedCodec<>("FromCrafting", FromCrafting.CODEC, false),
                        (o, v) -> o.fromCrafting = v, o -> o.fromCrafting,
                        (o, p) -> o.fromCrafting = p.fromCrafting)
                .documentation("Derive additional Conversions from the engine's own native crafting/processing recipes; null = no derivation.").add()
                .appendInherited(new KeyedCodec<>("Yield", Yield.CODEC, false),
                        (o, v) -> o.yield = v, o -> o.yield, (o, p) -> o.yield = p.yield)
                .documentation("Per-cycle output-quantity transform applied to whichever conversion runs (authored or derived); null = each conversion's own authored quantity, unchanged.").add()
                .build();

        @Nonnull
        public static Recipe of(@Nullable Conversion[] conversions) {
            return of(conversions, null);
        }

        @Nonnull
        public static Recipe of(@Nullable Conversion[] conversions, @Nullable FromCrafting fromCrafting) {
            return of(conversions, fromCrafting, null);
        }

        @Nonnull
        public static Recipe of(@Nullable Conversion[] conversions, @Nullable FromCrafting fromCrafting,
                @Nullable Yield yield) {
            Recipe r = new Recipe();
            r.conversions = conversions;
            r.fromCrafting = fromCrafting;
            r.yield = yield;
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

        /** The per-cycle output-quantity transform; null = no transform (authored quantities stand). */
        @Nullable
        public Yield getYield() {
            return yield;
        }
    }

    /**
     * The per-cycle OUTPUT-QUANTITY transform, applied to whichever conversion a real cycle runs -
     * authored {@code Conversions} and {@code FromCrafting}-derived ones alike, which is exactly why
     * it sits on {@link Recipe} rather than inside {@link FromCrafting}: what a station YIELDS is a
     * property of the recipe, not of how that recipe was discovered.
     *
     * <p><b>Resolution order</b> (all four leaves independent and composable, no mode):
     * {@code Base} (or the conversion's own authored quantity when {@code Base} is absent) is scaled
     * by {@code Scale}, then {@code Bonus}'s reached floor adds its {@code Add}, then the result
     * is clamped into {@code [Min, Max]}. A floor of 1 output is ALWAYS enforced underneath - a
     * conversion that consumed its inputs and produced nothing is item loss, never a balance choice.
     *
     * <p><b>FRACTIONAL yields are real, and land as a STOCHASTIC REMAINDER.</b> {@code Scale} and a
     * floor's {@code Add} are both doubles, so an effective yield of {@code 2.5} is authorable and
     * means "two items every cycle, plus a third on a 50% roll" - the whole part is paid out always
     * and only the remainder is rolled, so the long-run average is exactly the authored number.
     * This exists because whole-number-only yields force every tool tier onto a shared step: a
     * mid-ladder tool that should sit BETWEEN two yields would otherwise have to be rounded onto one
     * of its neighbours, collapsing a rung. The roll is stateless by design (no per-session carry to
     * persist, lose on a crash, or reason about across a relog), and it draws from the same RNG seam
     * the loot rolls use, injected so the decision core stays pure.
     *
     * <p><b>{@code Bonus} is where the tool ladder lives.</b> It is the same weighted-factor
     * vocabulary a loot {@code Roll.Ladder} uses ({@code Values} is a {@link FactorRef} array summed
     * as {@code sum(resolve(Factor,Param) * Weight)}, then looked up against {@code Floors} by
     * descending {@code Min}), so a yield bonus can key off ANY registered factor - the built-in
     * {@code hytale:tool_power} plus {@code hytale:tool_quality} /
     * {@code hytale:tool_item_level} for a "better tools yield
     * more" curve, a {@code stat} channel, or a fourth party's own {@code yourmod:} factor. Summing
     * power AND quality is the intended shape for a tool ladder: gather power saturates across a
     * family's upper tiers while quality keeps separating them, so neither alone ranks a full ladder.
     */
    public static final class Yield {
        @Nullable protected Integer base;
        @Nullable protected Double scale;
        @Nullable protected Bonus bonus;
        @Nullable protected Integer min;
        @Nullable protected Integer max;

        /** The absolute floor on a produced quantity - see this class's javadoc (item-loss guard, not a knob). */
        public static final int ABSOLUTE_MIN = 1;

        public static final BuilderCodec<Yield> CODEC = BuilderCodec.builder(Yield.class, Yield::new)
                .appendInherited(new KeyedCodec<>("Base", Codec.INTEGER, false),
                        (o, v) -> o.base = v, o -> o.base, (o, p) -> o.base = p.base)
                .documentation("Flat output quantity per conversion BEFORE Scale/Bonus; null = use each conversion's own authored output quantity.").add()
                .appendInherited(new KeyedCodec<>("Scale", Codec.DOUBLE, false),
                        (o, v) -> o.scale = v, o -> o.scale, (o, p) -> o.scale = p.scale)
                .documentation("Multiplier on the base quantity, rounded to the nearest whole item; reader-defaults to 1.0 (no scaling).").add()
                .appendInherited(new KeyedCodec<>("Bonus", Bonus.CODEC, false),
                        (o, v) -> o.bonus = v, o -> o.bonus, (o, p) -> o.bonus = p.bonus)
                .documentation("Optional factor-driven bonus ladder adding extra output (e.g. a better tool yields more); null = no bonus.").add()
                .appendInherited(new KeyedCodec<>("Min", Codec.INTEGER, false),
                        (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
                .documentation("Lower clamp on the final quantity; null = the engine's own 1-item floor.").add()
                .appendInherited(new KeyedCodec<>("Max", Codec.INTEGER, false),
                        (o, v) -> o.max = v, o -> o.max, (o, p) -> o.max = p.max)
                .documentation("Upper clamp on the final quantity; null = uncapped.").add()
                .build();

        @Nonnull
        public static Yield of(@Nullable Integer base, @Nullable Double scale, @Nullable Bonus bonus,
                @Nullable Integer min, @Nullable Integer max) {
            Yield y = new Yield();
            y.base = base;
            y.scale = scale;
            y.bonus = bonus;
            y.min = min;
            y.max = max;
            return y;
        }

        /** Flat per-conversion base quantity; null = defer to the conversion's own authored quantity. */
        @Nullable
        public Integer getBase() {
            return base;
        }

        /** Multiplier on the base quantity; null = the neutral {@code 1.0}. */
        @Nullable
        public Double getScale() {
            return scale;
        }

        /** Reader-defaulted {@link #getScale()} (neutral 1.0 when absent or non-finite/non-positive). */
        public double effectiveScale() {
            return scale != null && Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
        }

        /** The factor-driven bonus ladder; null = no bonus. */
        @Nullable
        public Bonus getBonus() {
            return bonus;
        }

        @Nullable
        public Integer getMin() {
            return min;
        }

        @Nullable
        public Integer getMax() {
            return max;
        }

        /**
         * The factor-driven yield bonus: sum {@code Values} (a weighted {@link FactorRef} sum, the
         * SAME composition every other factor site uses), then add the reached {@code Floors} entry's
         * {@code Add}. Deliberately the {@code Roll.Ladder} shape rather than a new one - a yield
         * bonus and a loot ladder ask the identical question ("where on this curve is the player") and
         * must not drift into two vocabularies an author has to learn twice.
         */
        public static final class Bonus {
            @Nullable protected FactorRef[] values;
            @Nullable protected Floor[] floors;

            public static final BuilderCodec<Bonus> CODEC = BuilderCodec.builder(Bonus.class, Bonus::new)
                    .appendInherited(new KeyedCodec<>("Values",
                                    new ArrayCodec<>(FactorRef.CODEC, FactorRef[]::new), false),
                            (o, v) -> o.values = v, o -> o.values, (o, p) -> o.values = p.values)
                    .documentation("Weighted factor references summed into the ladder value (e.g. tool power plus tool quality); empty = a constant 0.").add()
                    .appendInherited(new KeyedCodec<>("Floors",
                                    new ArrayCodec<>(Floor.CODEC, Floor[]::new), false),
                            (o, v) -> o.floors = v, o -> o.floors, (o, p) -> o.floors = p.floors)
                    .documentation("Thresholds over the summed value; the HIGHEST floor whose Min is reached contributes its Add. Empty = no bonus.").add()
                    .build();

            @Nonnull
            public static Bonus of(@Nullable FactorRef[] values, @Nullable Floor[] floors) {
                Bonus b = new Bonus();
                b.values = values;
                b.floors = floors;
                return b;
            }

            @Nullable
            public FactorRef[] getValues() {
                return values;
            }

            @Nullable
            public Floor[] getFloors() {
                return floors;
            }
        }

        /**
         * One yield-bonus threshold: at or above {@code Min} on the summed value, add {@code Add}
         * items. {@code Add} is FRACTIONAL on purpose - see {@link Yield}'s own javadoc for how a
         * remainder is paid out, which is what lets a mid-tier tool sit genuinely between two whole
         * yields instead of being rounded onto one of its neighbours.
         */
        public static final class Floor {
            @Nullable protected Double min;
            @Nullable protected Double add;

            public static final BuilderCodec<Floor> CODEC = BuilderCodec.builder(Floor.class, Floor::new)
                    .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                            (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
                    .documentation("The summed factor value at or above which this floor applies; reader-defaults to 0.").add()
                    .appendInherited(new KeyedCodec<>("Add", Codec.DOUBLE, false),
                            (o, v) -> o.add = v, o -> o.add, (o, p) -> o.add = p.add)
                    .documentation("Extra items added when this floor is the reached one; may be fractional (2.5 = two always plus a 50% chance of a third). Reader-defaults to 0.").add()
                    .build();

            @Nonnull
            public static Floor of(@Nullable Double min, @Nullable Double add) {
                Floor f = new Floor();
                f.min = min;
                f.add = add;
                return f;
            }

            /** Reader-defaulted threshold (0 when absent). */
            public double effectiveMin() {
                return min != null && Double.isFinite(min) ? min : 0.0;
            }

            /** Reader-defaulted addend (0 when absent or non-finite). */
            public double effectiveAdd() {
                return add != null && Double.isFinite(add) ? add : 0.0;
            }

            @Nullable
            public Double getMin() {
                return min;
            }

            @Nullable
            public Double getAdd() {
                return add;
            }
        }
    }

    /**
     * Derive-from-native rule (seam wave decision 51c/52, native-recipe composition): Conversions are
     * derived from the engine's OWN recipe truth by reference. A recipe is selected when it scopes to
     * one of this station's {@link #benches} (native {@code BenchRequirement} id match) AND its
     * category intersects {@link #categories} AND its declared {@link #types} include the recipe's
     * kind; a derived conversion carries the native recipe's own one-per-craft output, and
     * {@link Recipe#getYield()} is where a station retunes that yield.
     *
     * <p><b>Native-time pacing ({@link #nativeTime}, decision 52):</b> native recipes drive WHAT is
     * produced, but a STATION owns the PACE - vanilla planks craft instantly, the sawmill's slower
     * diegetic loop IS the value. {@link NativeTime} is the LINEAR transform (y = m*x + b:
     * {@code Scale}*x + {@code OffsetMs}) over a derived recipe's own {@code TimeSeconds}, with
     * defaults intended to keep stations MEANINGFULLY SLOWER than vanilla.
     *
     * <p><b>Per-cycle time resolution precedence</b> (documented here, applied engine-side):
     * an authored {@code Conversion.DurationMs} &gt; this {@link NativeTime} linear transform over the
     * recipe's {@code TimeSeconds} &gt; the station-level {@code Work.CycleMs} fallback.
     */
    public static final class FromCrafting {
        /** The two recipe kinds a station may derive (decision 51c). */
        public static final String TYPE_CRAFTING = "Crafting";
        public static final String TYPE_PROCESSING = "Processing";

        @Nullable protected String[] categories;
        @Nullable protected String[] benches;
        @Nullable protected String[] types;
        @Nullable protected NativeTime nativeTime;

        public static final BuilderCodec<FromCrafting> CODEC = BuilderCodec.builder(FromCrafting.class, FromCrafting::new)
                .appendInherited(new KeyedCodec<>("Categories", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.categories = v, o -> o.categories, (o, p) -> o.categories = p.categories)
                .documentation("Native recipe categories to derive Conversions from (e.g. 'WoodPlanks'); a pack-added category in the set just works.").add()
                .appendInherited(new KeyedCodec<>("Benches", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.benches = v, o -> o.benches, (o, p) -> o.benches = p.benches)
                .documentation("Native BenchRequirement bench ids this station's recipes scope to (id-ref-only string match).").add()
                .appendInherited(new KeyedCodec<>("Types", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.types = v, o -> o.types, (o, p) -> o.types = p.types)
                .documentation("The recipe kinds to derive: 'Crafting' and/or 'Processing'; absent = both.").add()
                .appendInherited(new KeyedCodec<>("NativeTime", NativeTime.CODEC, false),
                        (o, v) -> o.nativeTime = v, o -> o.nativeTime, (o, p) -> o.nativeTime = p.nativeTime)
                .documentation("Linear transform (Scale*TimeSeconds + OffsetMs) over each derived recipe's native time; defaults stay slower than vanilla.").add()
                .build();

        @Nonnull
        public static FromCrafting of(@Nullable String[] categories) {
            return of(categories, null, null, null);
        }

        @Nonnull
        public static FromCrafting of(@Nullable String[] categories,
                @Nullable String[] benches, @Nullable String[] types, @Nullable NativeTime nativeTime) {
            FromCrafting f = new FromCrafting();
            f.categories = categories;
            f.benches = benches;
            f.types = types;
            f.nativeTime = nativeTime;
            return f;
        }

        @Nullable
        public String[] getCategories() {
            return categories;
        }

        /** Native BenchRequirement bench ids this station's derived recipes scope to (id-ref-only); null = none. */
        @Nullable
        public String[] getBenches() {
            return benches;
        }

        /** The recipe kinds to derive ({@code Crafting}/{@code Processing}); null/empty = both. */
        @Nullable
        public String[] getTypes() {
            return types;
        }

        /** The linear native-time pacing transform; null = no native-time pacing (fall to Work.CycleMs). */
        @Nullable
        public NativeTime getNativeTime() {
            return nativeTime;
        }

        /**
         * The linear native-time pacing transform (decision 52, y = m*x + b): a derived recipe's
         * effective per-cycle time = {@code Scale * (recipe TimeSeconds in ms) + OffsetMs}.
         * {@link #scale} is the multiplier (m); {@link #offsetMs} is the additive floor (b) in
         * milliseconds. The DEFAULTS deliberately keep a station slower than vanilla's instant craft:
         * a null {@link #scale} reader-defaults to {@link #DEFAULT_SCALE} and a null {@link #offsetMs}
         * to {@link #DEFAULT_OFFSET_MS}, so even an empty {@code NativeTime: {}} group stretches native
         * time rather than leaving it instant. Overall precedence (engine-side): an authored
         * {@code Conversion.DurationMs} &gt; this transform &gt; {@code Work.CycleMs}.
         */
        public static final class NativeTime {
            /** Default multiplier when {@link #scale} is omitted (stretches native time; slower than vanilla). */
            public static final double DEFAULT_SCALE = 1.0;
            /** Default additive floor in ms when {@link #offsetMs} is omitted (a non-instant diegetic beat). */
            public static final long DEFAULT_OFFSET_MS = 2000L;

            @Nullable protected Double scale;
            @Nullable protected Long offsetMs;

            public static final BuilderCodec<NativeTime> CODEC = BuilderCodec.builder(NativeTime.class, NativeTime::new)
                    .appendInherited(new KeyedCodec<>("Scale", Codec.DOUBLE, false),
                            (o, v) -> o.scale = v, o -> o.scale, (o, p) -> o.scale = p.scale)
                    .documentation("Multiplier (m) on the recipe's native time; reader-defaults to 1.0 (stretch, never speed up below vanilla).").add()
                    .appendInherited(new KeyedCodec<>("OffsetMs", Codec.LONG, false),
                            (o, v) -> o.offsetMs = v, o -> o.offsetMs, (o, p) -> o.offsetMs = p.offsetMs)
                    .documentation("Additive floor (b) in milliseconds added after scaling; reader-defaults to 2000 so a station is never instant like vanilla.").add()
                    .build();

            @Nonnull
            public static NativeTime of(@Nullable Double scale, @Nullable Long offsetMs) {
                NativeTime n = new NativeTime();
                n.scale = scale;
                n.offsetMs = offsetMs;
                return n;
            }

            @Nullable
            public Double getScale() {
                return scale;
            }

            @Nullable
            public Long getOffsetMs() {
                return offsetMs;
            }

            /** {@link #scale}, reader-defaulted to {@link #DEFAULT_SCALE} when null/non-positive. */
            public double effectiveScale() {
                return scale != null && scale > 0 ? scale : DEFAULT_SCALE;
            }

            /** {@link #offsetMs}, reader-defaulted to {@link #DEFAULT_OFFSET_MS} when null/negative. */
            public long effectiveOffsetMs() {
                return offsetMs != null && offsetMs >= 0 ? offsetMs : DEFAULT_OFFSET_MS;
            }
        }
    }

    /**
     * One native-shaped input-to-output conversion, both sides an {@link Ingredient} ARRAY matching
     * vanilla {@code CraftingRecipe.Input}/{@code Output}: an {@code Input} entry is exactly one of
     * {@code ItemId}/{@code ResourceTypeId}, an {@code Output} entry is always an exact
     * {@code ItemId}. Multi-input ("2 planks + 1 nail -&gt; 1 crate") and multi-output (a main
     * product plus a byproduct) both author directly, and {@code StationRecipeDeriver} derives
     * multi-input native recipes rather than skipping them.
     *
     * <p>The conversion is ALL-OR-NOTHING per cycle: the runnable check requires every input
     * available AND room for every output before a cycle starts.
     *
     * <p><b>{@link #durationMs} (seam wave decision 52):</b> the OPTIONAL per-conversion authored
     * pace override in milliseconds - the HIGHEST-precedence time source. Per-cycle time resolution
     * precedence: an authored {@code DurationMs} &gt; a {@code FromCrafting.NativeTime} linear
     * transform over a derived recipe's {@code TimeSeconds} &gt; the station-level {@code Work.CycleMs}.
     * Null = this conversion has no authored pace (fall to the next precedence tier).
     *
     * <p><b>{@link #category} (selection wave, decision 56):</b> the OPTIONAL source-category tag a
     * multi-output station groups conversions by for the sneak+F output PICKER. The
     * {@code StationRecipeDeriver} STAMPS each derived conversion with its native source category
     * (the matched recipe category, else the matched bench id when the recipe carries no category);
     * a hand-authored conversion MAY author it directly. Null = untagged (the conversion belongs to
     * no named category, so it is only ever produced by the unfiltered - no picker selection - path).
     */
    public static final class Conversion {
        @Nullable protected Ingredient[] input;
        @Nullable protected Ingredient[] output;
        @Nullable protected Long durationMs;
        @Nullable protected String category;

        public static final BuilderCodec<Conversion> CODEC = BuilderCodec.builder(Conversion.class, Conversion::new)
                .appendInherited(new KeyedCodec<>("Input", new ArrayCodec<>(Ingredient.CODEC, Ingredient[]::new), false),
                        (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input)
                .documentation("The conversion inputs (native CraftingRecipe.Input shape): each an Ingredient, exactly one of ItemId | ResourceTypeId. Every entry must be available for the cycle to run.").add()
                .appendInherited(new KeyedCodec<>("Output", new ArrayCodec<>(Ingredient.CODEC, Ingredient[]::new), false),
                        (o, v) -> o.output = v, o -> o.output, (o, p) -> o.output = p.output)
                .documentation("The conversion outputs (native CraftingRecipe.Output shape): each an exact-ItemId Ingredient. Every entry needs inventory room for the cycle to run.").add()
                .appendInherited(new KeyedCodec<>("DurationMs", Codec.LONG, false),
                        (o, v) -> o.durationMs = v, o -> o.durationMs, (o, p) -> o.durationMs = p.durationMs)
                .documentation("Optional per-conversion pace override in ms; highest precedence (> NativeTime > Work.CycleMs). Null = none.").add()
                .appendInherited(new KeyedCodec<>("Category", Codec.STRING, false),
                        (o, v) -> o.category = v, o -> o.category, (o, p) -> o.category = p.category)
                .documentation("Optional source-category tag the multi-output picker groups by; the deriver stamps it from the matched native recipe category (else bench id). Null = untagged.").add()
                .build();

        /** Convenience for the classic one-in/one-out conversion. */
        @Nonnull
        public static Conversion of(@Nullable Ingredient input, @Nullable Ingredient output) {
            return of(input, output, null);
        }

        /** Convenience for the classic one-in/one-out conversion, with a pace override. */
        @Nonnull
        public static Conversion of(@Nullable Ingredient input, @Nullable Ingredient output,
                @Nullable Long durationMs) {
            return of(input, output, durationMs, null);
        }

        /** Convenience for the classic one-in/one-out conversion, with a pace override + category. */
        @Nonnull
        public static Conversion of(@Nullable Ingredient input, @Nullable Ingredient output,
                @Nullable Long durationMs, @Nullable String category) {
            return of(input == null ? null : new Ingredient[] {input},
                    output == null ? null : new Ingredient[] {output}, durationMs, category);
        }

        @Nonnull
        public static Conversion of(@Nullable Ingredient[] input, @Nullable Ingredient[] output,
                @Nullable Long durationMs, @Nullable String category) {
            Conversion c = new Conversion();
            c.input = input;
            c.output = output;
            c.durationMs = durationMs;
            c.category = category;
            return c;
        }

        /** Every input this conversion consumes per cycle; null/empty = an incomplete conversion (skipped). */
        @Nullable
        public Ingredient[] getInput() {
            return input;
        }

        /** Every output this conversion yields per cycle; null/empty = an incomplete conversion (skipped). */
        @Nullable
        public Ingredient[] getOutput() {
            return output;
        }

        /**
         * The FIRST input, the one a single-material read (picker preview, custody acceptance,
         * validator label) speaks about; null when none is authored. A multi-input conversion still
         * consumes every entry - this is a display/matching convenience, never the consume path.
         */
        @Nullable
        public Ingredient primaryInput() {
            return input != null && input.length > 0 ? input[0] : null;
        }

        /**
         * The FIRST output, the one a single-item read (picker tab icon/cost line, bonus-copy
         * source, validator label) speaks about; null when none is authored.
         */
        @Nullable
        public Ingredient primaryOutput() {
            return output != null && output.length > 0 ? output[0] : null;
        }

        /** True when both sides carry at least one entry (an incomplete conversion never runs). */
        public boolean isComplete() {
            return input != null && input.length > 0 && output != null && output.length > 0;
        }

        /** The optional per-conversion pace override in ms (highest time precedence); null = none. */
        @Nullable
        public Long getDurationMs() {
            return durationMs;
        }

        /**
         * The optional source-category tag (selection wave, decision 56) the multi-output picker
         * groups conversions by; stamped by {@code StationRecipeDeriver} on derived conversions, or
         * hand-authored. Null = untagged.
         */
        @Nullable
        public String getCategory() {
            return category;
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
                        (o, p) -> o.movementLock = p.movementLock)
                .documentation("Whether the movement-lock effect applies while working. Reader-defaults to true. Ignored while a Block-surface Mount is active (the mount itself is the lock).").add()
                .appendInherited(new KeyedCodec<>("EffectId", Codec.STRING, false),
                        (o, v) -> o.effectId = v, o -> o.effectId, (o, p) -> o.effectId = p.effectId)
                .documentation("The native EntityEffect asset id re-applied every heartbeat under decay-as-release. Reader-defaults to 'RPG_Station_Hold'. A bare id, never a timed EffectRef - the hold's lifetime is engine-owned.").add()
                .appendInherited(new KeyedCodec<>("InterruptOnDamage", Codec.BOOLEAN, false),
                        (o, v) -> o.interruptOnDamage = v, o -> o.interruptOnDamage,
                        (o, p) -> o.interruptOnDamage = p.interruptOnDamage)
                .documentation("Whether taking damage stops the session. Reader-defaults to true.").add()
                .appendInherited(new KeyedCodec<>("Mount", Mount.CODEC, false),
                        (o, v) -> o.mount = v, o -> o.mount, (o, p) -> o.mount = p.mount)
                .documentation("The alternate mount-based hold strategy (Block seat or Entity standing mount); null = the default self-effect hold above.").add()
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

        /**
         * The native {@code EntityEffect} asset id the movement hold applies. Deliberately a BARE
         * id, NOT the shared {@link EffectRef} {@code {Id, DurationMs?}} group every other effect
         * site uses: the hold's lifetime is ENGINE-OWNED, not authorable. It is re-applied on a
         * short fixed TTL every session heartbeat under an overwrite policy
         * ({@code station.StationHoldController}), so the hold decays on its own within seconds if
         * a teardown is ever missed. An authored per-station duration here would either be ignored
         * (a schema lie) or would defeat that decay-as-release safety net, so the leaf stays an id.
         */
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
                            (o, v) -> o.surface = v, o -> o.surface, (o, p) -> o.surface = p.surface)
                    .documentation("The mount mechanism union discriminator: 'Block' (native seat mount, default) or 'Entity' (standing work mount). Unrecognized values fall to Block with a validator warn.")
                    .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:mount-surface"))).add()
                    .appendInherited(new KeyedCodec<>("Entity", Entity.CODEC, false),
                            (o, v) -> o.entity = v, o -> o.entity, (o, p) -> o.entity = p.entity)
                    .documentation("The standing work mount's sub-knobs; read only when Surface is 'Entity' (a validator warns if authored under 'Block').").add()
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
                @Nullable protected Vec3 offset;
                @Nullable protected Boolean dismountOnMove;
                @Nullable protected Boolean steerable;
                @Nullable protected String visibleAnchorItemId;

                public static final BuilderCodec<Entity> CODEC = BuilderCodec.builder(Entity.class, Entity::new)
                        .appendInherited(new KeyedCodec<>("Offset", Vec3.CODEC, false),
                                (o, v) -> o.offset = v, o -> o.offset, (o, p) -> o.offset = p.offset)
                        .documentation("The mount attachment offset from the anchor entity, in blocks (X right, Y up, Z forward).").add()
                        .appendInherited(new KeyedCodec<>("DismountOnMove", Codec.BOOLEAN, false),
                                (o, v) -> o.dismountOnMove = v, o -> o.dismountOnMove,
                                (o, p) -> o.dismountOnMove = p.dismountOnMove)
                        .documentation("Whether a heartbeat walk-off check dismounts the player (no native auto-dismount for this route). Reader-defaults to true; false = hard-lock until crouch/re-press.").add()
                        .appendInherited(new KeyedCodec<>("Steerable", Codec.BOOLEAN, false),
                                (o, v) -> o.steerable = v, o -> o.steerable,
                                (o, p) -> o.steerable = p.steerable)
                        .documentation("Whether the anchor may be WASD-steered by the mounted player. Reader-defaults to false (a per-heartbeat snap-back defeats native steering); true is validator-flagged as untested.").add()
                        .appendInherited(new KeyedCodec<>("VisibleAnchorItemId", Codec.STRING, false),
                                (o, v) -> o.visibleAnchorItemId = v, o -> o.visibleAnchorItemId,
                                (o, p) -> o.visibleAnchorItemId = p.visibleAnchorItemId)
                        .documentation("Diagnostic/authoring aid: an item id the invisible anchor renders as a dropped-item-style prop, so its position is visible in-game. Null = no visual (the shipped default).").add()
                        .build();

                @Nonnull
                public static Entity of(@Nullable Vec3 offset, @Nullable Boolean dismountOnMove,
                        @Nullable Boolean steerable) {
                    return of(offset, dismountOnMove, steerable, null);
                }

                @Nonnull
                public static Entity of(@Nullable Vec3 offset, @Nullable Boolean dismountOnMove,
                        @Nullable Boolean steerable, @Nullable String visibleAnchorItemId) {
                    Entity e = new Entity();
                    e.offset = offset;
                    e.dismountOnMove = dismountOnMove;
                    e.steerable = steerable;
                    e.visibleAnchorItemId = visibleAnchorItemId;
                    return e;
                }

                /**
                 * The attachment-offset knob, as the shared {@link Vec3} group. The
                 * {@code MountedComponent} entity-mount constructor's matching parameter is a
                 * {@code Rotation3f}, NOT a {@code Vector3f}, despite reading like a plain
                 * positional offset (a native mislabeling - {@code attachmentOffset} is used as a
                 * spatial XYZ offset for entity mounts, never as an actual rotation). The
                 * conversion happens explicitly at the ONE ECS call site,
                 * {@code station.StationEntityMountController.attach} - see that class's javadoc.
                 */
                @Nullable
                public Vec3 getOffset() {
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
                 * DIAGNOSTIC/authoring aid (decision 62's confirm kit): when set, the invisible
                 * mount anchor entity also renders this item id as a dropped-item-style prop, so
                 * the anchor's position is visible in-game. A WORKING Entity mount is otherwise
                 * deliberately invisible (the mount is a positioning/input-lock primitive; real
                 * station visuals come from Camera/Animation/Puppet authoring). Null = no visual,
                 * the shipped default.
                 */
                @Nullable
                public String getVisibleAnchorItemId() {
                    return visibleAnchorItemId;
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
            }
        }
    }

    /**
     * The held-tool gate: the player must be HOLDING a matching tool to start (and keep)
     * working. Three optional NATIVE match routes; match = ANY route satisfied.
     *
     * <p>{@link #minDurabilityPercent} is a SEPARATE, orthogonal condition layered on top of the
     * identity routes: which tool, and how worn it may be, are two independent questions.
     */
    public static final class Tool {
        @Nullable protected Map<String, String[]> tags;
        @Nullable protected Gather gather;
        @Nullable protected String[] ids;
        @Nullable protected PowerScale powerScale;
        @Nullable protected Durability durability;
        @Nullable protected Double minDurabilityPercent;

        public static final BuilderCodec<Tool> CODEC = BuilderCodec.builder(Tool.class, Tool::new)
                .appendInherited(new KeyedCodec<>("Tags", TagMatch.CODEC, false),
                        (o, v) -> o.tags = v, o -> o.tags, (o, p) -> o.tags = p.tags)
                .documentation("Native tag family match (e.g. {\"Family\":[\"Dagger\"]}): matches ANY-of the values per key against the held Item's raw tags.").add()
                .appendInherited(new KeyedCodec<>("Gather", Gather.CODEC, false),
                        (o, v) -> o.gather = v, o -> o.gather, (o, p) -> o.gather = p.gather)
                .documentation("The functional gather-power match route: a native GatherType plus a minimum power floor.").add()
                .appendInherited(new KeyedCodec<>("Ids", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.ids = v, o -> o.ids, (o, p) -> o.ids = p.ids)
                .documentation("The FALLBACK exact/underscore-segment item id match route, for modded tools with no matching tag or gather spec.").add()
                .appendInherited(new KeyedCodec<>("PowerScale", PowerScale.CODEC, false),
                        (o, v) -> o.powerScale = v, o -> o.powerScale, (o, p) -> o.powerScale = p.powerScale)
                .documentation("Maps held-tool power to the multiplier applied to every Work.PerCycleContributions "
                        + "amount on a real cycle (forwarded as the cycle event's toolMultiplier); forced 1.0 on an "
                        + "idle cycle. Null = the multiplier stays neutral 1.0 regardless of held tool.").add()
                .appendInherited(new KeyedCodec<>("Durability", Durability.CODEC, false),
                        (o, v) -> o.durability = v, o -> o.durability, (o, p) -> o.durability = p.durability)
                .documentation("Opt-in held-tool durability drain per swing and/or per cycle; null = no drain.").add()
                .appendInherited(new KeyedCodec<>("MinDurabilityPercent", Codec.DOUBLE, false),
                        (o, v) -> o.minDurabilityPercent = v, o -> o.minDurabilityPercent,
                        (o, p) -> o.minDurabilityPercent = p.minDurabilityPercent)
                .documentation("Minimum held-tool durability (0-100) required to START working; null = no wear gate. Checked at engage only, so a session already running is never cut short by wear.").add()
                .build();

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids) {
            return of(tags, gather, ids, null);
        }

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids, @Nullable PowerScale powerScale) {
            return of(tags, gather, ids, powerScale, null);
        }

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids, @Nullable PowerScale powerScale, @Nullable Durability durability) {
            return of(tags, gather, ids, powerScale, durability, null);
        }

        @Nonnull
        public static Tool of(@Nullable Map<String, String[]> tags, @Nullable Gather gather,
                @Nullable String[] ids, @Nullable PowerScale powerScale, @Nullable Durability durability,
                @Nullable Double minDurabilityPercent) {
            Tool t = new Tool();
            t.tags = tags;
            t.gather = gather;
            t.ids = ids;
            t.powerScale = powerScale;
            t.durability = durability;
            t.minDurabilityPercent = minDurabilityPercent;
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
        public PowerScale getPowerScale() {
            return powerScale;
        }

        @Nullable
        public Durability getDurability() {
            return durability;
        }

        /**
         * The minimum held-tool durability PERCENT (0-100) required to start a session; null (or a
         * non-positive value) = no wear gate. A tool that tracks no durability at all always passes.
         * Deliberately an ENGAGE-time gate only: the per-heartbeat tool re-check stays about tool
         * IDENTITY, so wearing a tool down mid-session ends the session at breakage
         * ({@code TOOL_BROKEN}) exactly as before, never at this threshold.
         */
        @Nullable
        public Double getMinDurabilityPercent() {
            return minDurabilityPercent;
        }

        /** True when a wear gate is authored and live (a positive percent). */
        public boolean hasDurabilityGate() {
            return minDurabilityPercent != null && minDurabilityPercent > 0;
        }

        /** The functional gather route: a {@code GatherType} plus a {@code MinPower} floor. */
        public static final class Gather {
            @Nullable protected String gatherType;
            @Nullable protected Double minPower;

            public static final BuilderCodec<Gather> CODEC = BuilderCodec.builder(Gather.class, Gather::new)
                    .appendInherited(new KeyedCodec<>("GatherType", Codec.STRING, false),
                            (o, v) -> o.gatherType = v, o -> o.gatherType, (o, p) -> o.gatherType = p.gatherType)
                    .documentation("The native GatherType id the held tool's ItemToolSpec must report.").add()
                    .appendInherited(new KeyedCodec<>("MinPower", Codec.DOUBLE, false),
                            (o, v) -> o.minPower = v, o -> o.minPower, (o, p) -> o.minPower = p.minPower)
                    .documentation("The minimum ItemToolSpec power the held tool must report for this GatherType.")
                    .addValidator(CodecWarnValidators.positive("Tool.Gather.MinPower should be positive.")).add()
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
         * Tool-power contribution scaling: every {@code Work.PerCycleContributions} amount
         * multiplies by {@code clamp((heldPower / ReferencePower) ^ Exponent, MinMult, MaxMult)}.
         * OMIT this group and the multiplier stays 1.0. Reader defaults: {@link #exponent} 1.0,
         * {@link #minMult} 0.5, {@link #maxMult} 2.0.
         */
        public static final class PowerScale {
            @Nullable protected String gatherType;
            @Nullable protected Double referencePower;
            @Nullable protected Double exponent;
            @Nullable protected Double minMult;
            @Nullable protected Double maxMult;

            public static final BuilderCodec<PowerScale> CODEC = BuilderCodec.builder(PowerScale.class, PowerScale::new)
                    .appendInherited(new KeyedCodec<>("GatherType", Codec.STRING, false),
                            (o, v) -> o.gatherType = v, o -> o.gatherType, (o, p) -> o.gatherType = p.gatherType)
                    .documentation("The native GatherType id whose power is read off the held tool each cycle for this scale.").add()
                    .appendInherited(new KeyedCodec<>("ReferencePower", Codec.DOUBLE, false),
                            (o, v) -> o.referencePower = v, o -> o.referencePower,
                            (o, p) -> o.referencePower = p.referencePower)
                    .documentation("The tool power treated as the neutral 1.0x baseline the held tool's power is divided by.")
                    .addValidator(CodecWarnValidators.positive("Tool.PowerScale.ReferencePower should be positive.")).add()
                    .appendInherited(new KeyedCodec<>("Exponent", Codec.DOUBLE, false),
                            (o, v) -> o.exponent = v, o -> o.exponent, (o, p) -> o.exponent = p.exponent)
                    .documentation("The power curve exponent applied to (heldPower / ReferencePower). Reader-defaults to 1.0 (linear).").add()
                    .appendInherited(new KeyedCodec<>("MinMult", Codec.DOUBLE, false),
                            (o, v) -> o.minMult = v, o -> o.minMult, (o, p) -> o.minMult = p.minMult)
                    .documentation("The floor the resolved multiplier clamps to. Reader-defaults to 0.5.")
                    .addValidator(CodecWarnValidators.positive("Tool.PowerScale.MinMult should be positive.")).add()
                    .appendInherited(new KeyedCodec<>("MaxMult", Codec.DOUBLE, false),
                            (o, v) -> o.maxMult = v, o -> o.maxMult, (o, p) -> o.maxMult = p.maxMult)
                    .documentation("The ceiling the resolved multiplier clamps to. Reader-defaults to 2.0.")
                    .addValidator(CodecWarnValidators.positive("Tool.PowerScale.MaxMult should be positive.")).add()
                    .build();

            @Nonnull
            public static PowerScale of(@Nullable String gatherType, @Nullable Double referencePower,
                    @Nullable Double exponent, @Nullable Double minMult, @Nullable Double maxMult) {
                PowerScale x = new PowerScale();
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
                            (o, v) -> o.perSwing = v, o -> o.perSwing, (o, p) -> o.perSwing = p.perSwing)
                    .documentation("Durability points drained from the held tool per swing cue. Reader-defaults to 0 (off).")
                    .addValidator(CodecWarnValidators.nonNegative("Tool.Durability.PerSwing should not be negative.")).add()
                    .appendInherited(new KeyedCodec<>("PerCycle", Codec.INTEGER, false),
                            (o, v) -> o.perCycle = v, o -> o.perCycle, (o, p) -> o.perCycle = p.perCycle)
                    .documentation("Durability points drained from the held tool per completed cycle. Reader-defaults to 0 (off).")
                    .addValidator(CodecWarnValidators.nonNegative("Tool.Durability.PerCycle should not be negative.")).add()
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
                        (o, v) -> o.mode = v, o -> o.mode, (o, p) -> o.mode = p.mode)
                .documentation("The camera route: 'ThirdPerson' (default, the pull applies) or 'None' (no camera pull at all - a mounted station with no authored Camera also defaults to none).")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:camera-mode"))).add()
                .appendInherited(new KeyedCodec<>("Locked", Codec.BOOLEAN, false),
                        (o, v) -> o.locked = v, o -> o.locked, (o, p) -> o.locked = p.locked)
                .documentation("Whether the third-person camera is locked in place while working (blocks player-driven camera rotation). Reader-defaults to true.").add()
                .appendInherited(new KeyedCodec<>("FaceBlock", Codec.BOOLEAN, false),
                        (o, v) -> o.faceBlock = v, o -> o.faceBlock, (o, p) -> o.faceBlock = p.faceBlock)
                .documentation("Whether the fixed-look FaceBlock camera preset applies (mouse-driven spin is fully frozen, not just locked). Only takes effect when the camera is applied at all.").add()
                .appendInherited(new KeyedCodec<>("Recipe", Codec.STRING, false),
                        (o, v) -> o.recipe = v, o -> o.recipe,
                        (o, p) -> o.recipe = p.recipe)
                .documentation("The StationCameraPreset recipe id the FaceBlock route applies (the exact ServerCameraSettings field combination); null = the engine's default FROZEN recipe.")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:camera-presets"))).add()
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
                        (o, v) -> o.emoteId = v, o -> o.emoteId, (o, p) -> o.emoteId = p.emoteId)
                .documentation("The registered EmoteAsset id looped while working; null = no work animation played.").add()
                .appendInherited(new KeyedCodec<>("Swing", Swing.CODEC, false),
                        (o, v) -> o.swing = v, o -> o.swing, (o, p) -> o.swing = p.swing)
                .documentation("The optional per-swing cadence layer (re-fires a one-shot cue on an interval); null = no swing layer.").add()
                .appendInherited(new KeyedCodec<>("ActionClip", Codec.STRING, false),
                        (o, v) -> o.actionClip = v, o -> o.actionClip, (o, p) -> o.actionClip = p.actionClip)
                .documentation("The Action-slot clip id for a SEAT-MODE swing (plays against the held item's own ItemPlayerAnimations set). Null/blank falls to the 'Chop' default.").add()
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
                            (o, v) -> o.intervalMs = v, o -> o.intervalMs, (o, p) -> o.intervalMs = p.intervalMs)
                    .documentation("Milliseconds between swing cues while working; a non-looping EmoteId needs this authored or the work animation never re-fires.")
                    .addValidator(CodecWarnValidators.positive("Animation.Swing.IntervalMs should be positive.")).add()
                    .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                            (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation)
                    .documentation("The presentation cue fired together with each swing re-fire; null = the swing plays with no extra sound/particles.").add()
                    .appendInherited(new KeyedCodec<>("Impact", Impact.CODEC, false),
                            (o, v) -> o.impact = v, o -> o.impact, (o, p) -> o.impact = p.impact)
                    .documentation("An optional delayed impact cue scheduled off this swing; null = no impact cue.").add()
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
                                (o, v) -> o.delayMs = v, o -> o.delayMs, (o, p) -> o.delayMs = p.delayMs)
                        .documentation("Milliseconds after the swing before the impact cue fires. Null/non-positive = fire together with the swing.")
                        .addValidator(CodecWarnValidators.nonNegative("Animation.Swing.Impact.DelayMs should not be negative.")).add()
                        .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                                (o, v) -> o.presentation = v, o -> o.presentation,
                                (o, p) -> o.presentation = p.presentation)
                        .documentation("The presentation cue fired at the scheduled impact moment.").add()
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
                        (o, v) -> o.moments = v, o -> o.moments, (o, p) -> o.moments = p.moments)
                .documentation("An open moment id (cycle/swing/impact/rare_find/completion, or step:<actionId>:<stepId>) to a Presentation overlay; each authored leaf overlays the base moment's own leaves, per moment id.").add()
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
