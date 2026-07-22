package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.api.impl.FlairUnlockRegistryImpl;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Exercises the PURE overlay core of the flair seam ({@link StationFlairs#effective}). Ported
 * from the MMO's {@code StationFlairsTest} (RPG Stations extraction leg 2); adapted for
 * RpgStations' own {@link Presentation} (7-arg factory ends in a nullable {@link
 * Presentation.Shake} instead of a Feedback string - unexercised here since no test needs it).
 *
 * <p>Leg 4: the single-provider {@code StationFlairs.setProvider} seam is retired; "grant a
 * flair to a player" is now registering a {@code FlairUnlockProvider} on the api-facing {@link
 * FlairUnlockRegistryImpl} (its production accumulate-only union), reset between tests via the
 * test-only {@link FlairUnlockRegistryImpl#resetForTests()}.
 */
public class StationFlairsTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String STATION_ID = "sawmill";

    @AfterEach
    void resetRegistry() {
        FlairUnlockRegistryImpl.getInstance().resetForTests();
    }

    private static void grant(Set<String> flairIds) {
        FlairUnlockRegistryImpl.getInstance().register(playerId -> flairIds);
    }

    private static StationAsset.Flair flair(Presentation swing, Presentation cycle) {
        return StationAsset.Flair.of(swing, cycle);
    }

    // ==================== No provider registered = identity pass-through ====================

    @Test
    void noProviderRegistered_isIdentityPassThrough_evenWithFlairsAuthored() {
        Presentation base = Presentation.of("SFX_Base", "Particles_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(Presentation.ofSound("SFX_Golden"), Presentation.ofSound("SFX_Golden_Cycle")));

        Presentation result = StationFlairs.effective(base, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertSame(base, result);
    }

    @Test
    void noProviderRegistered_nullBase_staysNull() {
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.ofSound("SFX_Golden_Cycle")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNull(result);
    }

    @Test
    void noFlairsAuthored_isIdentityPassThrough_evenIfSomethingIsUnlocked() {
        grant(Set.of("golden_saw"));
        Presentation base = Presentation.of("SFX_Base", "Particles_Base");

        Presentation result = StationFlairs.effective(base, null, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertSame(base, result);
    }

    // ==================== Single-flair leaf overlay ====================

    @Test
    void singleFlair_overridesSound_inheritsParticles() {
        grant(Set.of("golden_saw"));
        Presentation base = Presentation.of("SFX_Base", "Particles_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.ofSound("SFX_Golden")));

        Presentation result = StationFlairs.effective(base, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Golden", result.getSound());
        assertEquals("Particles_Base", result.getParticles());
    }

    // ==================== Add onto a null base ====================

    @Test
    void singleFlair_addsOntoANullBase() {
        grant(Set.of("golden_saw"));
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.of("SFX_Golden", "Particles_Golden")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Golden", result.getSound());
        assertEquals("Particles_Golden", result.getParticles());
    }

    // ==================== Multi-flair sorted-id stacking ====================

    @Test
    void twoFlairs_stackInSortedOrder_laterIdWinsPerLeaf() {
        grant(Set.of("zulu_saw", "alpha_saw"));
        Map<String, StationAsset.Flair> flairs = Map.of(
                "alpha_saw", flair(null, Presentation.of("SFX_Alpha", "Particles_Alpha")),
                "zulu_saw", flair(null, Presentation.ofSound("SFX_Zulu")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Zulu", result.getSound());
        assertEquals("Particles_Alpha", result.getParticles());
    }

    // ==================== Unlocked id absent from the authored map ====================

    @Test
    void unlockedIdAbsentFromMap_isIgnored() {
        grant(Set.of("retired_flair"));
        Presentation base = Presentation.of("SFX_Base", "Particles_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.ofSound("SFX_Golden")));

        Presentation result = StationFlairs.effective(base, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertSame(base, result);
    }

    // ==================== Slot separation ====================

    @Test
    void cycleOnlyFlair_neverTouchesSwing() {
        grant(Set.of("golden_saw"));
        Presentation swingBase = Presentation.ofSound("SFX_Swing_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.ofSound("SFX_Golden_Cycle")));

        Presentation result = StationFlairs.effective(swingBase, flairs, StationFlairs.Slot.SWING, PLAYER, STATION_ID);

        assertSame(swingBase, result);
    }

    @Test
    void swingOnlyFlair_overlaysSwing_neverTouchesCycle() {
        grant(Set.of("golden_saw"));
        Presentation swingBase = Presentation.ofSound("SFX_Swing_Base");
        Presentation cycleBase = Presentation.ofSound("SFX_Cycle_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(Presentation.ofSound("SFX_Golden_Swing"), null));

        Presentation swingResult = StationFlairs.effective(swingBase, flairs, StationFlairs.Slot.SWING, PLAYER, STATION_ID);
        Presentation cycleResult = StationFlairs.effective(cycleBase, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertEquals("SFX_Golden_Swing", swingResult.getSound());
        assertSame(cycleBase, cycleResult);
    }

    // ==================== RARE_FIND slot ====================

    @Test
    void noProviderRegistered_rareFindSlot_isIdentityPassThrough() {
        Presentation base = Presentation.of("SFX_T3_Base", "Particles_T3_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                StationAsset.Flair.of(null, null, Presentation.ofSound("SFX_Golden_Rare")));

        Presentation result = StationFlairs.effective(base, flairs, StationFlairs.Slot.RARE_FIND, PLAYER, STATION_ID);

        assertSame(base, result);
    }

    @Test
    void rareFindOnlyFlair_overlaysRareFind_neverTouchesSwingOrCycle() {
        grant(Set.of("golden_saw"));
        Presentation swingBase = Presentation.ofSound("SFX_Swing_Base");
        Presentation cycleBase = Presentation.ofSound("SFX_Cycle_Base");
        Presentation rareFindBase = Presentation.ofSound("SFX_RareFind_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                StationAsset.Flair.of(null, null, Presentation.ofSound("SFX_Golden_Rare")));

        Presentation rareFindResult = StationFlairs.effective(rareFindBase, flairs, StationFlairs.Slot.RARE_FIND, PLAYER, STATION_ID);
        Presentation swingResult = StationFlairs.effective(swingBase, flairs, StationFlairs.Slot.SWING, PLAYER, STATION_ID);
        Presentation cycleResult = StationFlairs.effective(cycleBase, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertEquals("SFX_Golden_Rare", rareFindResult.getSound());
        assertSame(swingBase, swingResult);
        assertSame(cycleBase, cycleResult);
    }

    @Test
    void rareFindFlair_addsOntoANullBase() {
        grant(Set.of("golden_saw"));
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                StationAsset.Flair.of(null, null, Presentation.of("SFX_Golden_Rare", "Particles_Golden_Rare")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.RARE_FIND, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Golden_Rare", result.getSound());
        assertEquals("Particles_Golden_Rare", result.getParticles());
    }

    // ==================== COMPLETION slot ====================

    @Test
    void noProviderRegistered_completionSlot_isIdentityPassThrough() {
        Presentation base = Presentation.of("SFX_Complete_Base", "Particles_Complete_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                StationAsset.Flair.of(null, null, null, Presentation.ofSound("SFX_Golden_Complete")));

        Presentation result = StationFlairs.effective(base, flairs, StationFlairs.Slot.COMPLETION, PLAYER, STATION_ID);

        assertSame(base, result);
    }

    @Test
    void completionOnlyFlair_overlaysCompletion_neverTouchesOtherSlots() {
        grant(Set.of("golden_saw"));
        Presentation swingBase = Presentation.ofSound("SFX_Swing_Base");
        Presentation cycleBase = Presentation.ofSound("SFX_Cycle_Base");
        Presentation rareFindBase = Presentation.ofSound("SFX_RareFind_Base");
        Presentation completionBase = Presentation.ofSound("SFX_Completion_Base");
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                StationAsset.Flair.of(null, null, null, Presentation.ofSound("SFX_Golden_Complete")));

        Presentation completionResult = StationFlairs.effective(completionBase, flairs, StationFlairs.Slot.COMPLETION, PLAYER, STATION_ID);
        Presentation swingResult = StationFlairs.effective(swingBase, flairs, StationFlairs.Slot.SWING, PLAYER, STATION_ID);
        Presentation cycleResult = StationFlairs.effective(cycleBase, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);
        Presentation rareFindResult = StationFlairs.effective(rareFindBase, flairs, StationFlairs.Slot.RARE_FIND, PLAYER, STATION_ID);

        assertEquals("SFX_Golden_Complete", completionResult.getSound());
        assertSame(swingBase, swingResult);
        assertSame(cycleBase, cycleResult);
        assertSame(rareFindBase, rareFindResult);
    }

    @Test
    void completionFlair_addsOntoANullBase() {
        grant(Set.of("golden_saw"));
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                StationAsset.Flair.of(null, null, null,
                        Presentation.of("SFX_Golden_Complete", "Particles_Golden_Complete")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.COMPLETION, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Golden_Complete", result.getSound());
        assertEquals("Particles_Golden_Complete", result.getParticles());
    }

    // ==================== Shake overlay (new leaf this leg) ====================

    @Test
    void shakeLeaf_overlaysIndependentlyOfSoundAndParticles() {
        grant(Set.of("golden_saw"));
        Presentation base = Presentation.of("SFX_Base", "Particles_Base");
        Presentation.Shake shake = Presentation.Shake.of("Damage_Shake", 0.5);
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.of(null, null, null, null, null, null, shake)));

        Presentation result = StationFlairs.effective(base, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Base", result.getSound(), "the flair leaves Sound null - the base survives");
        assertSame(shake, result.getShake());
    }

    // ==================== Union across providers (new this leg) ====================

    @Test
    void unionAcrossTwoProviders_bothContribute() {
        FlairUnlockRegistryImpl.getInstance().register(playerId -> Set.of("alpha_saw"));
        FlairUnlockRegistryImpl.getInstance().register(playerId -> Set.of("zulu_saw"));
        Map<String, StationAsset.Flair> flairs = Map.of(
                "alpha_saw", flair(null, Presentation.of("SFX_Alpha", "Particles_Alpha")),
                "zulu_saw", flair(null, Presentation.ofSound("SFX_Zulu")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Zulu", result.getSound());
        assertEquals("Particles_Alpha", result.getParticles());
    }

    @Test
    void aThrowingProvider_isSkipped_othersStillApply() {
        FlairUnlockRegistryImpl.getInstance().register(playerId -> {
            throw new IllegalStateException("boom");
        });
        grant(Set.of("golden_saw"));
        Map<String, StationAsset.Flair> flairs = Map.of("golden_saw",
                flair(null, Presentation.ofSound("SFX_Golden")));

        Presentation result = StationFlairs.effective(null, flairs, StationFlairs.Slot.CYCLE, PLAYER, STATION_ID);

        assertNotNull(result);
        assertEquals("SFX_Golden", result.getSound());
    }
}
