package com.ziggfreed.rpgstations.asset;

import java.util.Locale;
import java.util.function.IntSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.ziggfreed.common.asset.EditorSchema;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditorSectionStart;

/**
 * RpgStations' single engine-settings asset - design section 4.6: {@code
 * Server/RpgStations/Settings/Settings.json}, ONE fixed id ({@link #ID}), jar default (this
 * jar ships {@code Settings.json} with every leaf explicit) plus pack-overridable (a pack layers
 * its own {@code Settings.json} over the jar default via the normal Pattern-A store merge - the
 * mob-scaling asset-codec-config paradigm; no loose Gson blob, no owner-file write-back in
 * 1.0.0). Independently composable, orthogonal knobs throughout.
 *
 * <pre>{@code
 * {
 *   "Enabled": true,
 *   "SummaryHud": { "Enabled": true, "Position": "TopCenter", "OffsetY": 120, "TtlMs": 6000 },
 *   "Limits": { "MaxSessionsPerWorld": 60, "MaxPuppetsPerWorld": 40, "MaxStashesPerSection": 8 }
 * }
 * }</pre>
 */
public final class RpgStationsSettingsAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, RpgStationsSettingsAsset>> {

    /** The one fixed id every {@code Settings.json} decodes to, regardless of authored filename casing. */
    public static final String ID = "settings";

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private Boolean enabled;
    @Nullable private SummaryHud summaryHud;
    @Nullable private Limits limits;

    public static final AssetBuilderCodec<String, RpgStationsSettingsAsset> CODEC = AssetBuilderCodec.builder(
                    RpgStationsSettingsAsset.class,
                    RpgStationsSettingsAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id == null ? ID : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .documentation("Ignored - the settings asset id is always the fixed 'settings' id, not this key. Kept as a schema field for editor display only.").add()
            .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                    (a, v) -> a.enabled = v, a -> a.enabled, (a, parent) -> a.enabled = parent.enabled)
            .documentation("The engine master switch; false disables the whole RpgStations engine at the SettingsCatalog check. Reader-defaults to true.")
            .metadata(new UIEditorSectionStart("Engine"))
            .metadata(EditorSchema.defaultValue(true)).add()
            .appendInherited(new KeyedCodec<>("SummaryHud", SummaryHud.CODEC, false),
                    (a, v) -> a.summaryHud = v, a -> a.summaryHud, (a, parent) -> a.summaryHud = parent.summaryHud)
            .documentation("The end-of-session summary panel's layout and lifetime.")
            .metadata(new UIEditorSectionStart("Summary HUD")).add()
            .appendInherited(new KeyedCodec<>("Limits", Limits.CODEC, false),
                    (a, v) -> a.limits = v, a -> a.limits, (a, parent) -> a.limits = parent.limits)
            .documentation("Ceilings a server owner can set on what this engine is allowed to have live at once (sessions and puppets per world, placed-input stashes per chunk section), plus the unattended pass's per-world visit interval. Absent, or any ceiling leaf left null, means unlimited; the interval defaults to 1000ms.")
            .metadata(new UIEditorSectionStart("Limits")).add()
            .build();

