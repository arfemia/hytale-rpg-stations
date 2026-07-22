package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Puppet;

/**
 * Pure tests for {@link StationPuppetController}'s two unit-JVM-safe decision cores (round-4
 * puppet-presentation design, doc section 3.6): the per-step clip-override resolution
 * ({@link StationPuppetController#resolveEffectiveClip}) and the effective prop item id
 * ({@link StationPuppetController#resolveEffectivePropItemId}). Every other method in that class
 * touches live Hytale ECS/component types (Store/CommandBuffer/Ref/InventoryComponent) or
 * {@code ziggfreed-common}'s {@code PlayerPuppetService} and has NO unit coverage, matching
 * {@link StationEntityMountController}'s own precedent - a live-server-only glue class beyond its
 * pure cores.
 */
class StationPuppetControllerTest {

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
