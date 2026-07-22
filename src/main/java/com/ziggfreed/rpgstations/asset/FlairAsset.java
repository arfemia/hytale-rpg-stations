package com.ziggfreed.rpgstations.asset;

import java.util.LinkedHashMap;
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
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

/**
 * A standalone, ANY-mod-authorable cosmetic flair layer (design section 9.6, phase 2 leg F): the
 * asset-driven half of the open flair/moment vocabulary. Loaded from
 * {@code Server/RpgStations/Flairs/<Name>.json} (Pattern A, id = lowercased filename, mirrors
 * {@link LootableAsset}/{@link RollPool}'s exact registration shape). Folded ONTO the per-station
 * flair map at catalog time ({@code station.FlairCatalog#effectiveFlairsFor}) - a station's own
 * inline {@link StationAsset.Flair} entries remain a pure authoring convenience, and a
 * {@code FlairAsset} from ANY installed pack/mod merges in under ITS OWN id as the flair id.
 * Nothing hardcodes a flair OR a moment id anywhere in Java; a grantor unlocks this asset's
 * {@link #getId()} for a player (any {@code FlairUnlockProvider}), and {@link #getMoments()} is
 * consulted per moment id exactly like a station's own inline flair.
 *
 * <pre>{@code
 * { "Stations": ["sawmill"], "Moments": { "swing": { "Particles": "Petal_Burst" },
 *                                         "step:enhance:stamp": { "Sound": "SFX_Choir_Hit" } } }
 * }</pre>
 *
 * <p>{@link #getStations()} {@code null} (or empty) means "applies to every station" - the design's
 * own "Stations null = applies to all" wording. {@link #getMoments()} is keyed by an ENGINE-EMITTED
 * moment id ({@code cycle}/{@code swing}/{@code impact}/{@code rare_find}/{@code completion}, or a
 * per-step {@code step:<actionId>:<stepId>} - see {@code station.StationFlairs}) or any FUTURE
 * moment id a newer engine emits; an unrecognized key warns at audit ({@code
 * station.StationValidator}), never errors, so an older-authored pack never breaks against a
 * newer engine.
 */
public final class FlairAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, FlairAsset>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String[] stations;
    @Nullable private Map<String, Presentation> moments;

    public static final AssetBuilderCodec<String, FlairAsset> CODEC = AssetBuilderCodec.builder(
                    FlairAsset.class,
                    FlairAsset::new,
                    Codec.STRING,
                    // Canonicalize to lowercase at decode, matching every other Pattern A store in
                    // this mod - the engine's asset key is the verbatim filename, every consumer
                    // (a grantor unlocking this id) authors lowercase.
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .appendInherited(new KeyedCodec<>("Stations", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                    (a, v) -> a.stations = v, a -> a.stations, (a, p) -> a.stations = p.stations)
            .add()
            .appendInherited(new KeyedCodec<>("Moments",
                            new MapCodec<>(Presentation.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.moments = v, a -> a.moments, (a, p) -> a.moments = p.moments)
            .add()
            .build();

    public FlairAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static FlairAsset of(@Nonnull String id, @Nullable String[] stations,
            @Nullable Map<String, Presentation> moments) {
        FlairAsset a = new FlairAsset();
        a.id = id;
        a.stations = stations;
        a.moments = moments;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /** Station ids this flair applies to; {@code null}/empty = every station. */
    @Nullable
    public String[] getStations() {
        return stations;
    }

    /** Moment id ({@code cycle}/{@code swing}/.../{@code step:<actionId>:<stepId>}) -> Presentation. */
    @Nullable
    public Map<String, Presentation> getMoments() {
        return moments;
    }

    /** Whether this flair applies to {@code stationId} (case-insensitive); null/empty {@link #stations} = all. */
    public boolean appliesTo(@Nonnull String stationId) {
        if (stations == null || stations.length == 0) {
            return true;
        }
        for (String s : stations) {
            if (s != null && s.equalsIgnoreCase(stationId)) {
                return true;
            }
        }
        return false;
    }
}
