package com.ziggfreed.rpgstations.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The two multi-placement api event POJOs' plain-data contracts: field passthrough, the nullable
 * leaves (a produce batch with no custody socket, a structure revert with no acting player), and
 * the outputs list's immutability. Live world-thread handles ({@code Store}/{@code PlayerRef}/
 * {@code Ref}) cannot be constructed in a bare unit JVM and are never touched by these accessors,
 * so {@code null} placeholders stand in for them exactly as in {@code StationEventsTest};
 * {@code ItemStack} construction is likewise a live-server boundary, so the outputs contract is
 * pinned over the empty batch.
 */
class StationApiEventContractTest {

    private static final UUID WORLD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void outputProduced_plainDataPassesThrough() {
        StationOutputProducedEvent e = new StationOutputProducedEvent(null, null, null, PLAYER_ID,
                WORLD_ID, 3, 64, -7, "cookingpit", "Stew", "output", List.of());

        assertEquals(PLAYER_ID, e.workerId());
        assertEquals(WORLD_ID, e.worldUuid());
        assertEquals(3, e.blockX());
        assertEquals(64, e.blockY());
        assertEquals(-7, e.blockZ());
        assertEquals("cookingpit", e.stationId());
        assertEquals("Stew", e.actionId());
        assertEquals("output", e.socketId());
        assertTrue(e.outputs().isEmpty());
    }

    @Test
    void outputProduced_inventoryRoute_hasNoSocket() {
        StationOutputProducedEvent e = new StationOutputProducedEvent(null, null, null, PLAYER_ID,
                WORLD_ID, 0, 64, 0, "sawmill", "Mill", null, List.of());

        assertNull(e.socketId(), "an inventory produce lands in no custody socket");
    }

    @Test
    void outputProduced_outputsListIsImmutable() {
        StationOutputProducedEvent e = new StationOutputProducedEvent(null, null, null, PLAYER_ID,
                WORLD_ID, 0, 64, 0, "sawmill", "Mill", null, List.of());

        assertThrows(UnsupportedOperationException.class, () -> e.outputs().add(null),
                "a listener must not be able to mutate the committed batch");
    }

    @Test
    void structureChanged_activation_carriesTheActor() {
        StationStructureChangedEvent e = new StationStructureChangedEvent(WORLD_ID, 1, 65, 2,
                "cookingpit", "RPG_Station_CookingPit", true, PLAYER_ID, null);

        assertEquals(WORLD_ID, e.worldUuid());
        assertEquals(1, e.anchorX());
        assertEquals(65, e.anchorY());
        assertEquals(2, e.anchorZ());
        assertEquals("cookingpit", e.patternId());
        assertEquals("RPG_Station_CookingPit", e.blockItemId());
        assertTrue(e.activated());
        assertEquals(PLAYER_ID, e.actorId());
    }

    @Test
    void structureChanged_environmentRevert_hasNoActor() {
        StationStructureChangedEvent e = new StationStructureChangedEvent(WORLD_ID, 1, 65, 2,
                "cookingpit", "Deco_Campfire_Off", false, null, null);

        assertFalse(e.activated());
        assertEquals("Deco_Campfire_Off", e.blockItemId(), "a revert reports the reverted block");
        assertNull(e.actorId(), "an environment break has no acting player");
        assertNull(e.actor());
    }
}
