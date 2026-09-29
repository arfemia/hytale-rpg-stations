package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;

/**
 * The pure cores of placement safety: the owner protect-list ({@link StationCustody#isProtected}),
 * the {@code Except} holes as their own answer ({@link StationCustody#exceptRefuses}), the
 * Protected refusal surviving a socket-less custody's generic deny key
 * ({@link StationService#placementDenyKey}), a count pile's refusal of per-instance data
 * ({@link StationCustody#carriesInstanceData}; a real stack is a live-server boundary, so only the
 * null shape is pinned here), and the state a work beat wears ({@link StationService#workingStateName}).
 * Every fixture is authored here.
 */
class StationPlacementSafetyTest {

    private static final Map<String, String[]> TROPHY_TAGS = Map.of("Type", new String[] {"Trophy"});

    @Test
    void theProtectList_matchesAnIdWithoutRegardToCase_andAListedTag() {
        RpgStationsSettingsAsset.Protected list = RpgStationsSettingsAsset.Protected.of(
                new String[] {"Fixture_Grimoire"}, Map.of("Type", new String[] {"Trophy"}));
        assertTrue(StationCustody.isProtected(list, "fixture_grimoire", null));
        assertTrue(StationCustody.isProtected(list, "Fixture_Sword", TROPHY_TAGS), "a listed tag value protects");
        assertFalse(StationCustody.isProtected(list, "Fixture_Sword", Map.of("Type", new String[] {"Weapon"})));
        assertFalse(StationCustody.isProtected(list, "Fixture_Sword", null));
    }

    @Test
    void anAbsentOrEmptyProtectList_protectsNothing() {
        assertFalse(StationCustody.isProtected(null, "Fixture_Grimoire", TROPHY_TAGS));
        RpgStationsSettingsAsset.Protected empty = RpgStationsSettingsAsset.Protected.of(null, null);
        assertTrue(empty.isEmpty());
        assertFalse(StationCustody.isProtected(empty, "Fixture_Grimoire", TROPHY_TAGS));
    }

    @Test
    void theExceptHoles_answerOnTheirOwn_singleOrSeveral() {
        ActionInput one = ActionInput.of(null, "Fixture_Family", null, null,
                ActionInput.of("Fixture_Grimoire", null, null, null));
        assertTrue(StationCustody.exceptRefuses(one, "Fixture_Grimoire", null, null, null));
        assertFalse(StationCustody.exceptRefuses(one, "Fixture_Sword", null, null, null));

        ActionInput several = ActionInput.of(null, null, null, null, new ActionInput[] {
                ActionInput.of("Fixture_Grimoire", null, null, null),
                ActionInput.of(null, null, TROPHY_TAGS, null)});
        assertTrue(StationCustody.exceptRefuses(several, "Fixture_Grimoire", null, null, null));
        assertTrue(StationCustody.exceptRefuses(several, "Fixture_Sword", null, TROPHY_TAGS, null),
                "any entry's routes carve a hole");
        assertFalse(StationCustody.exceptRefuses(several, "Fixture_Sword", null, null, null));
        assertFalse(StationCustody.exceptRefuses(null, "Fixture_Grimoire", null, null, null));
        assertFalse(StationCustody.exceptRefuses(ActionInput.of(null, "Fixture_Family", null, null),
                "Fixture_Grimoire", null, null, null), "no entry, no hole");
    }

    @Test
    void anExceptOnlyMatcher_refusesTheHoleAndAcceptsTheRest_underTheSharedRule() {
        ActionInput holeOnly = ActionInput.of(null, null, null, null,
                ActionInput.of("Fixture_Grimoire", null, null, null));
        assertTrue(holeOnly.isCatchAll() && holeOnly.hasExcept());
        assertFalse(StationCustody.accepts(holeOnly, "Fixture_Grimoire", null, null, null));
        assertTrue(StationCustody.accepts(holeOnly, "Fixture_Sword", null, null, null),
                "the shared rule alone still reads a catch-all as everything but the hole; the placement"
                        + " site narrows it to what the station derives");
    }

    @Test
    void theProtectedDenial_keepsItsOwnKey_evenOnASocketLessCustody() {
        assertEquals("ui.station.protected",
                StationService.placementDenyKey(false, StationCustody.PlacementDenial.PROTECTED));
        assertEquals("ui.station.protected",
                StationService.placementDenyKey(true, StationCustody.PlacementDenial.PROTECTED));
        assertEquals("ui.station.no_materials",
                StationService.placementDenyKey(false, StationCustody.PlacementDenial.WRONG_INPUT),
                "every other denial still folds to the generic line on a socket-less custody");
        assertEquals("ui.station.socket_wrong_input",
                StationService.placementDenyKey(true, StationCustody.PlacementDenial.WRONG_INPUT));
        assertEquals("ui.station.no_materials", StationService.placementDenyKey(true, null));
        assertEquals("Protected", StationRefusals.reasonOf("ui.station.protected"),
                "the refusal's moment is Refused:Protected");
    }

    @Test
    void protectedIsTheMostSpecificDenial() {
        assertEquals(0, StationCustody.PlacementDenial.PROTECTED.ordinal(),
                "enum order is precedence: a protected piece outranks every other reason");
    }

    @Test
    void aNullStack_carriesNoInstanceData() {
        assertFalse(StationCustody.carriesInstanceData(null));
    }

    @Test
    void theStateAWorkBeatWears_isTheStepsOwn_elseWorking_elseNone() {
        Custody.States states = Custody.States.of("Empty", "Loaded", "Working");
        assertEquals("Drawing", StationService.workingStateName(states, "Drawing"));
        assertEquals("Working", StationService.workingStateName(states, null));
        assertEquals("Working", StationService.workingStateName(states, "  "));
        assertNull(StationService.workingStateName(Custody.States.of("Empty", "Loaded"), null),
                "no Working name and no step state: no flip");
        assertEquals("Drawing", StationService.workingStateName(Custody.States.of("Empty", "Loaded"), "Drawing"),
                "a step state needs only the States group, not a Working name");
        assertNull(StationService.workingStateName(null, "Drawing"),
                "no States group at all: the exit could not restore a resting look, so no flip");
    }
}
