package com.ziggfreed.rpgstations.api;

import java.util.Collection;

import javax.annotation.Nonnull;

/**
 * The RPG Stations extension surface (design section 3.2): typed request/response registries a
 * request/response consumer (any mod, e.g. the MMO Skill Tree bridge) reaches through a single
 * injected implementation the RpgStations plugin installs at {@code setup()} - mirrors the MMO's
 * own {@code MMOSkillTreeAPI} singleton discipline. Paired with the observe-only native events
 * in {@link com.ziggfreed.rpgstations.api.event} (station session/cycle lifecycle).
 *
 * <p>Everything reachable from here is FROZEN once RpgStations 1.0.0 releases; until then it is
 * free to reshape.
 */
public interface RpgStationsApi {

    /**
     * The one extensible numeric-factor vocabulary: conditional lootables ({@code Roll}
     * Conditions/Chance/Ladder), station start gates ({@code Requires.Conditions}), and (phase 2)
     * step conditions all evaluate over it.
     */
    @Nonnull
    FactorRegistry factors();

    /** The per-station cosmetic flair-unlock union registry (persistence stays with the registering mod). */
    @Nonnull
    FlairUnlockRegistry flairUnlocks();

    /** The end-of-session summary-panel extra-row + post-build theming registry. */
    @Nonnull
    SummaryEnricherRegistry summaryEnrichers();

    /** A read-only snapshot of every currently-folded station (objective target names, soft-warns, ...). */
    @Nonnull
    Collection<StationView> stations();

    /**
     * Installed by the RpgStations plugin at {@code setup()}. Not for external callers - a
     * third-party mod only ever calls {@link #get()}.
     */
    static void set(@Nonnull RpgStationsApi impl) {
        RpgStationsApiHolder.set(impl);
    }

    /**
     * The live extension surface. Throws {@link IllegalStateException} when RpgStations has not
     * finished {@code setup()} yet (or is simply not installed) - a caller MUST presence-check
     * the plugin first (design section 3.3's soft-detection contract; this method does not do
     * that detection itself).
     */
    @Nonnull
    static RpgStationsApi get() {
        return RpgStationsApiHolder.get();
    }
}
