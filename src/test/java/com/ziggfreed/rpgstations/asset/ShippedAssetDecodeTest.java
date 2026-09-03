package com.ziggfreed.rpgstations.asset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.stamp.RollPoolAsset;
import com.ziggfreed.common.progress.asset.ObjectiveKindAsset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Decodes every {@code RpgStations} asset this jar SHIPS (and every one the companion pack ships,
 * when that repo sits beside this one) through the exact codec the server reads it with, so a file
 * the running server would reject can never leave the build green.
 *
 * <p><b>Why a decode and not a scan.</b> The other asset tests decode FIXTURES, and the schema
 * tests walk CODEC objects; neither ever opens a shipped file, so a file that parses as JSON and
 * satisfies every text-level rule can still fail its codec at server start and take the whole asset
 * with it. The failure this guards is not hypothetical: whether a key a {@code BuilderCodec} would
 * ignore is also ignored inside a map-shaped group depends on which map codec backs that group.
 * {@code InheritMapCodec} skips a {@code $}-key (every moment/flair map: an action's
 * {@code Moments}, {@code Flairs}, {@code Flairs[].Moments}, {@code FlairAsset.Moments}); the
 * engine's own {@code MapCodec} does not, because every key there is a map key whose value is handed
 * straight to the value codec. Editorial keys ({@code $Comment} and friends) therefore still belong
 * inside an object with named fields rather than directly inside an {@code Anchors} /
 * {@code PerStat} / {@code Tags} group - the list {@code asset/CLAUDE.md} keeps current.
 *
 * <p>Two jar roots are walked: this mod's own {@code Server/RpgStations} stores, and the files it
 * ships into the SHARED library's {@code Server/ZiggfreedCommon} stores (its lootables and the two
 * objective kinds it fires), decoded through the library's codecs the same way. A file is judged by
 * the store folder directly under its root, so a store may group its files in a sub-folder (the
 * objective kinds sit under {@code ObjectiveKinds/RpgStations/}) without that grouping reading as an
 * unknown store. An unmapped store folder under a scanned root FAILS rather than being skipped, so
 * a new asset type is covered here the day it ships its first file; a mapped folder holding zero
 * files is legal (a registered store can be empty in a given release scope), because only files are
 * ever judged. The optional roots are not declared Gradle task inputs, so an edit touching only one
 * of them can leave this task {@code UP-TO-DATE} locally: pass {@code --rerun-tasks} when such a
 * file is the only thing that moved.
 */
public class ShippedAssetDecodeTest {

    /** Decodes one file's body under {@code assetKey} (the filename), throwing whatever the codec throws. */
    @FunctionalInterface
    private interface AssetDecoder {
        Object decode(String body, String assetKey) throws Exception;
    }

    /** The asset-store folder name (directly under a scanned root) -> its registered codec. */
    private static final Map<String, AssetDecoder> DECODERS = Map.ofEntries(Map.entry(
            "Stations", (body, key) -> StationAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(StationAsset.class, key))),
            Map.entry("Actions", (body, key) -> ActionAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(ActionAsset.class, key))),
            Map.entry("Extensions", (body, key) -> ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(ExtensionAsset.class, key))),
            Map.entry("Lootables", (body, key) -> LootableAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(LootableAsset.class, key))),
            Map.entry("RollPools", (body, key) -> RollPoolAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(RollPoolAsset.class, key))),
            Map.entry("ObjectiveKinds", (body, key) -> ObjectiveKindAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(ObjectiveKindAsset.class, key))),
            Map.entry("Flairs", (body, key) -> FlairAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(FlairAsset.class, key))),
            Map.entry("Patterns", (body, key) -> StructurePatternAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(StructurePatternAsset.class, key))),
            Map.entry("Settings", (body, key) -> RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(body), null, info(RpgStationsSettingsAsset.class, key))));

    /** This jar's own shipped assets: its own stores, and what it ships into the shared library's stores. */
    private static final List<Path> JAR_ROOTS = List.of(
            Path.of("src", "main", "resources", "Server", "RpgStations"),
            Path.of("src", "main", "resources", "Server", "ZiggfreedCommon"));

    /**
     * This repo's own held-back content mirror (a byte-exact mirror of {@code src/main/resources},
     * outside every Gradle resource root): always present in a checkout, and decoded with the same
     * rigor as the shipped set so a later restore ships pre-verified.
     */
    private static final Path HELD_ROOT = Path.of("unreleased", "Server", "RpgStations");

    /**
     * The companion pack's assets, scanned only when that repo is checked out beside this one (the
     * same "cover it when it is there" rule the comment-hygiene sweeps use): a standalone clone of
     * this mod still runs a full green build.
     */
    private static final List<Path> OPTIONAL_ROOTS = List.of(
            HELD_ROOT,
            Path.of("..", "..", "content-packs", "skill-stations-pack", "Server", "RpgStations"),
            Path.of("..", "..", "content-packs", "skill-stations-pack", "unreleased", "Server", "RpgStations"));

    private static AssetExtraInfo<String> info(Class<? extends JsonAsset<String>> assetClass, String assetKey) {
        return new AssetExtraInfo<>(new AssetExtraInfo.Data(assetClass, assetKey, null));
    }

    @Test
    void everyShippedAsset_decodesThroughItsOwnCodec() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path root : JAR_ROOTS) {
            int decoded = decodeRoot(root, failures);
            assertTrue(decoded > 0, "no shipped RpgStations assets were found at " + root.toAbsolutePath());
        }
        for (Path root : OPTIONAL_ROOTS) {
            decodeRoot(root, failures);
        }
        if (!failures.isEmpty()) {
            fail("shipped assets that the server would fail to decode:\n  " + String.join("\n  ", failures));
        }
    }

    /**
     * Decodes every {@code .json} under {@code root}, appending one line per failure; returns the
     * file count. The store is the folder directly under {@code root}, so a store grouping its
     * files one level deeper is still judged by its own codec.
     */
    private static int decodeRoot(Path root, List<String> failures) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                count++;
                String folder = root.relativize(file).getName(0).toString();
                AssetDecoder decoder = DECODERS.get(folder);
                if (decoder == null) {
                    failures.add(file + " sits in unmapped folder '" + folder
                            + "' - add its codec to this test's DECODERS map");
                    continue;
                }
                String assetKey = file.getFileName().toString().replace(".json", "");
                String body = Files.readString(file, StandardCharsets.UTF_8);
                try {
                    assertNotNull(decoder.decode(body, assetKey), file + " decoded to null");
                } catch (Throwable t) {
                    failures.add(file + " -> " + describe(t));
                }
            }
        }
        return count;
    }

    /**
     * The whole cause chain on one line: a codec failure states "Failed to decode" at the top and
     * names the offending key/value only further down, so the top frame alone would send a reader
     * hunting through a large asset by hand.
     */
    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
        }
        return sb.toString();
    }
}
