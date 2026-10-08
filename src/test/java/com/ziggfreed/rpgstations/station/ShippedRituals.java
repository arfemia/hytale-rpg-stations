package com.ziggfreed.rpgstations.station;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;

/**
 * The Disenchanting Tables' shipped ritual pair, decoded through the real codec the way the action
 * store inherits it: the lesser table's {@code Disenchant}, and the greater table's
 * {@code Disenchant_Greater} as a {@code Parent} child over it. For the tests that read the shipped
 * ritual itself (its sockets, its program, what its double holds) rather than a fixture.
 */
final class ShippedRituals {

    private static final String LESSER = "Disenchant";
    private static final String GREATER = "Disenchant_Greater";

    private ShippedRituals() {
    }

    /** The lesser table's ritual. */
    @Nonnull
    static ActionDef lesser() throws Exception {
        return decode(LESSER, null, null).getBody();
    }

    /** The greater table's ritual, inheriting every leaf it does not author from the lesser one. */
    @Nonnull
    static ActionDef greater() throws Exception {
        return decode(GREATER, decode(LESSER, null, null), LESSER).getBody();
    }

    @Nonnull
    private static ActionAsset decode(@Nonnull String id, @Nullable ActionAsset parent, @Nullable String parentId)
            throws Exception {
        Path file = Path.of("src", "main", "resources", "Server", "RpgStations", "Actions", id + ".json");
        return ActionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(Files.readString(file, StandardCharsets.UTF_8)), parent,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ActionAsset.class, id, parentId)));
    }
}
