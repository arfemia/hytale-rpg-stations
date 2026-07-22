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
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * A named, reusable conditional-lootable table: {@code Server/RpgStations/Lootables/<Name>.json}
 * (Pattern A, id = lowercased filename), body {@code { "Rolls": [ <Roll>, ... ] } } - design
 * section 4.5.1. Referenced by a {@link StationAsset.Loot#getTables()} entry (a station may
 * combine any number of shared tables with its own inline {@code Loot.Rolls}); a table with no
 * consumer is simply dormant content.
 */
public final class LootableAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, LootableAsset>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private Roll[] rolls;

    public static final AssetBuilderCodec<String, LootableAsset> CODEC = AssetBuilderCodec.builder(
                    LootableAsset.class,
                    LootableAsset::new,
                    Codec.STRING,
                    // Canonicalize to lowercase at decode, matching StationAsset's convention -
                    // the engine's asset key is the verbatim filename, every consumer (Loot.Tables
                    // references) authors lowercase.
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .appendInherited(new KeyedCodec<>("Rolls", new ArrayCodec<>(Roll.CODEC, Roll[]::new), false),
                    (a, v) -> a.rolls = v, a -> a.rolls, (a, parent) -> a.rolls = parent.rolls)
            .add()
            .build();

    public LootableAsset() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static LootableAsset of(@Nonnull String id, @Nullable Roll[] rolls) {
        LootableAsset a = new LootableAsset();
        a.id = id;
        a.rolls = rolls;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public Roll[] getRolls() {
        return rolls;
    }
}