    public RpgStationsSettingsAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static RpgStationsSettingsAsset of(@Nullable Boolean enabled, @Nullable SummaryHud summaryHud) {
        return of(enabled, summaryHud, null);
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static RpgStationsSettingsAsset of(@Nullable Boolean enabled, @Nullable SummaryHud summaryHud,
            @Nullable Limits limits) {
        RpgStationsSettingsAsset a = new RpgStationsSettingsAsset();
        a.id = ID;
        a.enabled = enabled;
        a.summaryHud = summaryHud;
        a.limits = limits;
        return a;
    }

    /**
     * The built-in, zero-authoring default (every leaf reader-defaulted, used before any asset
     * loads). Limits are absent by default: an unset ceiling is unlimited, so a server that never
     * touches this group behaves exactly as it did before the group existed.
     */
    @Nonnull
    public static RpgStationsSettingsAsset defaults() {
        return of(true, SummaryHud.of(true, null, null, null), null);
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    /** {@link #enabled}, reader-defaulted to {@code true} when null (the engine stays live by default). */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    @Nullable
    public SummaryHud getSummaryHud() {
        return summaryHud;
    }

    /** The per-world ceilings, or {@code null} when the owner authored none (everything unlimited). */
    @Nullable
    public Limits getLimits() {
        return limits;
    }

    /**
     * Ceilings on what the engine may have live at once - INDEPENDENT knobs, each nullable and
     * each meaning "unlimited" when absent, so an owner can cap one thing without implying
     * anything about the others - plus the unattended pass's per-world visit interval
     * ({@link #unattendedIntervalMs}, a pace knob rather than a ceiling, defaulting to
     * {@value #DEFAULT_UNATTENDED_INTERVAL_MS}ms when absent).
     *
     * <p>The session and puppet caps are scoped PER WORLD: the cost they protect against (session
     * ticking, replicated performer entities) is paid by the players and the tick loop of one
     * world, and a busy hub must not be able to starve a quiet one. The stash cap is scoped PER
     * CHUNK SECTION, because placed input is stored on the block's own chunk section and that is
     * the bound a per-section store can enforce.
     *
     * <p>They differ in what exceeding them DOES, which is a property of the thing being capped, not
     * a mode: a session and a placed-input stash are all-or-nothing, so those two deny the
     * interaction with a localized toast; a puppet is pure presentation, so that one silently
     * degrades to the in-body route the engine already falls back to whenever a spawn fails.
     */
    public static final class Limits {

        /** The {@link #unattendedIntervalMs} reader default: one unattended visit pass per world per second. */
        public static final long DEFAULT_UNATTENDED_INTERVAL_MS = 1000L;

        @Nullable protected Integer maxSessionsPerWorld;
        @Nullable protected Integer maxPuppetsPerWorld;
        @Nullable protected Integer maxStashesPerSection;
        @Nullable protected Long unattendedIntervalMs;
        @Nullable protected Integer retiredMaxCustodyClaimsPerWorld;

        public static final BuilderCodec<Limits> CODEC = BuilderCodec.builder(Limits.class, Limits::new)
                .appendInherited(new KeyedCodec<>("MaxSessionsPerWorld", Codec.INTEGER, false),
                        (o, v) -> o.maxSessionsPerWorld = v, o -> o.maxSessionsPerWorld,
                        (o, p) -> o.maxSessionsPerWorld = p.maxSessionsPerWorld)
                .documentation("The most work sessions that may run at once in ONE world. A press that would exceed it is denied with a localized toast, and the station is left untouched. Null (the default) means unlimited.")
                .addValidator(CodecWarnValidators.positive("Limits.MaxSessionsPerWorld should be positive.")).add()
                .appendInherited(new KeyedCodec<>("MaxPuppetsPerWorld", Codec.INTEGER, false),
                        (o, v) -> o.maxPuppetsPerWorld = v, o -> o.maxPuppetsPerWorld,
                        (o, p) -> o.maxPuppetsPerWorld = p.maxPuppetsPerWorld)
                .documentation("The most live puppets (an action's Puppet group: the spawned, network-replicated figure that performs the work in the player's place) that may exist at once in ONE world. Past it a session still starts and runs normally, it simply performs in the player's own body instead of spawning a puppet - the same graceful fallback a failed spawn already takes. Null (the default) means unlimited.")
                .addValidator(CodecWarnValidators.positive("Limits.MaxPuppetsPerWorld should be positive.")).add()
                .appendInherited(new KeyedCodec<>("MaxStashesPerSection", Codec.INTEGER, false),
                        (o, v) -> o.maxStashesPerSection = v, o -> o.maxStashesPerSection,
                        (o, p) -> o.maxStashesPerSection = p.maxStashesPerSection)
                .documentation("The most blocks in ONE chunk section (a 16x16x16 cube) that may hold placed station input at once. Placing into a station that already holds material always works (it tops the existing store up); only a placement that would CREATE a new one past the ceiling is denied, with a localized toast. Null (the default) means unlimited.")
                .addValidator(CodecWarnValidators.positive("Limits.MaxStashesPerSection should be positive.")).add()
                .appendInherited(new KeyedCodec<>("UnattendedIntervalMs", Codec.LONG, false),
                        (o, v) -> o.unattendedIntervalMs = v, o -> o.unattendedIntervalMs,
                        (o, p) -> o.unattendedIntervalMs = p.unattendedIntervalMs)
                .documentation("How often, in milliseconds, ONE world's unattended pass runs (visiting placed-input stations whose action authors Work.Unattended and settling any cycles world game time has earned them). Null (the default) means 1000. Raising it makes unattended stations settle in coarser bursts; the math is the same either way.")
                .addValidator(CodecWarnValidators.positive("Limits.UnattendedIntervalMs should be positive.")).add()
                .appendInherited(new KeyedCodec<>("MaxCustodyClaimsPerWorld", Codec.INTEGER, false),
                        (o, v) -> o.retiredMaxCustodyClaimsPerWorld = v, o -> o.retiredMaxCustodyClaimsPerWorld,
                        (o, p) -> o.retiredMaxCustodyClaimsPerWorld = p.retiredMaxCustodyClaimsPerWorld)
                .documentation("Retired and ignored. Placed input is stored on the block's own chunk section, so the bound is per section: author MaxStashesPerSection instead. A value here logs a warning naming that replacement and changes nothing.")
                .add()
                .build();

        @Nonnull
        public static Limits of(@Nullable Integer maxSessionsPerWorld, @Nullable Integer maxPuppetsPerWorld,
                @Nullable Integer maxStashesPerSection) {
            return of(maxSessionsPerWorld, maxPuppetsPerWorld, maxStashesPerSection, null);
        }

        @Nonnull
        public static Limits of(@Nullable Integer maxSessionsPerWorld, @Nullable Integer maxPuppetsPerWorld,
                @Nullable Integer maxStashesPerSection, @Nullable Long unattendedIntervalMs) {
            Limits l = new Limits();
            l.maxSessionsPerWorld = maxSessionsPerWorld;
            l.maxPuppetsPerWorld = maxPuppetsPerWorld;
            l.maxStashesPerSection = maxStashesPerSection;
            l.unattendedIntervalMs = unattendedIntervalMs;
            return l;
        }

        @Nullable
        public Integer getMaxSessionsPerWorld() {
            return maxSessionsPerWorld;
        }

        @Nullable
        public Integer getMaxPuppetsPerWorld() {
            return maxPuppetsPerWorld;
        }

        /** The per-chunk-section placed-input stash ceiling, or null for unlimited. */
        @Nullable
        public Integer getMaxStashesPerSection() {
            return maxStashesPerSection;
        }

        /** The per-world unattended-pass interval in ms, or null for the reader default. */
        @Nullable
        public Long getUnattendedIntervalMs() {
            return unattendedIntervalMs;
        }

        /** {@link #unattendedIntervalMs} with the {@value #DEFAULT_UNATTENDED_INTERVAL_MS}ms reader default (a non-positive authored value reads as the default too). */
        public long effectiveUnattendedIntervalMs() {
            return unattendedIntervalMs != null && unattendedIntervalMs > 0
                    ? unattendedIntervalMs : DEFAULT_UNATTENDED_INTERVAL_MS;
        }

        /**
         * The retired {@code MaxCustodyClaimsPerWorld} leaf's authored value, kept decoded ONLY so
         * the settings fold can warn once naming {@code MaxStashesPerSection}; nothing enforces it.
         */
        @Nullable
        public Integer getRetiredMaxCustodyClaimsPerWorld() {
            return retiredMaxCustodyClaimsPerWorld;
        }

        /**
         * PURE: is {@code max} an active ceiling that {@code currentCount} has already reached? The
         * ONE predicate all three limits are enforced through, so "unlimited" has exactly one
         * spelling (null, or a non-positive number an owner may have typed by mistake - a ceiling of
         * zero or less would otherwise mean "this feature is off", which is what the engine's own
         * Enabled switch is for).
         *
         * <p>{@code currentCount} is a SUPPLIER, not a number, so a caller never pays for counting
         * what a limit nobody set was going to ignore anyway - the unlimited default costs one null
         * check at each enforcement point.
         */
        public static boolean atCapacity(@Nullable Integer max, @Nonnull IntSupplier currentCount) {
            return max != null && max > 0 && currentCount.getAsInt() >= max;
        }
    }

    /** The panel layout + lifetime for {@code ui.StationSummaryHud}. */
    public static final class SummaryHud {
        @Nullable protected Boolean enabled;
        @Nullable protected String position;
        @Nullable protected Integer offsetX;
        @Nullable protected Integer offsetY;
        @Nullable protected Long ttlMs;

        public static final BuilderCodec<SummaryHud> CODEC = BuilderCodec.builder(SummaryHud.class, SummaryHud::new)
                .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                        (o, v) -> o.enabled = v, o -> o.enabled, (o, p) -> o.enabled = p.enabled)
                .metadata(EditorSchema.defaultValue(true))
                .documentation("Whether the end-of-session summary panel shows at all; defaults to true.").add()
                .appendInherited(new KeyedCodec<>("Position", Codec.STRING, false),
                        (o, v) -> o.position = v, o -> o.position, (o, p) -> o.position = p.position)
                .documentation("A shared-library HudPosition preset id, authored PascalCase like every other id in this schema (e.g. 'TopCenter'); the legacy SCREAMING_SNAKE spelling ('TOP_CENTER') still resolves since matching is case- and underscore-insensitive. Null or unknown falls back to the HUD's own default.")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:hud-positions"))).add()
                .appendInherited(new KeyedCodec<>("OffsetX", Codec.INTEGER, false),
                        (o, v) -> o.offsetX = v, o -> o.offsetX, (o, p) -> o.offsetX = p.offsetX)
                .documentation("Horizontal pixel offset from the chosen Position preset; defaults to 0.").add()
                .appendInherited(new KeyedCodec<>("OffsetY", Codec.INTEGER, false),
                        (o, v) -> o.offsetY = v, o -> o.offsetY, (o, p) -> o.offsetY = p.offsetY)
                .documentation("Vertical pixel offset from the chosen Position preset.").add()
                .appendInherited(new KeyedCodec<>("TtlMs", Codec.LONG, false),
                        (o, v) -> o.ttlMs = v, o -> o.ttlMs, (o, p) -> o.ttlMs = p.ttlMs)
                .documentation("How long the summary panel stays on screen, in milliseconds.")
                .addValidator(CodecWarnValidators.positive("SummaryHud.TtlMs should be positive.")).add()
                .build();

        @Nonnull
        public static SummaryHud of(@Nullable Boolean enabled, @Nullable String position,
                @Nullable Integer offsetY, @Nullable Long ttlMs) {
            return of(enabled, position, null, offsetY, ttlMs);
        }

        @Nonnull
        public static SummaryHud of(@Nullable Boolean enabled, @Nullable String position,
                @Nullable Integer offsetX, @Nullable Integer offsetY, @Nullable Long ttlMs) {
            SummaryHud h = new SummaryHud();
            h.enabled = enabled;
            h.position = position;
            h.offsetX = offsetX;
            h.offsetY = offsetY;
            h.ttlMs = ttlMs;
            return h;
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        /** A {@code common.ui.hud.HudPosition}-preset id (e.g. {@code "TopCenter"}, SCREAMING_SNAKE alias accepted); null/unknown falls back to the HUD's own default. */
        @Nullable
        public String getPosition() {
            return position;
        }

        /** Horizontal pixel offset from the chosen {@link #position} preset; null = the HUD's own default. */
        @Nullable
        public Integer getOffsetX() {
            return offsetX;
        }

        @Nullable
        public Integer getOffsetY() {
            return offsetY;
        }

        @Nullable
        public Long getTtlMs() {
            return ttlMs;
        }
    }
}
