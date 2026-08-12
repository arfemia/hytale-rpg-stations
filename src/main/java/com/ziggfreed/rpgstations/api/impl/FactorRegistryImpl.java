package com.ziggfreed.rpgstations.api.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.factor.FactorProvider;
import com.ziggfreed.common.factor.HytaleFactors;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.registry.RegistryLedger;
import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.FactorRegistry;
import com.ziggfreed.rpgstations.api.StationFactorProvider;

/**
 * The concrete {@link FactorRegistry} the RpgStations engine dogfoods for its OWN {@code
 * rpgstations:} built-ins and exposes to third parties via {@link
 * com.ziggfreed.rpgstations.api.RpgStationsApi#get()}{@code .factors()}.
 *
 * <p>The vocabulary itself is the SHARED one every engine in the primitive library gates and
 * scales on - registration bookkeeping (owner attribution, per-provider failure counting,
 * warn-once on an unregistered id), the fail-closed null sentinel, and the portable {@code
 * hytale:} standard library all come from there through {@link CoreFactorVocabulary}. What stays
 * here is this mod's own surface: the register-only api interface a third party codes against, and
 * the station-flavoured resolution of the ids a work session answers better than a live read can.
 *
 * <p>Engine-internal readers ({@code loot.FactorSnapshot}, {@code station.StationService}'s
 * Requires gate, {@code station.StationValidator}'s known-factor check, the {@code
 * rpgstations:factors} Asset Editor dataset) call {@link #resolve}/{@link #isKnown}/{@link
 * #registeredIds} directly against THIS singleton rather than through the narrow {@code
 * register}-only public interface - those reads are an engine-internal extension of the frozen api
 * contract, not part of it.
 */
public final class FactorRegistryImpl implements FactorRegistry {

    /** Attribution for every id this engine registers itself, as the ledger records it. */
    private static final String OWNER = "rpgstations";

    private static final FactorRegistryImpl INSTANCE = new FactorRegistryImpl();

    @Nonnull
    private final CoreFactorVocabulary core = new CoreFactorVocabulary(OWNER);

    /**
     * One adapter instance per registered {@link StationFactorProvider} instance, so a caller that
     * re-registers the SAME provider (a hot reload, a second plugin load) hands the shared ledger
     * the same {@link FactorProvider} reference back and its identity-based idempotent-re-registration
     * rule recognizes it, instead of minting a fresh wrapper every call and tripping a spurious
     * overwrite warning.
     */
    @Nonnull
    private final Map<StationFactorProvider, FactorProvider> wrappers = new ConcurrentHashMap<>();

    private FactorRegistryImpl() {
    }

    @Nonnull
    public static FactorRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(@Nonnull String factorId, @Nonnull StationFactorProvider provider) {
        register(factorId, null, provider);
    }

    /**
     * As {@link #register(String, StationFactorProvider)}, recording {@code owner} as the claimant
     * so {@link #info} can name who a factor (or a failing one) belongs to. Engine-internal: the
     * public api surface stays the two-argument form, since a third party registering through it is
     * already identified by its own namespace.
     */
    public void register(@Nonnull String factorId, @Nullable String owner,
            @Nonnull StationFactorProvider provider) {
        core.register(factorId, owner, wrap(provider));
    }

    /**
     * Register the built-in factors - called once from {@code RpgStationsPlugin#setup()},
     * idempotent (re-registering is harmless).
     *
     * <p><b>A factor's namespace names the VOCABULARY's owner, not whoever registered the
     * provider.</b> This engine registers all of the below, but only some of the numbers are its
     * own idea:
     * <ul>
     *   <li>{@code rpgstations:session_seconds} / {@code rpgstations:cycle_count} - concepts that
     *       exist only because a station SESSION exists. Genuinely this mod's vocabulary.</li>
     *   <li>{@code hytale:tool_power} / {@code hytale:tool_quality} /
     *       {@code hytale:tool_item_level} / {@code hytale:tool_durability_percent} - straight reads
     *       of native item data ({@code ItemToolSpec} power for a native {@code GatherType},
     *       {@code ItemQuality.QualityValue}, {@code ItemLevel}, durability), meaningful with no
     *       station involved at all. They are answered from the SESSION's own tool rather than from
     *       whatever is in the hand at the instant of the read, which is the right answer at a
     *       station and the reason this engine resolves them itself: same portable vocabulary,
     *       context-appropriate number. {@code tool_power} takes the gather type as its
     *       {@code Param} exactly as {@code stat} takes a stat id, so the addressing is explicit and
     *       the factor stays portable; omitting {@code Param} falls back to the station's own
     *       effective gather type, which is the common case.</li>
     *   <li>{@code hytale:stat} - reads ANY registered native {@code EntityStatType}'s effective
     *       value ({@code {"Factor":"hytale:stat","Param":"<StatId>"}}), so a mod that writes native
     *       stats participates in loot/gate/cap formulas with zero bridge code. Adopted wholesale
     *       from the shared standard library, which reads the acting entity's own stat map.</li>
     * </ul>
     * The practical payoff: a {@code hytale:}-namespaced id means the same thing to every mod that
     * reads native data, so two mods converging on it is agreement rather than a collision, and an
     * author can tell from the id alone whether a factor is portable or station-specific.
     */
    public void registerBuiltins() {
        register("rpgstations:session_seconds", OWNER, (ctx, param) -> (double) ctx.sessionSeconds());
        register("rpgstations:cycle_count", OWNER, (ctx, param) -> (double) ctx.cycleIndex());
        // tool_power goes through the NULLABLE core seam rather than the primitive station-provider
        // one, so a gather type the held tool has no spec for answers "cannot tell" instead of 0 -
        // which is what keeps a bounds-less gate on it shut. The no-Param form deliberately stays
        // the STATION's own effective gather type rather than the portable "best of any type"
        // aggregate: at a work session that is the context-appropriate answer, and the vocabulary is
        // per consumer precisely so it can be.
        core.register(HytaleFactors.TOOL_POWER, OWNER, CoreFactorVocabulary.wrap((payload, param) ->
                payload instanceof FactorContext station ? station.toolPowerOrNull(param) : null));
        register(HytaleFactors.TOOL_DURABILITY_PERCENT, OWNER, (ctx, param) -> ctx.toolDurabilityPercent());
        register(HytaleFactors.TOOL_QUALITY, OWNER, (ctx, param) -> ctx.toolQuality());
        register(HytaleFactors.TOOL_ITEM_LEVEL, OWNER, (ctx, param) -> ctx.toolItemLevel());
        core.registerPortable(HytaleFactors.STAT, OWNER);
    }

