package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link FactorSnapshot} memoization coverage (design 4.8): a repeated {@code resolve} call for
 * the SAME {@code (factorId, param)} pair within one snapshot hits the registry AT MOST ONCE -
 * the "one aggregation, many consumers" invariant a Roll's {@code Chance} and {@code Ladder}
 * both rely on when they reference the same factor.
 */
public class FactorSnapshotTest {

    private static final String TEST_FACTOR = "rpgstations:_test_snapshot_factor";

    @AfterEach
    void cleanup() {
        // StationFactorRegistry has no unregister; overwrite with a stub so other test classes
        // (which share the process-static registry) never see this leftover counting provider.
        StationFactorRegistry.register(TEST_FACTOR, (ctx, param) -> 0.0);
    }

    @Test
    void resolve_memoizesPerFactorIdAndParam() {
        AtomicInteger calls = new AtomicInteger();
        StationFactorRegistry.register(TEST_FACTOR, (ctx, param) -> {
            calls.incrementAndGet();
            return 42.0;
        });
        FactorSnapshot snapshot = new FactorSnapshot(FactorContext.of(0L, 1, 0.0, 100.0));

        assertEquals(42.0, snapshot.resolve(TEST_FACTOR, null));
        assertEquals(42.0, snapshot.resolve(TEST_FACTOR, null));
        assertEquals(42.0, snapshot.resolve(TEST_FACTOR, null));

        assertEquals(1, calls.get(), "three resolves of the same (factorId, param) hit the registry once");
    }

    @Test
    void resolve_differentParams_areCachedSeparately() {
        AtomicInteger calls = new AtomicInteger();
        StationFactorRegistry.register(TEST_FACTOR, (ctx, param) -> {
            calls.incrementAndGet();
            return "a".equals(param) ? 1.0 : 2.0;
        });
        FactorSnapshot snapshot = new FactorSnapshot(FactorContext.of(0L, 1, 0.0, 100.0));

        assertEquals(1.0, snapshot.resolve(TEST_FACTOR, "a"));
        assertEquals(2.0, snapshot.resolve(TEST_FACTOR, "b"));
        assertEquals(1.0, snapshot.resolve(TEST_FACTOR, "a"));

        assertEquals(2, calls.get(), "two distinct params resolve twice; the repeat 'a' is cached");
    }

    @Test
    void resolve_blankFactorId_returnsNullWithoutTouchingTheRegistry() {
        FactorSnapshot snapshot = new FactorSnapshot(FactorContext.of(0L, 1, 0.0, 100.0));
        assertNull(snapshot.resolve(null, null));
        assertNull(snapshot.resolve("", null));
    }

    @Test
    void resolve_unregisteredFactor_isNullAndCached() {
        FactorSnapshot snapshot = new FactorSnapshot(FactorContext.of(0L, 1, 0.0, 100.0));
        assertNull(snapshot.resolve("rpgstations:definitely_unregistered", null));
        assertNull(snapshot.resolve("rpgstations:definitely_unregistered", null));
    }
}
