package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.common.codec.Vec3;

/**
 * Pure tests for {@link StationPuppetController}'s unit-JVM-safe decision cores (round-4
 * puppet-presentation design, doc section 3.6): the per-step clip-override resolution
 * ({@link StationPuppetController#resolveEffectiveClip}), the swing SLOT choice that resolution
 * feeds ({@link StationPuppetController#useActionSlotForPuppetSwing}), the effective prop item id
 * ({@link StationPuppetController#resolveEffectivePropItemId}), and (round-3 smoke) the
 * FACING-RELATIVE placement composition ({@link StationPuppetController#resolveWorldOffset}/
 * {@link StationPuppetController#resolveYawRadians}) - the placed block's facing enters as a plain
 * {@code blockYawRadians} scalar, exactly the primitive-typed discipline
 * {@link StationCustodyDisplayTest} established for the sibling {@code Custody.Display} math. Every
 * other method in that class touches live Hytale ECS/component types (Store/CommandBuffer/Ref/
 * InventoryComponent) or {@code ziggfreed-common}'s {@code PlayerPuppetService} and has NO unit
 * coverage, matching {@link StationEntityMountController}'s own precedent - a live-server-only glue
 * class beyond its pure cores.
 */
class StationPuppetControllerTest {

    private static final double YAW_90 = Math.PI / 2.0;
    private static final double YAW_180 = Math.PI;
    private static final double YAW_270 = 3.0 * Math.PI / 2.0;

    // ==================== resolveWorldOffset (facing-relative X/Z rotation) ====================

    @Test
    void resolveWorldOffset_noOffsetGroup_isZero() {
        assertArrayEquals(new double[] {0.0, 0.0, 0.0},
                StationPuppetController.resolveWorldOffset(null, 0.0));
    }

    @Test
    void resolveWorldOffset_defaultFacing_isIdentity() {
        // yaw 0 (default placement): the maintainer's in-game-tuned sawmill values pass through
        // unchanged, exactly the pre-change WORLD-SPACE behavior - no re-tune needed.
        Vec3 offset = Vec3.of(0.0, -0.4, 1.0);
        assertArrayEquals(new double[] {0.0, -0.4, 1.0},
                StationPuppetController.resolveWorldOffset(offset, 0.0), 1e-9);
    }

    @Test
    void resolveWorldOffset_frontOffset_rotatesThroughFourFacings() {
        // The shipped sawmill/cutting-board/cooking-fire shape: a pure +Z (block FRONT) shift.
        // Matches the engine's Rotation.rotateY on (0, y, 1): None (0,1), Ninety (1,0),
        // OneEighty (0,-1), TwoSeventy (-1,0).
        Vec3 offset = Vec3.of(0.0, -0.4, 1.0);
        assertArrayEquals(new double[] {0.0, -0.4, 1.0},
                StationPuppetController.resolveWorldOffset(offset, 0.0), 1e-9);
        assertArrayEquals(new double[] {1.0, -0.4, 0.0},
                StationPuppetController.resolveWorldOffset(offset, YAW_90), 1e-9);
        assertArrayEquals(new double[] {0.0, -0.4, -1.0},
                StationPuppetController.resolveWorldOffset(offset, YAW_180), 1e-9);
        assertArrayEquals(new double[] {-1.0, -0.4, 0.0},
                StationPuppetController.resolveWorldOffset(offset, YAW_270), 1e-9);
    }

    @Test
    void resolveWorldOffset_mixedOffset_rotatesBothAxes() {
        // Offset (X=1, Z=2) at yaw 90: worldX = 1*cos + 2*sin = 2, worldZ = -1*sin + 2*cos = -1.
        Vec3 offset = Vec3.of(1.0, 0.5, 2.0);
        assertArrayEquals(new double[] {2.0, 0.5, -1.0},
                StationPuppetController.resolveWorldOffset(offset, YAW_90), 1e-9);
    }

    @Test
    void resolveWorldOffset_yStaysVertical_neverRotated() {
        Vec3 offset = Vec3.of(null, -0.45, null);
        for (double yaw : new double[] {0.0, YAW_90, YAW_180, YAW_270}) {
            assertArrayEquals(new double[] {0.0, -0.45, 0.0},
                    StationPuppetController.resolveWorldOffset(offset, yaw), 1e-9);
        }
    }

    @Test
    void resolveWorldOffset_partiallyAuthored_missingLeavesDefaultToZero() {
        Vec3 offset = Vec3.of(1.0, null, null);
        assertArrayEquals(new double[] {1.0, 0.0, 0.0},
                StationPuppetController.resolveWorldOffset(offset, 0.0), 1e-9);
    }

    // ==================== resolveYawRadians (authored degrees + block facing) ====================

    @Test
    void resolveYawRadians_defaultFacing_isAuthoredDegreesVerbatim() {
        assertEquals(0f, StationPuppetController.resolveYawRadians(0.0, 0.0), 1e-5f);
        assertEquals((float) Math.PI, StationPuppetController.resolveYawRadians(180.0, 0.0), 1e-5f);
    }

