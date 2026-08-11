package com.ziggfreed.rpgstations.api.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.factor.FactorConditions;
import com.ziggfreed.common.factor.FactorContext;
import com.ziggfreed.common.factor.FactorProvider;
import com.ziggfreed.common.factor.FactorRegistry;
import com.ziggfreed.common.factor.HytaleFactors;
import com.ziggfreed.common.registry.RegistryLedger;

/**
 * The adapter between this engine's own factor surface and the SHARED factor vocabulary that backs
 * it: it owns the one shared registry instance, turns a station evaluation into the shared
 * question shape, and answers the three engine-internal reads ({@code resolve}, {@code
 * isRegistered}, {@code ids}) plus the ledger's owner/failure snapshot.
 *
 * <p>Kept as its own class rather than inlined into {@link FactorRegistryImpl} so that exactly one
 * file speaks both vocabularies: the shared types and this mod's api types share several simple
 * names, and confining the translation here keeps every other file naming just one of them.
 *
 * <p><b>The station evaluation rides as the shared question's PAYLOAD.</b> A registered station
 * provider is handed this mod's own evaluation context (session seconds, cycle index, the tool the
 * session is working with), which the shared context carries opaquely; the store and the acting
 * entity are ALSO published as the shared subject leaves, which is what lets a portable provider
 * written against the shared vocabulary answer here with no station knowledge at all.
 *
 * <p><b>Fail-closed is the shared registry's own rule</b> and is inherited whole: an unregistered
 * id, a provider that throws, and a non-finite answer all resolve to {@code null}, and a gate on
 * {@code null} never opens.
 */
final class CoreFactorVocabulary {

    /** Prefixes this vocabulary's registration/fail-closed log lines. */
    private static final String LABEL = "rpgstations:factors";

    /** This engine's own vocabulary: everything an author can address at a station. */
    @Nonnull
    private final FactorRegistry registry = new FactorRegistry(LABEL);

    /**
     * The portable {@code hytale:} standard library, kept in its OWN registry and read through
     * {@link #registerPortable} for the ids this engine adopts wholesale. Separate on purpose: the
     * ids a station resolves from its own session snapshot (the tool it is working with) must stay
     * this engine's, and a shared library that also claims them would otherwise replace them.
     */
    @Nonnull
    private final FactorRegistry portable = new FactorRegistry(LABEL);

    /**
     * One forwarding {@link FactorProvider} per adopted portable id, so a second {@link
     * #registerPortable} call for an id already adopted (a hot reload, a second {@code
     * registerBuiltins()} call) hands the shared ledger the same forwarder instance back rather than
     * a freshly-capturing lambda, keeping the identity-based idempotent-re-registration rule intact.
     */
    @Nonnull
    private final Map<String, FactorProvider> portableForwarders = new ConcurrentHashMap<>();

    CoreFactorVocabulary(@Nullable String portableOwner) {
        HytaleFactors.registerInto(portable, portableOwner);
    }

    /** Register (or replace) {@code factorId}'s provider, attributed to {@code owner}. */
    void register(@Nullable String factorId, @Nullable String owner, @Nullable FactorProvider provider) {
        registry.register(factorId, owner, provider);
    }

    /**
     * Adopt a portable {@code hytale:} id wholesale: this vocabulary answers it by forwarding to
     * the shared standard library's own resolver, so the reading of native engine data lives in
     * one place for every mod rather than being re-derived per consumer.
     */
    void registerPortable(@Nonnull String factorId, @Nullable String owner) {
        registry.register(factorId, owner,
                portableForwarders.computeIfAbsent(factorId, id -> ctx -> portable.resolve(id, ctx)));
    }

    /** Is {@code factorId} registered? (A validator's known-factor check.) */
    boolean isRegistered(@Nullable String factorId) {
        return registry.isRegistered(factorId);
    }

    /** Every registered id, sorted. */
    @Nonnull
    List<String> ids() {
        return registry.ids();
    }

    /** Every registered id's owner + failure history. */
    @Nonnull
    Map<String, RegistryLedger.RegistrationInfo> info() {
        return registry.info();
    }

    /**
     * Resolve {@code factorId} for one station evaluation. {@code null} when the id is blank or
     * unregistered, the provider could not answer, or it threw (counted against its owner and
     * warned once by the shared registry).
     */
    @Nullable
    Double resolve(@Nullable String factorId, @Nullable String param, @Nullable Store<EntityStore> store,
            @Nullable Ref<EntityStore> subject, @Nonnull Object payload) {
        return registry.resolve(factorId, question(param, store, subject, payload));
    }

    /**
     * The factor id of the FIRST condition that did not pass, or {@code null} when every one
     * passed - the shared array evaluator, which re-scopes each entry with its own {@code Param}.
     */
    @Nullable
    String firstFailedCondition(@Nullable FactorCondition[] conditions, @Nullable Store<EntityStore> store,
            @Nullable Ref<EntityStore> subject, @Nonnull Object payload) {
        return FactorConditions.firstFailure(conditions, registry, question(null, store, subject, payload));
    }

    /**
     * Wrap a station provider as a shared one: it is handed the station evaluation back out of the
     * payload, and answers {@code null} (cannot answer) when a caller resolved against something
     * that is not a station evaluation at all.
     */
    @Nonnull
    static FactorProvider wrap(@Nonnull StationProviderCall call) {
        return ctx -> call.resolve(ctx.payload(), ctx.param());
    }

    /** The station-side half of {@link #wrap}, so this file never names this mod's own api types. */
    @FunctionalInterface
    interface StationProviderCall {
        /** {@code null} when {@code payload} is not the station evaluation this provider expects. */
        @Nullable
        Double resolve(@Nullable Object payload, @Nullable String param);
    }

    @Nonnull
    private static FactorContext question(@Nullable String param, @Nullable Store<EntityStore> store,
            @Nullable Ref<EntityStore> subject, @Nonnull Object payload) {
        return FactorContext.builder()
                .param(param)
                .store(store)
                .subject(subject)
                .payload(payload)
                .build();
    }
}
