package com.ziggfreed.rpgstations.asset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

/**
 * The ONE additive-extension asset (scope-2 design 1.8, decision 27): a fourth party extends a
 * third party's content WITHOUT owning or replacing its files.
 * {@code Server/RpgStations/Extensions/<Name>.json} (Pattern A, id = lowercased filename). Every
 * collection-bearing group that is NOT cosmetic routes through this ONE mechanism (cosmetics stay
 * {@link FlairAsset}).
 *
 * <p><b>{@link #target}</b> names EXACTLY ONE target ({@code {Station|Action|Lootable|RollPool}} -
 * orthogonal exactly-one-of leaves, gate Q21, never a {@code Type} enum). Target-specific payload
 * groups (all nullable; authoring a group the target type cannot carry is validator
 * {@code EXTENSION_PAYLOAD_MISMATCH} - see {@link #payloadAllowedFor}):
 * <table><tr><th>Target</th><th>Payload keys</th></tr>
 * <tr><td>Station</td><td>Xp, Loot, Actions, Conversions, Steps, Anchors, Puppet, Custody</td></tr>
 * <tr><td>Action</td><td>Steps, Anchors, Loot, Conversions, Xp, Puppet, Custody</td></tr>
 * <tr><td>Lootable</td><td>Rolls</td></tr>
 * <tr><td>RollPool</td><td>Entries</td></tr></table>
 *
 * <p><b>Merge + conflict semantics (deterministic, applied engine-side in
 * {@code ExtensionCatalog.applyTo} - leg A3):</b>
 * <ol>
 *   <li>ADDITIVE ONLY for every COLLECTION payload - never mutate/replace/remove an existing entry
 *   (replacing a whole file stays load-order's job). The two PRESENTATION-overlay payloads
 *   ({@link #puppet}/{@link #custody}, rule 5) are the deliberate exception: they carry no
 *   collection at all, so "additive" there means per-leaf, never a whole-group clobber.
 *   <li>Keyed collections ({@code Actions}, {@code Anchors}): the BASE always wins a key collision
 *   ({@code EXTENSION_KEY_COLLISION}, entry skipped); among extensions the {@link #APPLY_ORDER}
 *   tuple ({@code Priority} asc so higher applies LATER, then extension id lex) decides, with the
 *   later (higher-priority) extension winning a new key.
 *   <li>Unkeyed arrays ({@code Xp}, {@code Conversions}, {@code Rolls}, {@code Entries}): pure
 *   append, ordered by {@link #APPLY_ORDER} ({@code Priority}, extension id, in-file order).
 *   <li>Ordered insertion ({@code Steps}): each {@link StepInsertion} carries an
 *   {@link StepInsertion.Anchor} exactly-one-of {@code {After|Before|AtStart|AtEnd}}; a missing
 *   anchor step id degrades to {@code AtEnd} + {@code EXTENSION_ANCHOR_MISSING}; inserted steps
 *   need {@code Id}s so LATER extensions can anchor on them. Co-anchored insertions from different
 *   extensions apply in {@link #APPLY_ORDER} (m2).
 *   <li>NESTED PER-LEAF OVERLAY ({@code Puppet}, {@code Custody}): recursively, at EVERY nesting
 *   depth, an AUTHORED extension leaf wins and a NULL extension leaf leaves the base's value
 *   intact - the {@code appendInherited}/nullable-nested-leaves convention applied across assets
 *   instead of across a {@code Parent} chain. So a {@code Custody} overlay carrying only
 *   {@code Display} never touches the base's {@code States}/{@code MaxQuantity}/{@code Input}, and
 *   a {@code Display} carrying only {@code Scale} never clears the base's {@code Offset}. Among
 *   extensions the later (higher-priority) one overlays on top, so IT wins a same-leaf contest.
 * </ol>
 *
 * <p><b>Composition order (m7, stated for the docsite):</b> extensions apply to the
 * Parent-resolved target at read time; extension ADDITIONS do NOT flow down Parent chains. A
 * {@code Target:{Action}} extension flows to every {@code Ref} user of that action; a
 * {@code Target:{Station}} step-insert applies post-Ref to THAT station only.
 *
 * <p><b>Deliberately NON-extensible</b> (docs state each): {@code Requires}, {@code Settings},
 * scalar groups (Work/Hold/Camera/Animation), the INTERNALS of an existing {@code Roll}, and
 * {@code FlairAsset.Moments} (extend by shipping another FlairAsset).
 *
 * <p><b>The presentation-overlay exception ({@link #puppet}/{@link #custody}):</b> {@code Puppet}
 * and the whole {@code Custody} group (incl. {@code Custody.States}) ARE overlayable, under rule 5's
 * per-leaf semantics. They were on the non-extensible list through wave 2 on the "override is
 * load-order's job" argument; a pack that only wants to RE-SKIN a base station's presentation had to
 * ship a full-file station override to do it, and deleting such an override silently dropped every
 * group it was the sole author of. A per-leaf overlay is the non-destructive route: a re-skinning
 * pack authors just the leaves it re-tunes and inherits the rest of the base station.
 */