    @Test
    void resolveYawRadians_blockFacingAddsOn() {
        // Authored Yaw 0 at a 90deg placement: the puppet turns WITH the block.
        assertEquals((float) YAW_90, StationPuppetController.resolveYawRadians(0.0, YAW_90), 1e-5f);
        // Authored Yaw 45 at a 90deg placement: PI/4 + PI/2.
        assertEquals((float) (Math.toRadians(45.0) + YAW_90),
                StationPuppetController.resolveYawRadians(45.0, YAW_90), 1e-5f);
    }

    @Test
    void resolveYawRadians_negativeAuthored_composesToo() {
        assertEquals((float) (Math.toRadians(-90.0) + YAW_180),
                StationPuppetController.resolveYawRadians(-90.0, YAW_180), 1e-5f);
    }

    // ==================== resolveEffectiveClip ====================

    @Test
    void resolveEffectiveClip_stepOverridePresent_wins() {
        assertEquals("Hammer_Strike", StationPuppetController.resolveEffectiveClip("Hammer_Strike", "Chop"));
    }

    @Test
    void resolveEffectiveClip_stepOverrideBlank_fallsBackToDefault() {
        assertEquals("Chop", StationPuppetController.resolveEffectiveClip("", "Chop"));
        assertEquals("Chop", StationPuppetController.resolveEffectiveClip("   ", "Chop"));
    }

    @Test
    void resolveEffectiveClip_noOverride_fallsBackToDefault() {
        assertEquals("Chop", StationPuppetController.resolveEffectiveClip(null, "Chop"));
    }

    @Test
    void resolveEffectiveClip_neitherAuthored_null() {
        assertNull(StationPuppetController.resolveEffectiveClip(null, null));
    }

    // ==================== useActionSlotForPuppetSwing (the swing SLOT choice) ====================

    @Test
    void puppetSwing_noEmoteClipAuthored_ridesActionSlot() {
        // The shipped station shape: Animation authors no EmoteId at all, so the work animation is
        // the held item's own Action-slot clip.
        assertTrue(StationPuppetController.useActionSlotForPuppetSwing(null));
    }

    @Test
    void puppetSwing_blankEmoteClip_ridesActionSlot() {
        assertTrue(StationPuppetController.useActionSlotForPuppetSwing(""));
        assertTrue(StationPuppetController.useActionSlotForPuppetSwing("   "));
    }

    @Test
    void puppetSwing_emoteClipAuthored_staysOnEmoteSlot() {
        // EmoteId is the opt-in full-body override, so it wins the slot when authored.
        assertFalse(StationPuppetController.useActionSlotForPuppetSwing("RPG_Emote_Saw"));
    }

    @Test
    void puppetSwing_stepClipOverride_staysOnEmoteSlot() {
        // A step's own Puppet.Clip resolves through resolveEffectiveClip FIRST, so an emote-less
        // station still keeps its step-synced clips on the Emote slot - the composition the two
        // cores make together, which is what the swing route actually feeds.
        String effective = StationPuppetController.resolveEffectiveClip("Hammer_Strike", null);
        assertFalse(StationPuppetController.useActionSlotForPuppetSwing(effective));
    }

    // ==================== resolveEffectivePropItemId ====================

    @Test
    void resolveEffectivePropItemId_noPropGroup_mirrorsHeld() {
        assertEquals("Tool_Hatchet_Cobalt",
                StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", null));
    }

    @Test
    void resolveEffectivePropItemId_noPropGroup_nothingHeld_null() {
        assertNull(StationPuppetController.resolveEffectivePropItemId(null, null));
    }

    @Test
    void resolveEffectivePropItemId_mirrorHeldDefault_mirrorsHeld() {
        Puppet.Prop prop = Puppet.Prop.of(Puppet.PROP_SOURCE_MIRROR_HELD, null, null);
        assertEquals("Tool_Hatchet_Cobalt", StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", prop));
    }

    @Test
    void resolveEffectivePropItemId_unrecognizedSource_defaultsToMirrorHeld() {
        Puppet.Prop prop = Puppet.Prop.of("Bogus", null, null);
        assertEquals("Tool_Hatchet_Cobalt", StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", prop));
    }

    @Test
    void resolveEffectivePropItemId_none_alwaysEmpty() {
        Puppet.Prop prop = Puppet.Prop.of(Puppet.PROP_SOURCE_NONE, null, null);
        assertNull(StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", prop));
    }

    @Test
    void resolveEffectivePropItemId_itemId_forcesConfiguredItem() {
        Puppet.Prop prop = Puppet.Prop.of(Puppet.PROP_SOURCE_ITEM_ID, "Tool_Hammer_Iron", null);
        assertEquals("Tool_Hammer_Iron", StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", prop));
    }

    @Test
    void resolveEffectivePropItemId_itemIdBlank_degradesToEmpty() {
        Puppet.Prop prop = Puppet.Prop.of(Puppet.PROP_SOURCE_ITEM_ID, "", null);
        assertNull(StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", prop));
    }

    @Test
    void resolveEffectivePropItemId_itemIdMissing_degradesToEmpty() {
        Puppet.Prop prop = Puppet.Prop.of(Puppet.PROP_SOURCE_ITEM_ID, null, null);
        assertNull(StationPuppetController.resolveEffectivePropItemId("Tool_Hatchet_Cobalt", prop));
    }
}
