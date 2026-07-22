package com.ziggfreed.rpgstations.asset;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * RpgStations' single engine-settings asset - design section 4.6: {@code
 * Server/RpgStations/Settings/Settings.json}, ONE fixed id ({@link #ID}), jar default (this
 * jar ships {@code Settings.json} with every leaf explicit) plus pack-overridable (a pack layers
 * its own {@code Settings.json} over the jar default via the normal Pattern-A store merge - the
 * mob-scaling asset-codec-config paradigm; no loose Gson blob, no owner-file write-back in
 * 1.0.0). Two independently composable, orthogonal knobs.
 *
 * <pre>{@code
 * {
 *   "Enabled": true,
 *   "SummaryHud": { "Enabled": true, "Position": "top_center", "OffsetY": 120, "TtlMs": 6000 }
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
            .add()
            .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                    (a, v) -> a.enabled = v, a -> a.enabled, (a, parent) -> a.enabled = parent.enabled)
            .add()
            .appendInherited(new KeyedCodec<>("SummaryHud", SummaryHud.CODEC, false),
                    (a, v) -> a.summaryHud = v, a -> a.summaryHud, (a, parent) -> a.summaryHud = parent.summaryHud)
            .add()
            .build();

    public RpgStationsSettingsAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static RpgStationsSettingsAsset of(@Nullable Boolean enabled, @Nullable SummaryHud summaryHud) {
        RpgStationsSettingsAsset a = new RpgStationsSettingsAsset();
        a.id = ID;
        a.enabled = enabled;
        a.summaryHud = summaryHud;
        return a;
    }

    /** The built-in, zero-authoring default (every leaf reader-defaulted, used before any asset loads). */
    @Nonnull
    public static RpgStationsSettingsAsset defaults() {
        return of(true, SummaryHud.of(true, null, null, null));
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

    /** The panel layout + lifetime for {@code ui.StationSummaryHud}. */
    public static final class SummaryHud {
        @Nullable protected Boolean enabled;
        @Nullable protected String position;
        @Nullable protected Integer offsetY;
        @Nullable protected Long ttlMs;

        public static final BuilderCodec<SummaryHud> CODEC = BuilderCodec.builder(SummaryHud.class, SummaryHud::new)
                .appendInherited(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                        (o, v) -> o.enabled = v, o -> o.enabled, (o, p) -> o.enabled = p.enabled).add()
                .appendInherited(new KeyedCodec<>("Position", Codec.STRING, false),
                        (o, v) -> o.position = v, o -> o.position, (o, p) -> o.position = p.position).add()
                .appendInherited(new KeyedCodec<>("OffsetY", Codec.INTEGER, false),
                        (o, v) -> o.offsetY = v, o -> o.offsetY, (o, p) -> o.offsetY = p.offsetY).add()
                .appendInherited(new KeyedCodec<>("TtlMs", Codec.LONG, false),
                        (o, v) -> o.ttlMs = v, o -> o.ttlMs, (o, p) -> o.ttlMs = p.ttlMs).add()
                .build();

        @Nonnull
        public static SummaryHud of(@Nullable Boolean enabled, @Nullable String position,
                @Nullable Integer offsetY, @Nullable Long ttlMs) {
            SummaryHud h = new SummaryHud();
            h.enabled = enabled;
            h.position = position;
            h.offsetY = offsetY;
            h.ttlMs = ttlMs;
            return h;
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        /** A {@code common.ui.hud.HudPosition}-preset id (e.g. {@code "top_center"}); null/unknown falls back to the HUD's own default. */
        @Nullable
        public String getPosition() {
            return position;
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
