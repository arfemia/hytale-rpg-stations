package com.ziggfreed.rpgstations.asset;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.codec.ContainedAssetCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * A named, reusable conditional-lootable table: {@code Server/RpgStations/Lootables/<Name>.json}
 * (Pattern A, id = lowercased filename), body {@code { "Rolls": [ <Roll>, ... ] } } - design
 * section 4.5.1. Referenced by a {@link LootRef#getLootables()} entry (a station, action, or
 * extension may combine any number of shared tables with its own inline {@code Rolls}); a table
 * with no consumer is simply dormant content.
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
                    // the engine's asset key is the verbatim filename, every consumer (Loot.Lootables
                    // references) authors lowercase.
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .documentation("Ignored - the lootable id comes from the asset filename, not this key. Kept as a schema field for editor display only.").add()
            .appendInherited(new KeyedCodec<>("Rolls", new ArrayCodec<>(Roll.CODEC, Roll[]::new), false),
                    (a, v) -> a.rolls = v, a -> a.rolls, (a, parent) -> a.rolls = parent.rolls)
            .documentation("The conditional-lootable rolls this reusable table contributes.").add()
            .build();

    /**
     * The engine's own contained-asset codec for this type: any leaf declaring it accepts EITHER a
     * plain {@code "<lootableId>"} string reference OR an inline anonymous body (registered as a
     * generated child asset in this same store), including a nested {@code "Parent": "<lootableId>"}
     * that inherits from a named table.
     *
     * <p><b>Inline bodies REPLACE, they never delta.</b> This asset's only content leaf is
     * {@code Rolls}, which inherits wholesale on omission and is replaced wholesale when authored -
     * so {@code {"Parent":"sawmillfinds","Rolls":[...]}} discards every base roll rather than adding
     * to them. To ADD rolls to an existing table, ship an {@link ExtensionAsset} targeting it (its
     * {@code Rolls} payload appends) or author the extra rolls inline beside the reference on the
     * consuming {@link LootRef}.
     */
    @Nonnull
    public static final Codec<String> CHILD_ASSET_CODEC =
            new ContainedAssetCodec<>(LootableAsset.class, CODEC);

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
