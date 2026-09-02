package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The volatile pending-anchor index's lifecycle: register on a partial build, answer the cheap
 * radius pre-check, evict on world removal, forget on activation/break, and hold its per-world
 * bound by dropping the oldest entry.
 */
public class PendingAnchorIndexTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void register_isIdempotentPerPositionAndPattern() {
        PendingAnchorIndex index = new PendingAnchorIndex();
        index.register(WORLD, 1, 2, 3, "ring");
        index.register(WORLD, 1, 2, 3, "ring");
        index.register(WORLD, 1, 2, 3, "Ring");
        assertEquals(1, index.size(WORLD));
        index.register(WORLD, 1, 2, 3, "otherring");
        assertEquals(2, index.size(WORLD), "two patterns may pend at one anchor position");
    }

    @Test
    void candidatesNear_isAChebyshevRadiusCheck() {
        PendingAnchorIndex index = new PendingAnchorIndex();
        index.register(WORLD, 0, 0, 0, "ring");

        assertEquals(1, index.candidatesNear(WORLD, 2, -2, 2, 2).size());
        assertEquals(0, index.candidatesNear(WORLD, 3, 0, 0, 2).size());
        assertEquals(0, index.candidatesNear(OTHER_WORLD, 0, 0, 0, 2).size(),
                "pending candidates never cross worlds");
    }

    @Test
    void removeAt_forgetsEveryPatternAtThatPosition() {
        PendingAnchorIndex index = new PendingAnchorIndex();
        index.register(WORLD, 0, 0, 0, "ring");
        index.register(WORLD, 0, 0, 0, "otherring");
        index.register(WORLD, 5, 0, 0, "ring");

        index.removeAt(WORLD, 0, 0, 0);

        assertEquals(1, index.size(WORLD));
        assertEquals(5, index.candidatesNear(WORLD, 5, 0, 0, 0).get(0).x());
    }

    @Test
    void remove_forgetsExactlyOneCandidate() {
        PendingAnchorIndex index = new PendingAnchorIndex();
        index.register(WORLD, 0, 0, 0, "ring");
        index.register(WORLD, 0, 0, 0, "otherring");

        List<PendingAnchorIndex.Pending> near = index.candidatesNear(WORLD, 0, 0, 0, 0);
        index.remove(WORLD, near.get(0));

        assertEquals(1, index.size(WORLD));
        assertEquals("otherring", index.candidatesNear(WORLD, 0, 0, 0, 0).get(0).patternId());
    }

    @Test
    void clearWorld_evictsThatWorldAlone() {
        PendingAnchorIndex index = new PendingAnchorIndex();
        index.register(WORLD, 0, 0, 0, "ring");
        index.register(OTHER_WORLD, 0, 0, 0, "ring");

        index.clearWorld(WORLD);

        assertEquals(0, index.size(WORLD));
        assertEquals(1, index.size(OTHER_WORLD));
    }

    @Test
    void theBound_dropsTheOldestEntry_neverGrowsPastTheCeiling() {
        PendingAnchorIndex index = new PendingAnchorIndex();
        for (int i = 0; i < PendingAnchorIndex.MAX_PER_WORLD + 10; i++) {
            index.register(WORLD, i, 0, 0, "ring");
        }
        assertEquals(PendingAnchorIndex.MAX_PER_WORLD, index.size(WORLD));
        assertTrue(index.candidatesNear(WORLD, 0, 0, 0, 5).isEmpty(),
                "the oldest entries (near the origin) were the ones dropped");
        assertEquals(1, index.candidatesNear(WORLD, PendingAnchorIndex.MAX_PER_WORLD + 9, 0, 0, 0).size());
    }
}
