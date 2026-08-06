package com.ziggfreed.rpgstations.api;

import java.util.Collection;

import javax.annotation.Nonnull;

/**
 * Everything a {@link ValidationHook} is allowed to look at: the folded
 * content of one full validate pass, projected read-only. Handed in fresh per pass; every
 * collection is an immutable snapshot, so a hook may iterate it as often as it likes and may
 * retain it (though there is no reason to - the next pass hands over a newer one).
 *
 * <p>This exists so a mod that owns a factor family or a contribution channel can enforce its OWN
 * composition rules against station content, WITHOUT this engine hosting a check that names a
 * foreign vocabulary. The engine keeps only the checks it can state in its own terms.
 */
public interface ValidationScope {

    /**
     * Every authored loot roll in the pass, from every source (a station's own {@code Loot}, an
     * action's, a step's {@code Roll} phase, a standalone lootable, and an extension's appended
     * rolls), each carrying the id and authoring path it came from.
     */
    @Nonnull
    Collection<RollView> allRolls();

    /** Every currently-folded station, the same projection {@code RpgStationsApi.stations()} returns. */
    @Nonnull
    Collection<StationView> stations();
}
