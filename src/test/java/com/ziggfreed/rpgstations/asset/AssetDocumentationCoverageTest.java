package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.WrappedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.ziggfreed.common.codec.Vec3;

/**
 * Schema/DX review round, proposal P6 (decision 78): every {@code KeyedCodec} leaf on the seven
 * Pattern A asset types (and every nested {@code BuilderCodec} group they reach) must carry a
 * non-blank {@code .documentation(...)} string, so the codec-autogen'd docsite schema reference
 * ships with the whole authoring surface explained rather than a partially-blank table. Decision
 * 16 makes this a hard prerequisite for the docsite; decision 54 sequences this seam wave BEFORE
 * that docsite wave so the documentation is captured against the FINAL leaf names exactly once.
 *
 * <p>Pure public-API walk, no {@code java.lang.reflect}: {@link BuilderCodec#getEntries()} and
 * {@link BuilderField#getDocumentation()} are both public, and a nested group codec is reached by
 * unwrapping {@link BuilderField#getCodec()}'s child codec through any {@link WrappedCodec}
 * layers ({@code ArrayCodec}/{@code MapCodec}) down to a {@link BuilderCodec} (or a leaf codec
 * that isn't one, e.g. a plain {@code STRING}/{@code DOUBLE} primitive, or the ref-or-inline
 * {@code ContainedAssetCodec} used by {@code ActionDef.Ref}/{@code LootRef.Lootables}/
 * {@code Stamp.Stats.Pool} - none of those wrap a further {@code BuilderCodec}, so the walk
 * naturally stops there). A shared leaf codec ({@link Vec3#CODEC}, {@link Condition#CODEC},
 * {@link Presentation#CODEC}, ...) embedded at several owner sites is walked once via an
 * identity-based visited set, not once per owner - it is documented once, not per reference.
 */
class AssetDocumentationCoverageTest {

    @Test
    void everyLeafOnTheSevenPatternATypesAndTheirNestedGroupsIsDocumented() {
        List<String> undocumented = new ArrayList<>();
        Set<BuilderCodec<?>> visited = new HashSet<>();

        walk("StationAsset", StationAsset.CODEC, undocumented, visited);
        walk("ActionAsset", ActionAsset.CODEC, undocumented, visited);
        walk("LootableAsset", LootableAsset.CODEC, undocumented, visited);
        walk("RollPool", RollPool.CODEC, undocumented, visited);
        walk("FlairAsset", FlairAsset.CODEC, undocumented, visited);
        walk("ExtensionAsset", ExtensionAsset.CODEC, undocumented, visited);
        walk("RpgStationsSettingsAsset", RpgStationsSettingsAsset.CODEC, undocumented, visited);

        assertTrue(undocumented.isEmpty(),
                "Codec leaves missing .documentation(\"...\") (schema/DX review decision 78):\n"
                        + String.join("\n", undocumented));
    }

    private static void walk(String path, BuilderCodec<?> codec, List<String> undocumented,
            Set<BuilderCodec<?>> visited) {
        // Identity-based: a shared leaf codec (Vec3.CODEC, Condition.CODEC, Presentation.CODEC, ...)
        // reached from several owners is checked once, not re-flagged per reference site.
        if (!visited.add(codec)) {
            return;
        }
        for (var entry : codec.getEntries().entrySet()) {
            String key = entry.getKey();
            for (var field : entry.getValue()) {
                String here = path + "." + key;
                String doc = field.getDocumentation();
                if (doc == null || doc.isBlank()) {
                    undocumented.add(here);
                }
                BuilderCodec<?> nested = unwrapToBuilderCodec(field.getCodec().getChildCodec());
                if (nested != null) {
                    walk(here, nested, undocumented, visited);
                }
            }
        }
    }

    /**
     * Unwraps an {@code ArrayCodec}/{@code MapCodec} (or any other {@link WrappedCodec}) down to
     * its element type, stopping at the first {@link BuilderCodec} found (or returning
     * {@code null} when the leaf never bottoms out on one - a primitive, a plain string, or a
     * {@code ContainedAssetCodec} ref-or-inline leaf).
     */
    private static BuilderCodec<?> unwrapToBuilderCodec(Codec<?> codec) {
        Codec<?> current = codec;
        while (!(current instanceof BuilderCodec<?>) && current instanceof WrappedCodec<?> wrapped) {
            current = wrapped.getChildCodec();
        }
        return current instanceof BuilderCodec<?> builderCodec ? builderCodec : null;
    }
}
