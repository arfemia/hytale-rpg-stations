package com.ziggfreed.rpgstations.puppetspike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link PuppetSpikeService}'s unit-JVM-safe logic: the puppet spawn geometry
 * ({@link PuppetSpikeService#computeForwardOffsetXZ}/{@link PuppetSpikeService#computeFacingYaw})
 * PLUS (round-4 harness-bug fix 1) the CRITICAL revert-teleport guard's decision core, {@link
 * PuppetSpikeService#positionDrifted}. Every other method in that class touches live Hytale
 * ECS/component/asset-registry types (Store/Holder/CosmeticsModule/ModelAsset/
 * HiddenPlayersManager/ScheduledExecutorService) and has NO unit coverage, matching {@code
 * StationEntityMountControllerTest}'s own precedent (pure-core only, everything else is a
 * live-server-only glue method). Invariant-style assertions per the repo's own "no exact numbers
 * from balance data" spirit - these are geometry conventions, not tunable content, so the exact
 * formula IS the thing under test, but the assertions are still framed as invariants
 * (magnitude, symmetry) rather than a single hardcoded coordinate pair.
 */
class PuppetSpikeServiceTest {

    private static final double EPSILON = 1e-9;

    @Test
    void computeForwardOffsetXZ_magnitudeMatchesRequestedOffset() {
        double[] offset = PuppetSpikeService.computeForwardOffsetXZ(0.73f, 2.0);
        double magnitude = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
        assertEquals(2.0, magnitude, 1e-6);
    }

    @Test
    void computeForwardOffsetXZ_oppositeYaw_givesOppositeOffset() {
        float yaw = 1.1f;
        double[] forward = PuppetSpikeService.computeForwardOffsetXZ(yaw, 2.0);
        double[] behind = PuppetSpikeService.computeForwardOffsetXZ(yaw + (float) Math.PI, 2.0);
        assertEquals(-forward[0], behind[0], 1e-6);
        assertEquals(-forward[1], behind[1], 1e-6);
    }

    @Test
    void computeForwardOffsetXZ_zeroOffset_isOrigin() {
        double[] offset = PuppetSpikeService.computeForwardOffsetXZ(2.4f, 0.0);
        assertEquals(0.0, offset[0], EPSILON);
        assertEquals(0.0, offset[1], EPSILON);
    }

    @Test
    void computeFacingYaw_isCallerYawRotatedHalfTurn() {
        float callerYaw = 0.6f;
        float facingYaw = PuppetSpikeService.computeFacingYaw(callerYaw);
        assertEquals(callerYaw - (float) Math.PI, facingYaw, 1e-6f);
    }

    @Test
    void positionDrifted_identicalPoint_isFalse() {
        assertFalse(PuppetSpikeService.positionDrifted(10, 64, -3, 10, 64, -3, 0.5));
    }

    @Test
    void positionDrifted_belowEpsilon_isFalse() {
        // A physically-plausible settle (well under half a block) must never false-positive.
        assertFalse(PuppetSpikeService.positionDrifted(10, 64, -3, 10.1, 64.05, -3.05, 0.5));
    }

    @Test
    void positionDrifted_atExactlyEpsilon_isFalse() {
        assertFalse(PuppetSpikeService.positionDrifted(0, 0, 0, 0.5, 0, 0, 0.5));
    }

    @Test
    void positionDrifted_beyondEpsilon_isTrue() {
        // A "random coordinates" teleport is many blocks, not a fraction of one.
        assertTrue(PuppetSpikeService.positionDrifted(10, 64, -3, 500, 12, 900, 0.5));
    }

    @Test
    void positionDrifted_singleAxisBeyondEpsilon_isTrue() {
        assertTrue(PuppetSpikeService.positionDrifted(0, 0, 0, 0, 5, 0, 0.5));
    }
}
