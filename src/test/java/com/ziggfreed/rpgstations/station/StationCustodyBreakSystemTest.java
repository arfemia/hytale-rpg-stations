package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.EnvironmentBreakBlockEvent;

/**
 * The two break routes into the one custody-removal funnel: the engine fires
 * {@code EnvironmentBreakBlockEvent} INSTEAD of {@code BreakBlockEvent} for a break with no
 * instigating entity (fire, physics, an unattributed explosion), so BOTH systems must exist or an
 * explosion leaves a stash standing under a destroyed block. What a unit JVM can pin is the
 * subscription shape and the shared block-key spelling; the actual stash removal + drop-at-block
 * runs against a live chunk store and is owned by in-game smoke (the established boundary - a
 * bare unit JVM has no {@code ChunkStore} sections and cannot construct {@code ItemStack}s).
 */
class StationCustodyBreakSystemTest {

    @Test
    void playerBreakSystem_subscribesToBreakBlockEvent() {
        assertEquals(BreakBlockEvent.class, new StationCustodyBreakSystem().getEventType());
    }

    @Test
    void environmentBreakSystem_subscribesToEnvironmentBreakBlockEvent() {
        StationCustodyBreakSystem.Environment system = new StationCustodyBreakSystem.Environment();
        assertEquals(EnvironmentBreakBlockEvent.class, system.getEventType());
        assertTrue(system instanceof WorldEventSystem,
                "an environment break has no actor entity, so the system is world-level by construction");
    }

    @Test
    void theTwoSystemsAreDistinctClasses_forTheClassKeyedRegistry() {
        // The engine's system registry is CLASS-keyed: two registrations of one class collide, so
        // the environment route must be its own class rather than a second instance.
        assertNotEquals(new StationCustodyBreakSystem().getClass(),
                new StationCustodyBreakSystem.Environment().getClass());
    }

    @Test
    void bothRoutesSpellTheBlockKeyIdentically() {
        // Both handlers key the SAME funnel with StationAnchors.blockKey; a drifted spelling would
        // make an environment break miss the display handle its block's placement recorded.
        assertEquals("world-uuid:1:2:3", StationAnchors.blockKey("world-uuid", 1, 2, 3));
    }
}
