package com.ziggfreed.rpgstations.api;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * The RPG Stations extension surface: typed request/response registries a
 * consumer (any mod) reaches through a single injected implementation the RpgStations plugin
 * installs at {@code setup()}. Paired with the observe-only native events in
 * {@link com.ziggfreed.rpgstations.api.event} (station session/cycle lifecycle).
 *
 * <p>Everything reachable from here is FROZEN once RpgStations 1.0.0 releases; until then it is
 * free to reshape. See {@code api/CLAUDE.md}'s "Additive growth policy" section for the exact
 * rule a POST-1.0.0 addition must follow.
 */
public interface RpgStationsApi {

    /**
     * The api surface version, bumped additively whenever a growth-policy-compliant addition
     * lands (a new default-bodied method, a new event class, a new additive event getter) - never
     * on a signature change, since the frozen contract permits none. A consumer that cares which
     * optional members exist can branch on this instead of reflecting on the interface. See
     * {@code api/CLAUDE.md}'s "Additive growth policy" section.
     */
    int apiVersion();

    /**
     * The one extensible numeric-factor vocabulary: conditional lootables ({@code Roll}
     * Conditions/Chance/Ladder), station start gates ({@code Requires.Conditions}), and step
     * conditions all evaluate over it.
     */
    @Nonnull
    FactorRegistry factors();

    /**
     * The write-side twin of {@link #factors()}: the declaration-only channel vocabulary a
     * station's {@code Contributions} post to. The engine resolves nothing here - it forwards
     * {@link StationContribution}s on the cycle event and lets the channel's owner interpret them.
     */
    @Nonnull
    ContributionChannelRegistry channels();

    /**
     * Third-party content checks that run inside this engine's own full validate pass, so a mod
     * that owns a factor family or a contribution channel keeps its composition rules with the
     * vocabulary instead of hardcoding them here. Advisory only, try-guarded, never blocking.
     */
    @Nonnull
    ValidationHookRegistry validationHooks();

    /** The per-station cosmetic flair-unlock union registry (persistence stays with the registering mod). */
    @Nonnull
    FlairUnlockRegistry flairUnlocks();

    /** The end-of-session summary-panel extra-row + post-build theming registry. */
    @Nonnull
    SummaryEnricherRegistry summaryEnrichers();

    /**
     * The Stamp step's {@code Stats}-leaf delegate registry: a single active
     * {@link EnhanceStamper} slot, last-registration-wins.
     */
    @Nonnull
    EnhanceStamperRegistry enhanceStampers();

    /** A read-only snapshot of every currently-folded station (objective target names, soft-warns, ...). */
    @Nonnull
    Collection<StationView> stations();

    /**
     * How many stations are currently folded - the cheap answer for a caller that only wants a
     * count or a presence check ({@code stationCount() > 0}).
     *
     * <p>{@link #stations()} builds a fresh {@link StationView} per station, and each of those
     * eagerly resolves the station's actions and merges its contribution and flair sets, so
     * answering "is anything installed" through it does real work per call. Station content cannot
     * appear after the asset load, and this counts the folded catalog directly.
     *
     * <p>Default-bodied per the additive growth policy; the shipped implementation overrides it with
     * the direct catalog size and never materializes a view.
     */
    default int stationCount() {
        return stations().size();
    }

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
     * the plugin first; this method does not do that detection itself.
     */
    @Nonnull
    static RpgStationsApi get() {
        return RpgStationsApiHolder.get();
    }

    /**
     * True once RpgStations has finished {@code setup()} and installed its implementation via
     * {@link #set}. Never throws.
     *
     * <p><b>This does NOT substitute for the {@code PluginManager} presence check a consumer must
     * run first.</b> {@code RpgStationsApi} and every other type in this package are
     * classloader-unresolvable exactly when RpgStations is absent or disabled (an
     * {@code OptionalDependencies} entry only grants classloader visibility while the dependency
     * plugin is actually loaded); calling this method AT ALL requires the calling class's bytecode
     * to already reference {@link RpgStationsApi}, which is the very thing an absent RpgStations
     * makes unverifiable. It is therefore safe to call only from INSIDE a consumer's own nested
     * "class holder" that is itself reached only after a zero-api-type {@code
     * PluginManager.getPlugin} check has already confirmed a loaded instance - see {@code
     * api/CLAUDE.md}'s "Two-step consumer idiom" section for the copy-pasteable pattern. What this method actually solves is
     * the NARROWER "RpgStations is loaded but {@code setup()} has not run yet" race inside that
     * already-safe holder, letting a caller branch instead of catching {@link
     * IllegalStateException} from {@link #get()}. The copy-pasteable shape lives in
     * {@code api/CLAUDE.md}'s "Two-step consumer idiom" section.
     */
    static boolean isAvailable() {
        return RpgStationsApiHolder.peek() != null;
    }

    /**
     * {@link #get()} without the throw: an empty {@link Optional} when RpgStations has not
     * finished {@code setup()} (or is not installed), the live api otherwise.
     *
     * <p>Carries the exact same caveat as {@link #isAvailable()} - it does NOT substitute for the
     * {@code PluginManager} presence check, and is safe to call only from inside a consumer's own
     * class holder reached after that check. See {@code api/CLAUDE.md}'s "Two-step consumer
     * idiom" section.
     */
    @Nonnull
    static Optional<RpgStationsApi> find() {
        return Optional.ofNullable(RpgStationsApiHolder.peek());
    }
}
