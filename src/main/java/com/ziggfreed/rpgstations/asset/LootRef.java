package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * The ONE loot-reference group (scope-2 design section 1.3, DRY principle 1): references to shared
 * {@link LootableAsset} tables and/or inline {@link Roll}s, both optional and independently
 * composable. Replaces the two divergent loot-reference vocabularies E1 found - the old
 * {@code StationAsset.Loot {Tables, Rolls}} and the old {@code StationStep.RollGroup {Lootable,
 * Rolls}} (singular). Reused at every site a station or a step or an extension references loot:
 * {@code StationAsset.Loot}, {@code StationStep.Roll}, {@code ExtensionAsset.Loot}.
 *
 * <p>{@link #lootables} are referenced {@link LootableAsset} ids (case-insensitive at resolve);
 * {@link #rolls} are inline rolls authored directly at the site (in addition to any tables). A
 * ref with both resolves both (the tables' rolls AND the inline rolls all evaluate).
 */
public final class LootRef {

    @Nullable protected String[] lootables;
    @Nullable protected Roll[] rolls;

    public static final BuilderCodec<LootRef> CODEC = BuilderCodec.builder(LootRef.class, LootRef::new)
            .appendInherited(new KeyedCodec<>("Lootables",
                            new ArrayCodec<>(LootableAsset.CHILD_ASSET_CODEC, String[]::new), false),
                    (o, v) -> o.lootables = v, o -> o.lootables, (o, p) -> o.lootables = p.lootables)
            .documentation("Referenced LootableAsset ids (case-insensitive at resolve); each table's Rolls evaluate. "
                    + "An entry may also be an INLINE lootable body, optionally with its own Parent - but a "
                    + "LootableAsset's single Rolls array REPLACES the parent's wholesale, it never appends to it, "
                    + "so use an ExtensionAsset (or the sibling inline Rolls leaf here) to ADD rolls to a shared table.").add()
            .appendInherited(new KeyedCodec<>("Rolls", new ArrayCodec<>(Roll.CODEC, Roll[]::new), false),
                    (o, v) -> o.rolls = v, o -> o.rolls, (o, p) -> o.rolls = p.rolls)
            .documentation("Inline Rolls authored directly at this site, in addition to any referenced Lootables.").add()
            .build();

    public LootRef() {
    }

    @Nonnull
    public static LootRef of(@Nullable String[] lootables, @Nullable Roll[] rolls) {
        LootRef l = new LootRef();
        l.lootables = lootables;
        l.rolls = rolls;
        return l;
    }

    /** Referenced {@link LootableAsset} ids (case-insensitive at resolve); null = none. */
    @Nullable
    public String[] getLootables() {
        return lootables;
    }

    /** Inline rolls authored directly at this site (in addition to any {@link #lootables}); null = none. */
    @Nullable
    public Roll[] getRolls() {
        return rolls;
    }

    /** True when neither leaf is authored (an empty ref is a no-op, same as an absent one). */
    public boolean isEmpty() {
        return (lootables == null || lootables.length == 0) && (rolls == null || rolls.length == 0);
    }
}
