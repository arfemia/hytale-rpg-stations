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
 * A named, reusable stat-roll table: {@code Server/RpgStations/RollPools/<Name>.json} (Pattern A,
 * id = lowercased filename), body {@code { "Entries": [ <StatRollEntry>, ... ] } } - design
 * section 9.5's composable roll model. Referenced by a {@code Stamp.Stats.Pool} leaf (an inline
 * {@code Stamp.Stats.Entries} array is the alternative authoring route - both share the exact same
 * {@link StatRollEntry} shape, resolved through {@code station.StampCapEngine}). Mirrors
 * {@link LootableAsset}'s exact shape/registration pattern.
 */
public final class RollPool implements JsonAssetWithMap<String, DefaultAssetMap<String, RollPool>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private StatRollEntry[] entries;

    public static final AssetBuilderCodec<String, RollPool> CODEC = AssetBuilderCodec.builder(
                    RollPool.class,
                    RollPool::new,
                    Codec.STRING,
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .appendInherited(new KeyedCodec<>("Entries", new ArrayCodec<>(StatRollEntry.CODEC, StatRollEntry[]::new), false),
                    (a, v) -> a.entries = v, a -> a.entries, (a, parent) -> a.entries = parent.entries)
            .add()
            .build();

    public RollPool() {
    }

    @Nonnull
    public static RollPool of(@Nonnull String id, @Nullable StatRollEntry[] entries) {
        RollPool p = new RollPool();
        p.id = id;
        p.entries = entries;
        return p;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public StatRollEntry[] getEntries() {
        return entries;
    }
}
