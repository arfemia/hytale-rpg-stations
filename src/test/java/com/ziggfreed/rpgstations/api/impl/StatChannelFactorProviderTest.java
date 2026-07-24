package com.ziggfreed.rpgstations.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.api.FactorContext;

/**
 * {@link StatChannelFactorProvider} guard-branch tests. The channel-index / {@code EntityStatMap}
 * read itself needs a live Hytale server (same constraint every other engine-touching class in
 * this package documents - {@code FactorRegistryImplTest} never exercises a store-dependent
 * built-in either); these tests cover the fail-closed short-circuits the provider's own contract
 * promises: a blank {@code Param}, and a {@link FactorContext} with no live {@code store()}/{@code
 * playerRef()} together (the exact shape {@code StationService#checkRequires} builds for the
 * pre-session {@code Requires} gate check).
 */
class StatChannelFactorProviderTest {

    private static final UUID PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static FactorContext bareCtx() {
        return FactorContext.builder()
                .playerId(PLAYER)
                .stationId("test_station")
                .build();
    }

    @Test
    void registeredUnderTheBareIdStat() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        assertTrue(FactorRegistryImpl.getInstance().isKnown("stat"));
    }

    @Test
    void resolve_blankParam_returnsZero() {
        StatChannelFactorProvider provider = new StatChannelFactorProvider();
        FactorContext ctx = bareCtx();
        assertEquals(0.0, provider.resolve(ctx, null));
        assertEquals(0.0, provider.resolve(ctx, ""));
        assertEquals(0.0, provider.resolve(ctx, "   "));
    }

    @Test
    void resolve_noLiveStoreOrPlayerRef_returnsZeroSilently() {
        // Mirrors StationService#checkRequires's pre-session FactorContext, which never
        // populates store()/playerRef() together.
        StatChannelFactorProvider provider = new StatChannelFactorProvider();
        FactorContext ctx = bareCtx();
        assertEquals(0.0, provider.resolve(ctx, "MMO_Luck"));
    }

    @Test
    void resolve_throughTheRegistry_unknownFactorDoesNotThrow() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        FactorContext ctx = bareCtx();
        // Routed through the registry (its own throw-guard applies too) - just must not throw and
        // must resolve to a non-null Double for a known factor id with a degenerate context.
        Double result = FactorRegistryImpl.getInstance().resolve("stat", "MMO_Luck", ctx);
        assertEquals(0.0, result);
    }
}
