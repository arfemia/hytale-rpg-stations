package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * The PURE cores of the refusal seam ({@link StationRefusals}): how a wording key becomes a reason
 * id, how the four cue layers fold, how the repeat throttle keys and admits, and the one asymmetry
 * the throttle is built around (the sound plays on every press, everything else on a fresh one).
 * The live {@code Press} entry needs a world and a player and is smoke-owned.
 *
 * <p>Every value below is authored by this test; nothing reads shipped content. The moment maps
 * are built the way the engine builds them ({@link StationFlairs#caseInsensitiveMomentKeys}), so a
 * lookup here answers exactly as one at play time does.
 */
public class StationRefusalsTest {

    private static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID P2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Presentation.SoundCue[] cues(String... eventIds) {
        Presentation.SoundCue[] out = new Presentation.SoundCue[eventIds.length];
        for (int i = 0; i < eventIds.length; i++) {
            out[i] = Presentation.SoundCue.of(eventIds[i]);
        }
        return out;
    }

    private static Presentation.ModelParticle[] particles(String systemId) {
        return new Presentation.ModelParticle[] {Presentation.ModelParticle.of(systemId)};
    }

    private static String[] soundIds(Presentation p) {
        Presentation.SoundCue[] sounds = p.getSounds();
        String[] out = new String[sounds.length];
        for (int i = 0; i < sounds.length; i++) {
            out[i] = sounds[i].getEventId();
        }
        return out;
    }

    /** A moment map held the way the engine holds every one: case-insensitive, authored spelling kept. */
    private static Map<String, Presentation> moments(Object... idsAndPresentations) {
        Map<String, Presentation> authored = new LinkedHashMap<>();
        for (int i = 0; i < idsAndPresentations.length; i += 2) {
            authored.put((String) idsAndPresentations[i], (Presentation) idsAndPresentations[i + 1]);
        }
        return StationFlairs.caseInsensitiveMomentKeys(authored);
    }

    // ==================== A wording key names a reason id ====================

    @Test
    void reasonOf_isTheTailAfterThePrefix_writtenAsAnId() {
        assertEquals("No_Materials", StationRefusals.reasonOf("ui.station.no_materials"));
        assertEquals("Wrong_Tool", StationRefusals.reasonOf("ui.station.wrong_tool"));
        assertEquals("Occupied", StationRefusals.reasonOf("ui.station.occupied"));
        assertEquals("Retrieve_Busy", StationRefusals.reasonOf("ui.station.retrieve.busy"),
                "a dotted tail folds whole, every segment one word of the id");
    }

    @Test
    void reasonOf_collapsesANamedVariantToItsBaseReason() {
        assertEquals("Socket_Missing", StationRefusals.reasonOf("ui.station.socket_missing_named"));
        assertEquals("Not_Shared", StationRefusals.reasonOf("ui.station.not_shared_named"));
        assertEquals("Pattern_Requirements_Unmet",
                StationRefusals.reasonOf("ui.station.pattern_requirements_unmet_named"));
    }

    @Test
    void reasonOf_isStableWhateverTheKeysCasing_andTakesAnUnprefixedKeyWhole() {
        assertEquals("No_Materials", StationRefusals.reasonOf("ui.station.No_Materials"));
        assertEquals("No_Materials", StationRefusals.reasonOf("ui.station.NO_MATERIALS"));
        assertEquals("Yourmod_Denied", StationRefusals.reasonOf("yourmod.denied"),
                "a key outside the family still yields a stable reason rather than an exception");
    }

    @Test
    void theMomentIdForAReason_isRefusedColonReason_andIsRecognized() {
        assertEquals("Refused:No_Materials", StationFlairs.refusedMomentId("No_Materials"));
        assertEquals("Refused:Socket_Missing",
                StationFlairs.refusedMomentId(StationRefusals.reasonOf("ui.station.socket_missing_named")));
        assertTrue(StationFlairs.isKnownMomentId("Refused:No_Materials"));
        assertTrue(StationFlairs.isKnownMomentId(StationFlairs.MOMENT_REFUSED));
        assertTrue(StationFlairs.isRefusalMomentId("Refused:Wrong_Tool"));
        assertTrue(StationFlairs.isRefusalMomentId("refused"), "matching ignores case");
        assertTrue(StationFlairs.isRefusalMomentId("refused:wrong_tool"), "matching ignores case");
        assertFalse(StationFlairs.isRefusalMomentId(StationFlairs.MOMENT_CYCLE));
    }

    // ==================== Resolution order: nearest wins, per leaf ====================

    @Test
    void resolveCue_nothingAuthoredAnywhere_isNull() {
        assertNull(StationRefusals.resolveCue("No_Materials", null, null));
        assertNull(StationRefusals.resolveCue("No_Materials", moments(), moments()));
        assertNull(StationRefusals.resolveCue("No_Materials",
                moments(StationFlairs.MOMENT_CYCLE, Presentation.ofSound("Fixture_Cycle")), moments()),
                "a non-refusal moment is never mistaken for a refusal cue");
    }

    @Test
    void resolveCue_theSettingsBareRefusedIsTheFloorEveryReasonLandsOn() {
        Map<String, Presentation> defaults = moments("Refused", Presentation.ofSound("Fixture_Thunk"));

        Presentation cue = StationRefusals.resolveCue("Wrong_Tool", null, defaults);

        assertNotNull(cue);
        assertArrayEquals(new String[] {"Fixture_Thunk"}, soundIds(cue));
    }

    @Test
    void resolveCue_settingsPerReasonOverlaysSettingsBare_perLeaf() {
        Map<String, Presentation> defaults = moments(
                "Refused", Presentation.of(cues("Fixture_Thunk"), particles("Fixture_Puff"), null, null, null),
                "Refused:Occupied", Presentation.ofSound("Fixture_Occupied"));

        Presentation cue = StationRefusals.resolveCue("Occupied", null, defaults);

        assertArrayEquals(new String[] {"Fixture_Occupied"}, soundIds(cue), "the per-reason sound wins");
        assertNotNull(cue.getParticles());
        assertEquals("Fixture_Puff", cue.getParticles()[0].getSystemId(),
                "the leaf the per-reason entry omits falls through to the bare entry");
    }

    @Test
    void resolveCue_theActionsBareRefusedOutranksTheSettingsPerReason() {
        Map<String, Presentation> defaults = moments(
                "Refused", Presentation.ofSound("Fixture_Thunk"),
                "Refused:Occupied", Presentation.ofSound("Fixture_Settings_Occupied"));
        Map<String, Presentation> action = moments("Refused", Presentation.ofSound("Fixture_Action_Refused"));

        Presentation cue = StationRefusals.resolveCue("Occupied", action, defaults);

        assertArrayEquals(new String[] {"Fixture_Action_Refused"}, soundIds(cue),
                "the action's own layer is nearer than any settings entry, per-reason included");
    }

    @Test
    void resolveCue_theActionsPerReasonIsNearestOfAll() {
        Map<String, Presentation> defaults = moments("Refused", Presentation.ofSound("Fixture_Thunk"));
        Map<String, Presentation> action = moments(
                "Refused", Presentation.ofSound("Fixture_Action_Refused"),
                "Refused:No_Materials", Presentation.ofSound("Fixture_Action_No_Materials"));

        assertArrayEquals(new String[] {"Fixture_Action_No_Materials"},
                soundIds(StationRefusals.resolveCue("No_Materials", action, defaults)));
        assertArrayEquals(new String[] {"Fixture_Action_Refused"},
                soundIds(StationRefusals.resolveCue("Wrong_Tool", action, defaults)),
                "another reason falls back to the action's bare entry");
    }

    @Test
    void resolveCue_anActionAuthoringOnlyParticlesKeepsTheSettingsSoundUnderneath() {
        Map<String, Presentation> defaults = moments("Refused", Presentation.ofSound("Fixture_Thunk"));
        Map<String, Presentation> action = moments("Refused",
                Presentation.of(null, particles("Fixture_Puff"), null, null, null));

        Presentation cue = StationRefusals.resolveCue("No_Materials", action, defaults);

        assertArrayEquals(new String[] {"Fixture_Thunk"}, soundIds(cue), "the omitted Sounds leaf falls through");
        assertEquals("Fixture_Puff", cue.getParticles()[0].getSystemId());
    }

    @Test
    void resolveCue_anAuthoredEmptySoundsArrayMeansSilence_unlikeAnOmittedKey() {
        Map<String, Presentation> defaults = moments("Refused", Presentation.ofSound("Fixture_Thunk"));
        Map<String, Presentation> silenced = moments("Refused",
                Presentation.of(new Presentation.SoundCue[0], null, null, null, null));

        Presentation cue = StationRefusals.resolveCue("No_Materials", silenced, defaults);

        assertNotNull(cue);
        assertNotNull(cue.getSounds(), "an authored empty array is a leaf, not an omission");
        assertEquals(0, cue.getSounds().length, "and it silences the settings' sound");
        assertNull(cue.soundsOnly(), "so nothing is scheduled for it");
    }

    @Test
    void resolveCue_aLowercaseAuthoredEntryResolves_andKeepsTheSpellingItWasAuthoredIn() {
        // A pack that wrote the ids the old way, all lowercase, keeps resolving: matching never
        // depends on casing, only what the map SHOWS does.
        Map<String, Presentation> defaults = moments(
                "refused", Presentation.ofSound("Fixture_Thunk"),
                "refused:no_materials", Presentation.ofSound("Fixture_No_Materials"));

        assertArrayEquals(new String[] {"Fixture_No_Materials"},
                soundIds(StationRefusals.resolveCue("No_Materials", null, defaults)));
        assertArrayEquals(new String[] {"Fixture_Thunk"},
                soundIds(StationRefusals.resolveCue("Wrong_Tool", null, defaults)));
        assertTrue(defaults.containsKey("REFUSED:NO_MATERIALS"), "any casing finds the entry");
        assertEquals("refused", defaults.keySet().iterator().next(),
                "the authored spelling is what the map still shows; nothing is rewritten");
    }

    // ==================== The sound escapes the throttle; nothing else does ====================

    @Test
    void theCueSplitsIntoTheSoundHalfAndTheRest() {
        Presentation cue = Presentation.of(cues("Fixture_Thunk"), particles("Fixture_Puff"),
                Presentation.Shake.of("Fixture_Shake", 0.5), null, null, 200L);

        Presentation sounds = cue.soundsOnly();
        Presentation rest = cue.withoutSounds();

        assertNotNull(sounds);
        assertArrayEquals(new String[] {"Fixture_Thunk"}, soundIds(sounds));
        assertNull(sounds.getParticles());
        assertNull(sounds.getShake());
        assertNull(sounds.getDelayMs(), "the sound half carries no timing: a refusal answers at once");

        assertNotNull(rest);
        assertNull(rest.getSounds());
        assertEquals("Fixture_Puff", rest.getParticles()[0].getSystemId());
        assertEquals("Fixture_Shake", rest.getShake().getEffectId());
    }

    @Test
    void aSoundOnlyCueHasNoRest_andAParticleOnlyCueHasNoSoundHalf() {
        assertNull(Presentation.ofSound("Fixture_Thunk").withoutSounds());
        assertNull(Presentation.of(null, particles("Fixture_Puff"), null, null, null).soundsOnly());
        assertFalse(Presentation.ofSound("Fixture_Thunk").hasNonSoundCue());
        assertTrue(Presentation.of(null, particles("Fixture_Puff"), null, null, null).hasNonSoundCue());
    }

    // ==================== The repeat throttle ====================

    @Test
    void throttleKey_isPlayerBlockAndReason() {
        assertEquals(P1 + "|w:0:64:0|No_Materials", StationRefusals.throttleKey(P1, "w:0:64:0", "No_Materials"));
    }

    @Test
    void admit_throttlesARepeatOfTheSameKeyInsideTheWindow() {
        Map<String, Long> lastAt = new HashMap<>();
        long window = 1_500L;
        String key = StationRefusals.throttleKey(P1, "w:0:64:0", "No_Materials");

        assertTrue(StationRefusals.admit(lastAt, key, 1_000L, window), "the first refusal always answers in full");
        assertFalse(StationRefusals.admit(lastAt, key, 1_000L + window - 1, window),
                "a repeat inside the window is a mashed key");
        assertTrue(StationRefusals.admit(lastAt, key, 1_000L + window, window),
                "the window's boundary re-arms the full answer");
    }

    @Test
    void admit_aDifferentReasonOrBlockOrPlayerIsNewInformation() {
        Map<String, Long> lastAt = new HashMap<>();
        long window = 1_500L;

        assertTrue(StationRefusals.admit(lastAt, StationRefusals.throttleKey(P1, "w:0:64:0", "No_Materials"), 1_000L, window));
        assertTrue(StationRefusals.admit(lastAt, StationRefusals.throttleKey(P1, "w:0:64:0", "Wrong_Tool"), 1_000L, window),
                "a different reason at the same block always gets through");
        assertTrue(StationRefusals.admit(lastAt, StationRefusals.throttleKey(P1, "w:9:64:9", "No_Materials"), 1_000L, window),
                "the same reason at another block always gets through");
        assertTrue(StationRefusals.admit(lastAt, StationRefusals.throttleKey(P2, "w:0:64:0", "No_Materials"), 1_000L, window),
                "a second player at the same block gets their own answer");
    }

    @Test
    void admit_aZeroWindowAnswersEveryPressInFull() {
        Map<String, Long> lastAt = new HashMap<>();
        String key = StationRefusals.throttleKey(P1, "w:0:64:0", "No_Materials");
        assertTrue(StationRefusals.admit(lastAt, key, 1_000L, 0L));
        assertTrue(StationRefusals.admit(lastAt, key, 1_000L, 0L));
        assertTrue(StationRefusals.admit(lastAt, key, 1_001L, -5L), "a negative window reads the same as zero");
    }

    @Test
    void admit_pruningDropsOnlyExpiredEntries() {
        Map<String, Long> lastAt = new HashMap<>();
        for (int i = 0; i < 400; i++) {
            StationRefusals.admit(lastAt, StationRefusals.throttleKey(P1, "w:" + i + ":64:0", "Occupied"), 1_000L, 5_000L);
        }
        // Far past every entry's window: the next decision prunes the map back down.
        StationRefusals.admit(lastAt, StationRefusals.throttleKey(P1, "w:999:64:0", "Occupied"), 60_000L, 5_000L);
        assertEquals(1, lastAt.size(), "expired entries are swept once the map outgrows its bound");
    }
}