public final class ExtensionAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, ExtensionAsset>> {

    /** Payload-group keys, used by {@link #payloadAllowedFor} and the validator. */
    public static final String PAYLOAD_XP = "Xp";
    public static final String PAYLOAD_LOOT = "Loot";
    public static final String PAYLOAD_ACTIONS = "Actions";
    public static final String PAYLOAD_CONVERSIONS = "Conversions";
    public static final String PAYLOAD_STEPS = "Steps";
    public static final String PAYLOAD_ANCHORS = "Anchors";
    public static final String PAYLOAD_PUPPET = "Puppet";
    public static final String PAYLOAD_CUSTODY = "Custody";
    public static final String PAYLOAD_ROLLS = "Rolls";
    public static final String PAYLOAD_ENTRIES = "Entries";

    /**
     * The ONE apply-order tuple ({@code Priority} ascending so a higher priority applies LATER and
     * wins a key tie, then extension id lexicographic). Total-order over distinct assets (ids are
     * unique), so a STABLE sort over the fold's in-file order fully determines the result on every
     * server - the design's {@code (Priority, extension id, in-file order)} rule.
     */
    public static final Comparator<ExtensionAsset> APPLY_ORDER =
            Comparator.comparingInt(ExtensionAsset::effectivePriority)
                    .thenComparing(e -> e.getId() == null ? "" : e.getId());

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private Target target;
    @Nullable private Integer priority;
    @Nullable private StationAsset.WorkXp[] xp;
    @Nullable private LootRef loot;
    @Nullable private Map<String, ActionDef> actions;
    @Nullable private StationAsset.Conversion[] conversions;
    @Nullable private StepInsertion[] steps;
    @Nullable private Map<String, ActionDef.Anchor> anchors;
    @Nullable private Puppet puppet;
    @Nullable private Custody custody;
    @Nullable private Roll[] rolls;
    @Nullable private StatRollEntry[] entries;

