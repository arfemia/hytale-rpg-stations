package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.Roll;

/**
 * F1 (decision 51d): the {@code Grants.Effects[]} altitude - {@link LootEngine#applyGrants} must
 * SURFACE every authored native-effect ref into {@link LootEngine.GrantResult#getEffectGrants} (it
 * previously ignored the array entirely). An effects-only {@code Grants} never touches the
 * drop-table seam, so this drives it with the pinned no-drop granter (mirroring the null-stand-in
 * pattern ziggfreed-common's {@code AppliedEffectTrackerTest} uses). The actual apply+track leg is
 * {@code StationService.applyAndTrackEffects} (see StationEffectGrantTest).
 */
class LootEngineEffectGrantTest {

    @Test
    void applyGrants_collectsEveryAuthoredEffect_droppingBlankIds() {
        LootEngine.GrantResult result = new LootEngine.GrantResult();
        Roll.Grants grants = Roll.Grants.of(null, null, new EffectRef[] {
                EffectRef.of("Root", null),
                EffectRef.of("Slow", 3000L),
                EffectRef.of("", null),   // blank id - dropped
                null                       // null entry - dropped
        });

        assertTrue(LootEngine.applyGrants(grants, DropListGranters.empty(), null, result, true),
                "collecting an effect counts as having produced something");

        assertEquals(2, result.getEffectGrants().size(), "both non-blank effects surface");
        assertEquals("Root", result.getEffectGrants().get(0).getId());
        assertEquals("Slow", result.getEffectGrants().get(1).getId());
        assertEquals(3000L, result.getEffectGrants().get(1).getDurationMs());
        assertTrue(result.anyGranted(), "an effects-only grant still counts as granted");
    }

    @Test
    void applyGrants_noEffects_collectsNothing() {
        LootEngine.GrantResult result = new LootEngine.GrantResult();
        assertFalse(LootEngine.applyGrants(Roll.Grants.of(null, null), DropListGranters.empty(), null, result, true),
                "an empty group produces nothing");
        assertTrue(result.getEffectGrants().isEmpty());
    }
}
