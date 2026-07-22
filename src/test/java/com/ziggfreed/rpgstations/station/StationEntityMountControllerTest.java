package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationEntityMountController}'s ONLY unit-JVM-safe logic -
 * {@link StationEntityMountController#resolveAttachmentOffset} (design section 9.2's CRITIQUE FIX
 * m7: the authored {@code Hold.Mount.Entity.Offset} converts to a float triple the ECS-touching
 * {@code attach} method feeds a {@code Rotation3f} constructor). Every other method in that class
 * touches live Hytale ECS/component types (Store/CommandBuffer/Holder/MountedComponent) and has
 * NO unit coverage, matching {@link StationMountController}'s own precedent (zero tests - a
 * live-server-only glue class).
 */
class StationEntityMountControllerTest {

    @Test
    void resolveAttachmentOffset_allNull_defaultsToZero() {
        assertArrayEquals(new float[] {0f, 0f, 0f},
                StationEntityMountController.resolveAttachmentOffset(null, null, null));
    }

    @Test
    void resolveAttachmentOffset_authoredValues_convertToFloat() {
        assertArrayEquals(new float[] {0.0f, 0.5f, 0.3f},
                StationEntityMountController.resolveAttachmentOffset(0.0, 0.5, 0.3));
    }

    @Test
    void resolveAttachmentOffset_partiallyAuthored_missingLeavesDefaultToZero() {
        assertArrayEquals(new float[] {1.25f, 0f, -2.0f},
                StationEntityMountController.resolveAttachmentOffset(1.25, null, -2.0));
    }
}