    public static final AssetBuilderCodec<String, ExtensionAsset> CODEC = AssetBuilderCodec.builder(
                    ExtensionAsset.class,
                    ExtensionAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .appendInherited(new KeyedCodec<>("Target", Target.CODEC, false),
                    (a, v) -> a.target = v, a -> a.target, (a, p) -> a.target = p.target)
            .documentation("Exactly one of Station | Action | Lootable | RollPool - what this extension adds to.").add()
            .appendInherited(new KeyedCodec<>("Priority", Codec.INTEGER, false),
                    (a, v) -> a.priority = v, a -> a.priority, (a, p) -> a.priority = p.priority)
            .documentation("Apply-order priority (default 0); a higher priority applies LATER and wins a key collision among extensions.").add()
            .appendInherited(new KeyedCodec<>("Xp", new ArrayCodec<>(StationAsset.WorkXp.CODEC, StationAsset.WorkXp[]::new), false),
                    (a, v) -> a.xp = v, a -> a.xp, (a, p) -> a.xp = p.xp)
            .documentation("Appended Work.Xp declarations (Station/Action target).").add()
            .appendInherited(new KeyedCodec<>("Loot", LootRef.CODEC, false),
                    (a, v) -> a.loot = v, a -> a.loot, (a, p) -> a.loot = p.loot)
            .documentation("Appended loot references and inline Rolls (Station/Action target).").add()
            .appendInherited(new KeyedCodec<>("Actions", new MapCodec<>(ActionDef.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.actions = v, a -> a.actions, (a, p) -> a.actions = p.actions)
            .documentation("NEW actions only (Station target); base wins a key collision.").add()
            .appendInherited(new KeyedCodec<>("Conversions", new ArrayCodec<>(StationAsset.Conversion.CODEC, StationAsset.Conversion[]::new), false),
                    (a, v) -> a.conversions = v, a -> a.conversions, (a, p) -> a.conversions = p.conversions)
            .documentation("Appended Recipe.Conversions (Station/Action target).").add()
            .appendInherited(new KeyedCodec<>("Steps", new ArrayCodec<>(StepInsertion.CODEC, StepInsertion[]::new), false),
                    (a, v) -> a.steps = v, a -> a.steps, (a, p) -> a.steps = p.steps)
            .documentation("Ordered step insertions into a named action (Station/Action target).").add()
            .appendInherited(new KeyedCodec<>("Anchors", new MapCodec<>(ActionDef.Anchor.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.anchors = v, a -> a.anchors, (a, p) -> a.anchors = p.anchors)
            .documentation("NEW anchor declarations only (Station/Action target); base wins a key collision.").add()
            .appendInherited(new KeyedCodec<>("Puppet", Puppet.CODEC, false),
                    (a, v) -> a.puppet = v, a -> a.puppet, (a, p) -> a.puppet = p.puppet)
            .documentation("Puppet presentation overlay (Station/Action target), merged PER LEAF at every depth: an authored leaf wins, an omitted leaf keeps the base station's own value.").add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (a, v) -> a.custody = v, a -> a.custody, (a, p) -> a.custody = p.custody)
            .documentation("Custody overlay (Station/Action target), merged PER LEAF at every depth: an overlay carrying only Display never clears the base's States, MaxQuantity or Input.").add()
            .appendInherited(new KeyedCodec<>("Rolls", new ArrayCodec<>(Roll.CODEC, Roll[]::new), false),
                    (a, v) -> a.rolls = v, a -> a.rolls, (a, p) -> a.rolls = p.rolls)
            .documentation("Appended Rolls (Lootable target).").add()
            .appendInherited(new KeyedCodec<>("Entries", new ArrayCodec<>(StatRollEntry.CODEC, StatRollEntry[]::new), false),
                    (a, v) -> a.entries = v, a -> a.entries, (a, p) -> a.entries = p.entries)
            .documentation("Appended StatRollEntry entries (RollPool target).").add()
            .build();

    public ExtensionAsset() {
    }

    @Nonnull
    public static ExtensionAsset of(@Nonnull String id, @Nullable Target target, @Nullable Integer priority) {
        ExtensionAsset a = new ExtensionAsset();
        a.id = id;
        a.target = target;
        a.priority = priority;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public Target getTarget() {
        return target;
    }

    @Nullable
    public Integer getPriority() {
        return priority;
    }

    /** {@link #priority}, reader-defaulted to 0 when null. */
    public int effectivePriority() {
        return priority != null ? priority : 0;
    }

    /** Appended {@code Work.Xp} declarations (Station/Action target); null = none. */
    @Nullable
    public StationAsset.WorkXp[] getXp() {
        return xp;
    }

    /** Appended loot references (Station/Action target); null = none. */
    @Nullable
    public LootRef getLoot() {
        return loot;
    }

    /** NEW actions only (Station target); null = none. */
    @Nullable
    public Map<String, ActionDef> getActions() {
        return actions;
    }

    /** Appended conversions (Station/Action target); null = none. */
    @Nullable
    public StationAsset.Conversion[] getConversions() {
        return conversions;
    }

    /** Ordered step insertions (Station/Action target); null = none. */
    @Nullable
    public StepInsertion[] getSteps() {
        return steps;
    }

    /** NEW anchor declarations only (Station/Action target); null = none. */
    @Nullable
    public Map<String, ActionDef.Anchor> getAnchors() {
        return anchors;
    }

    /**
     * The {@code Puppet} presentation overlay (Station/Action target); null = none. Merged PER LEAF
     * onto the base station's own group (rule 5), never as a whole-group replace - so a re-skinning
     * pack authors only the leaves it re-tunes.
     */
    @Nullable
    public Puppet getPuppet() {
        return puppet;
    }

    /**
     * The {@code Custody} overlay (Station/Action target); null = none. Merged PER LEAF onto the base
     * station's own group (rule 5): an overlay carrying only {@code Display} leaves the base's
     * {@code States}/{@code MaxQuantity}/{@code Input} exactly as they were.
     */
    @Nullable
    public Custody getCustody() {
        return custody;
    }

    /** Appended Rolls (Lootable target); null = none. */
    @Nullable
    public Roll[] getRolls() {
        return rolls;
    }

    /** Appended entries (RollPool target); null = none. */
    @Nullable
    public StatRollEntry[] getEntries() {
        return entries;
    }

    /**
     * PURE apply-ordering (unit-tested; shared by the catalog and the validator): the extensions
     * sorted into {@link #APPLY_ORDER}. Deterministic on every server for the same input set (ids
     * are unique, so the tuple is a total order; a stable sort makes any encounter order collapse
     * to the same result). The catalog applies them in this order (m2).
     */
    @Nonnull
    public static List<ExtensionAsset> sortedForApply(@Nonnull Collection<ExtensionAsset> extensions) {
        List<ExtensionAsset> out = new ArrayList<>(extensions);
        out.sort(APPLY_ORDER);
        return out;
    }

    /**
     * PURE payload-legality check (design 1.8 table): whether {@code payloadKey} (one of the
     * {@code PAYLOAD_*} constants) is legal for {@code targetType} (a {@link Target} type constant).
     * Used by the validator ({@code EXTENSION_PAYLOAD_MISMATCH}) and documented for authors.
     */
    public static boolean payloadAllowedFor(@Nullable String targetType, @Nonnull String payloadKey) {
        if (targetType == null) {
            return false;
        }
        switch (targetType) {
            case Target.STATION:
                return PAYLOAD_XP.equals(payloadKey) || PAYLOAD_LOOT.equals(payloadKey)
                        || PAYLOAD_ACTIONS.equals(payloadKey) || PAYLOAD_CONVERSIONS.equals(payloadKey)
                        || PAYLOAD_STEPS.equals(payloadKey) || PAYLOAD_ANCHORS.equals(payloadKey)
                        || PAYLOAD_PUPPET.equals(payloadKey) || PAYLOAD_CUSTODY.equals(payloadKey);
            case Target.ACTION:
                return PAYLOAD_STEPS.equals(payloadKey) || PAYLOAD_ANCHORS.equals(payloadKey)
                        || PAYLOAD_LOOT.equals(payloadKey) || PAYLOAD_CONVERSIONS.equals(payloadKey)
                        || PAYLOAD_XP.equals(payloadKey)
                        || PAYLOAD_PUPPET.equals(payloadKey) || PAYLOAD_CUSTODY.equals(payloadKey);
            case Target.LOOTABLE:
                return PAYLOAD_ROLLS.equals(payloadKey);
            case Target.ROLLPOOL:
                return PAYLOAD_ENTRIES.equals(payloadKey);
            default:
                return false;
        }
    }

    /**
     * The exactly-one-of target (gate Q21): {@code {Station|Action|Lootable|RollPool}} - four
     * orthogonal string leaves, EXACTLY one authored (never a {@code Type} enum + Id pair). See
     * {@link #hasExactlyOneTarget()}/{@link #resolvedType()}/{@link #resolvedId()}.
     */
    public static final class Target {
        public static final String STATION = "Station";
        public static final String ACTION = "Action";
        public static final String LOOTABLE = "Lootable";
        public static final String ROLLPOOL = "RollPool";

        @Nullable protected String station;
        @Nullable protected String action;
        @Nullable protected String lootable;
        @Nullable protected String rollPool;

        public static final BuilderCodec<Target> CODEC = BuilderCodec.builder(Target.class, Target::new)
                .appendInherited(new KeyedCodec<>("Station", Codec.STRING, false),
                        (o, v) -> o.station = v, o -> o.station, (o, p) -> o.station = p.station)
                .documentation("A station id to extend (exactly one of Station | Action | Lootable | RollPool).").add()
                .appendInherited(new KeyedCodec<>("Action", Codec.STRING, false),
                        (o, v) -> o.action = v, o -> o.action, (o, p) -> o.action = p.action)
                .documentation("An ActionAsset id to extend (exactly one of Station | Action | Lootable | RollPool).").add()
                .appendInherited(new KeyedCodec<>("Lootable", Codec.STRING, false),
                        (o, v) -> o.lootable = v, o -> o.lootable, (o, p) -> o.lootable = p.lootable)
                .documentation("A LootableAsset id to extend (exactly one of Station | Action | Lootable | RollPool).").add()
                .appendInherited(new KeyedCodec<>("RollPool", Codec.STRING, false),
                        (o, v) -> o.rollPool = v, o -> o.rollPool, (o, p) -> o.rollPool = p.rollPool)
                .documentation("A RollPool id to extend (exactly one of Station | Action | Lootable | RollPool).").add()
                .build();

        @Nonnull
        public static Target station(@Nullable String id) {
            Target t = new Target();
            t.station = id;
            return t;
        }

        @Nonnull
        public static Target action(@Nullable String id) {
            Target t = new Target();
            t.action = id;
            return t;
        }

        @Nonnull
        public static Target lootable(@Nullable String id) {
            Target t = new Target();
            t.lootable = id;
            return t;
        }

        @Nonnull
        public static Target rollPool(@Nullable String id) {
            Target t = new Target();
            t.rollPool = id;
            return t;
        }

        @Nullable
        public String getStation() {
            return station;
        }

        @Nullable
        public String getAction() {
            return action;
        }

        @Nullable
        public String getLootable() {
            return lootable;
        }

        @Nullable
        public String getRollPool() {
            return rollPool;
        }

        private static boolean set(@Nullable String s) {
            return s != null && !s.isBlank();
        }

        /** True when EXACTLY one target leaf is authored (the exactly-one-of contract). */
        public boolean hasExactlyOneTarget() {
            int n = 0;
            if (set(station)) {
                n++;
            }
            if (set(action)) {
                n++;
            }
            if (set(lootable)) {
                n++;
            }
            if (set(rollPool)) {
                n++;
            }
            return n == 1;
        }

        /** The resolved target TYPE constant ({@link #STATION}/...), or null when not exactly one is authored. */
        @Nullable
        public String resolvedType() {
            if (!hasExactlyOneTarget()) {
                return null;
            }
            if (set(station)) {
                return STATION;
            }
            if (set(action)) {
                return ACTION;
            }
            if (set(lootable)) {
                return LOOTABLE;
            }
            return ROLLPOOL;
        }

        /** The resolved target ID (the authored leaf's value), or null when not exactly one is authored. */
        @Nullable
        public String resolvedId() {
            if (!hasExactlyOneTarget()) {
                return null;
            }
            if (set(station)) {
                return station;
            }
            if (set(action)) {
                return action;
            }
            if (set(lootable)) {
                return lootable;
            }
            return rollPool;
        }
    }

    /**
     * ONE ordered step insertion (design 1.8): {@link #action} names the target action's step
     * program (for a {@code Target:{Station}}, WHICH of the station's actions to insert into; for a
     * {@code Target:{Action}} it may be omitted - the action itself); {@link #anchor} is the
     * exactly-one-of position; {@link #insert} are the inserted {@link StationStep}s (each REQUIRES
     * an {@code Id} so later extensions can anchor on them).
     */
    public static final class StepInsertion {
        @Nullable protected String action;
        @Nullable protected Anchor anchor;
        @Nullable protected StationStep[] insert;

        public static final BuilderCodec<StepInsertion> CODEC = BuilderCodec.builder(StepInsertion.class, StepInsertion::new)
                .appendInherited(new KeyedCodec<>("Action", Codec.STRING, false),
                        (o, v) -> o.action = v, o -> o.action, (o, p) -> o.action = p.action)
                .documentation("Which action's step program to insert into (e.g. 'work'); omit for a Target:{Action} extension.").add()
                .appendInherited(new KeyedCodec<>("Anchor", Anchor.CODEC, false),
                        (o, v) -> o.anchor = v, o -> o.anchor, (o, p) -> o.anchor = p.anchor)
                .documentation("The insertion position: exactly one of After | Before | AtStart | AtEnd.").add()
                .appendInherited(new KeyedCodec<>("Insert", new ArrayCodec<>(StationStep.CODEC, StationStep[]::new), false),
                        (o, v) -> o.insert = v, o -> o.insert, (o, p) -> o.insert = p.insert)
                .documentation("The steps to insert; each REQUIRES an Id so later extensions can anchor on them.").add()
                .build();

        @Nonnull
        public static StepInsertion of(@Nullable String action, @Nullable Anchor anchor, @Nullable StationStep[] insert) {
            StepInsertion s = new StepInsertion();
            s.action = action;
            s.anchor = anchor;
            s.insert = insert;
            return s;
        }

        @Nullable
        public String getAction() {
            return action;
        }

        @Nullable
        public Anchor getAnchor() {
            return anchor;
        }

        @Nullable
        public StationStep[] getInsert() {
            return insert;
        }

        /**
         * The insertion anchor (design 1.8, gate Q11): EXACTLY one of {@code {After: "<stepId>"} |
         * {Before: "<stepId>"} | {AtStart: true} | {AtEnd: true}}. A missing/dangling {@code After}/
         * {@code Before} step id degrades to {@link #AT_END} + {@code EXTENSION_ANCHOR_MISSING}
         * (engine-side). See {@link #effectivePlacement()}.
         */
        public static final class Anchor {
            public static final String AFTER = "After";
            public static final String BEFORE = "Before";
            public static final String AT_START = "AtStart";
            public static final String AT_END = "AtEnd";

            @Nullable protected String after;
            @Nullable protected String before;
            @Nullable protected Boolean atStart;
            @Nullable protected Boolean atEnd;

            public static final BuilderCodec<Anchor> CODEC = BuilderCodec.builder(Anchor.class, Anchor::new)
                    .appendInherited(new KeyedCodec<>("After", Codec.STRING, false),
                            (o, v) -> o.after = v, o -> o.after, (o, p) -> o.after = p.after)
                    .documentation("Insert AFTER the step with this Id (exactly one of After | Before | AtStart | AtEnd).").add()
                    .appendInherited(new KeyedCodec<>("Before", Codec.STRING, false),
                            (o, v) -> o.before = v, o -> o.before, (o, p) -> o.before = p.before)
                    .documentation("Insert BEFORE the step with this Id (exactly one of After | Before | AtStart | AtEnd).").add()
                    .appendInherited(new KeyedCodec<>("AtStart", Codec.BOOLEAN, false),
                            (o, v) -> o.atStart = v, o -> o.atStart, (o, p) -> o.atStart = p.atStart)
                    .documentation("Insert at the START of the program (exactly one of After | Before | AtStart | AtEnd).").add()
                    .appendInherited(new KeyedCodec<>("AtEnd", Codec.BOOLEAN, false),
                            (o, v) -> o.atEnd = v, o -> o.atEnd, (o, p) -> o.atEnd = p.atEnd)
                    .documentation("Insert at the END of the program (exactly one of After | Before | AtStart | AtEnd; the degrade default).").add()
                    .build();

            @Nonnull
            public static Anchor after(@Nullable String stepId) {
                Anchor a = new Anchor();
                a.after = stepId;
                return a;
            }

            @Nonnull
            public static Anchor before(@Nullable String stepId) {
                Anchor a = new Anchor();
                a.before = stepId;
                return a;
            }

            @Nonnull
            public static Anchor atStart() {
                Anchor a = new Anchor();
                a.atStart = true;
                return a;
            }

            @Nonnull
            public static Anchor atEnd() {
                Anchor a = new Anchor();
                a.atEnd = true;
                return a;
            }

            @Nullable
            public String getAfter() {
                return after;
            }

            @Nullable
            public String getBefore() {
                return before;
            }

            @Nullable
            public Boolean getAtStart() {
                return atStart;
            }

            @Nullable
            public Boolean getAtEnd() {
                return atEnd;
            }

            private static boolean set(@Nullable String s) {
                return s != null && !s.isBlank();
            }

            private static boolean set(@Nullable Boolean b) {
                return b != null && b;
            }

            /** True when EXACTLY one placement is authored (the exactly-one-of contract). */
            public boolean hasExactlyOnePlacement() {
                int n = 0;
                if (set(after)) {
                    n++;
                }
                if (set(before)) {
                    n++;
                }
                if (set(atStart)) {
                    n++;
                }
                if (set(atEnd)) {
                    n++;
                }
                return n == 1;
            }

            /**
             * The resolved placement TYPE constant ({@link #AFTER}/{@link #BEFORE}/{@link #AT_START}/
             * {@link #AT_END}), reader-defaulted to {@link #AT_END} when not exactly one is authored
             * (the degrade rule; the engine additionally warns for a dangling After/Before step id).
             */
            @Nonnull
            public String effectivePlacement() {
                if (!hasExactlyOnePlacement()) {
                    return AT_END;
                }
                if (set(after)) {
                    return AFTER;
                }
                if (set(before)) {
                    return BEFORE;
                }
                if (set(atStart)) {
                    return AT_START;
                }
                return AT_END;
            }

            /** The referenced step id for an {@link #AFTER}/{@link #BEFORE} placement, else null. */
            @Nullable
            public String anchorStepId() {
                if (set(after)) {
                    return after;
                }
                if (set(before)) {
                    return before;
                }
                return null;
            }
        }
    }
}
