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
 * TWO guards over the same walk, both about the shipped documentation surface: every
 * {@code KeyedCodec} leaf on the seven Pattern A asset types (and every nested {@code BuilderCodec}
 * group they reach) must carry a non-blank {@code .documentation(...)} string, AND no such string may
 * narrate this project's own internal process. Both matter because a documentation string ships in
 * the jar schema and is the source of the generated schema reference, so it is read by pack authors
 * who have no way to decode an internal label.
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

    /**
     * Fragments that mean something to whoever BUILT this schema and nothing at all to whoever
     * AUTHORS content against it. A {@code .documentation(...)} string ships in the jar's schema and
     * is the source of the generated schema reference, so it is a PUBLIC surface: it may explain what
     * a leaf does, but never how the project got there.
     */
    private static final String[] PROCESS_NARRATION = {
            "decision ", "ruling ", "critique", "handoff", "maintainer",
            " wave", "wave ", "leg ", " m1 ", " m2 ", " m3 ", " m4 ", " m5 ",
            "(m1", "(m2", "(m3", "(m4", "(m5", "m2 rule", "m3 fix", ".claude/",
    };

    @Test
    void everyLeafOnTheSevenPatternATypesAndTheirNestedGroupsIsDocumented() {
        List<String> undocumented = new ArrayList<>();
        List<String> narrated = new ArrayList<>();
        walkEveryPatternAType(undocumented, narrated);

        assertTrue(undocumented.isEmpty(),
                "Codec leaves missing .documentation(\"...\"):\n" + String.join("\n", undocumented));
    }

    /**
     * The class of finding, not the instances: a leaf's shipped documentation must not narrate the
     * project's own process. Caught here rather than by review because these strings reach the public
     * schema reference, where a reader has no way to decode an internal label.
     */
    @Test
    void noLeafDocumentationNarratesInternalProcess() {
        List<String> undocumented = new ArrayList<>();
        List<String> narrated = new ArrayList<>();
        walkEveryPatternAType(undocumented, narrated);

        assertTrue(narrated.isEmpty(),
                "Codec documentation strings carrying internal process narration (they ship in the jar"
                        + " schema and the generated schema reference, so they are PUBLIC):\n"
                        + String.join("\n", narrated));
    }

    private static void walkEveryPatternAType(List<String> undocumented, List<String> narrated) {
        Set<BuilderCodec<?>> visited = new HashSet<>();
        walk("StationAsset", StationAsset.CODEC, undocumented, narrated, visited);
        walk("ActionAsset", ActionAsset.CODEC, undocumented, narrated, visited);
        walk("LootableAsset", LootableAsset.CODEC, undocumented, narrated, visited);
        walk("RollPool", RollPool.CODEC, undocumented, narrated, visited);
        walk("FlairAsset", FlairAsset.CODEC, undocumented, narrated, visited);
        walk("ExtensionAsset", ExtensionAsset.CODEC, undocumented, narrated, visited);
        walk("RpgStationsSettingsAsset", RpgStationsSettingsAsset.CODEC, undocumented, narrated, visited);
    }

    private static void walk(String path, BuilderCodec<?> codec, List<String> undocumented,
            List<String> narrated, Set<BuilderCodec<?>> visited) {
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
                } else {
                    String lower = doc.toLowerCase(java.util.Locale.ROOT);
                    for (String fragment : PROCESS_NARRATION) {
                        if (lower.contains(fragment)) {
                            narrated.add(here + " contains \"" + fragment.trim() + "\": " + doc);
                        }
                    }
                }
                BuilderCodec<?> nested = unwrapToBuilderCodec(field.getCodec().getChildCodec());
                if (nested != null) {
                    walk(here, nested, undocumented, narrated, visited);
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