    /**
     * Resolve {@code factorId} against {@code ctx}; {@code null} when {@code factorId} is blank,
     * unregistered, the provider could not answer, or it threw (warned once and counted against its
     * owner, never propagated - a bad factor provider must not crash a loot roll or a station gate
     * check).
     */
    @Nullable
    public Double resolve(@Nullable String factorId, @Nullable String param, @Nonnull FactorContext ctx) {
        return core.resolve(factorId, param, ctx.store(), subjectOf(ctx), ctx);
    }

    /**
     * One batch's memoized {@code (factorId, param) -> value} reading set for {@code ctx}: every
     * distinct pair is resolved AT MOST ONCE, so two formulas reading the same factor inside one
     * cycle can never disagree about it.
     *
     * <p>Build one per moment and discard it. The context carries live world-thread handles, so a
     * snapshot held across moments is both stale and unsafe.
     */
    @Nonnull
    public FactorLookup snapshotFor(@Nonnull FactorContext ctx) {
        return core.snapshot(ctx.store(), subjectOf(ctx), ctx);
    }

    /**
     * The factor id of the FIRST condition that did not pass, or {@code null} when every one passed
     * (or none was authored) - the shared array evaluator, so a {@code Conditions} block means the
     * same thing here as at every other site reading this vocabulary. The caller turns a non-null
     * answer into its own gate reason.
     */
    @Nullable
    public String firstFailedCondition(@Nullable FactorCondition[] conditions, @Nonnull FactorContext ctx) {
        return core.firstFailedCondition(conditions, ctx.store(), subjectOf(ctx), ctx);
    }

    /** True when a provider is registered for {@code factorId} (the validator's known-factor check). */
    public boolean isKnown(@Nullable String factorId) {
        return core.isRegistered(factorId);
    }

    /**
     * Every currently-registered factor id, lowercased and sorted - an engine-internal extension
     * read beside {@link #resolve}/{@link #isKnown} (never part of the frozen register-only api
     * contract). Backs the {@code rpgstations:factors} Asset-Editor dropdown dataset, which is why
     * it is a snapshot: a mod registering a factor later simply widens the next request's answer.
     */
    @Nonnull
    public List<String> registeredIds() {
        return core.ids();
    }

    /**
     * Every registered factor's owner plus its failure history, keyed by id - what an admin listing
     * reads to tell a server owner WHICH mod claimed a factor, and which claimant's provider keeps
     * failing.
     */
    @Nonnull
    public Map<String, RegistryLedger.RegistrationInfo> info() {
        return core.info();
    }

    /**
     * Adapt a station provider to the shared one: the station evaluation travels as the shared
     * question's payload and comes back out here, so a third party keeps writing against this
     * mod's own context type.
     */
    @Nonnull
    private FactorProvider wrap(@Nonnull StationFactorProvider provider) {
        return wrappers.computeIfAbsent(provider, p -> CoreFactorVocabulary.wrap((payload, param) ->
                payload instanceof FactorContext station ? p.resolve(station, param) : null));
    }

    /**
     * The acting entity behind {@code ctx}, published to the shared question so a portable provider
     * can read it. Null when the evaluation carries no player reference or the reference is not
     * live (the pre-session gate check builds a context with no store at all), which the shared
     * providers treat as "cannot answer" - fail-closed, never a substituted zero.
     */
    @Nullable
    private static Ref<EntityStore> subjectOf(@Nonnull FactorContext ctx) {
        PlayerRef playerRef = ctx.playerRef();
        if (playerRef == null) {
            return null;
        }
        Ref<EntityStore> ref;
        try {
            ref = playerRef.getReference();
        } catch (Throwable t) {
            return null;
        }
        return ref != null && ref.isValid() ? ref : null;
    }
}
