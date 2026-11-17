package com.ziggfreed.rpgstations.station;

import com.ziggfreed.common.stats.StatAliases;

/**
 * This mod's stat ids renamed to Hytale's shape (one PascalCase word, no mod prefix): the Disenchanting Table's
 * proficiency, {@code RPG_Disenchanting_Proficiency} before, is {@link #DISENCHANTING_PROFICIENCY}. The old id is a
 * ziggfreed-common {@link StatAliases} entry onto the new, so a stack's saved roll and another mod's file naming it
 * read and write the new one, with one warning naming it. Registered once at setup.
 */
public final class RenamedStats {

    /** The Disenchanting Table's proficiency stat: points on its pace curve. */
    public static final String DISENCHANTING_PROFICIENCY = "DisenchantingProficiency";

    /** Its id before the rename, as items and packs saved it. */
    static final String RETIRED_DISENCHANTING_PROFICIENCY = "RPG_Disenchanting_Proficiency";

    private RenamedStats() {
    }

    /** The old id onto the new. Idempotent. */
    public static void register() {
        StatAliases.register(RETIRED_DISENCHANTING_PROFICIENCY, DISENCHANTING_PROFICIENCY);
    }
}
