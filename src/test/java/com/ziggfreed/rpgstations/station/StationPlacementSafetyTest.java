package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.ProtectListAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The pure cores of placement safety: the protect-list match ({@link StationCustody#isProtected}),
 * the {@code Except} holes as their own answer ({@link StationCustody#exceptRefuses}), the
 * placement site's acceptance ({@link StationService#socketAccepts}: an {@code Except}-only
 * {@code Custody.Input} narrows what the station derives), what a press that placed nothing answers
 * ({@link StationService#unplacedPressRefusal}: Protected refuses only at an empty station, and the
 * press reaches no other Protected answer, read off the source), the
 * Protected refusal surviving a socket-less custody's generic deny key
 * ({@link StationService#placementDenyKey}), a count pile's refusal of per-instance data
 * ({@link StationCustody#carriesInstanceData} over the raw readings), and the state a work beat
 * wears ({@link StationService#workingStateName}). Every fixture is authored here.
 */
class StationPlacementSafetyTest {

    private static final Map<String, String[]> TROPHY_TAGS = Map.of("Type", new String[] {"Trophy"});

    private static boolean isProtected(List<ProtectListAsset> lists, String itemId, Map<String, String[]> tags) {
        return StationCustody.isProtected(lists, "fixture_table", "Unmake", itemId, null, tags, null);
    }

    @Test
    void theProtectList_matchesAnIdWithoutRegardToCase_andAListedTag() {
        List<ProtectListAsset> lists = List.of(ProtectListAsset.of("fixture_trophies", new ActionInput[] {
                ActionInput.of("Fixture_Grimoire", null, null, null),
                ActionInput.of(null, null, TROPHY_TAGS, null)}, null, null));
        assertTrue(isProtected(lists, "fixture_grimoire", null));
        assertTrue(isProtected(lists, "Fixture_Sword", TROPHY_TAGS), "a listed tag value protects");
        assertFalse(isProtected(lists, "Fixture_Sword", Map.of("Type", new String[] {"Weapon"})));
        assertFalse(isProtected(lists, "Fixture_Sword", null));
    }

    @Test
    void noFile_anEmptyFile_orARoutelessEntry_protectsNothing() {
        assertFalse(isProtected(List.of(), "Fixture_Grimoire", TROPHY_TAGS));
        assertFalse(isProtected(List.of(ProtectListAsset.of("empty", null, null, null)), "Fixture_Grimoire",
                TROPHY_TAGS));
        assertFalse(isProtected(List.of(ProtectListAsset.of("hollow",
                new ActionInput[] {ActionInput.of(null, null, null, null)}, null, null)), "Fixture_Grimoire",
                TROPHY_TAGS), "an entry authoring no route is never read as everything");
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

    /** The degenerate single socket an Except-only Custody.Input makes: a single-item socket, the hole as its match. */
    private static Custody.ResolvedSocket exceptOnlySocket() {
        Custody custody = Custody.of(1, ActionInput.of(null, null, null, null,
                ActionInput.of("Fixture_Grimoire", null, null, null)), null);
        return custody.effectiveSockets().get(0);
    }

    private static final StationAsset.Conversion[] DERIVED_ROWS = {
            StationAsset.Conversion.of(Ingredient.item("Fixture_Sword", 1), Ingredient.item("Fixture_Bar", 2))};

    private static boolean placesAtExceptOnlySocket(String itemId, boolean fallbackTakes) {
        return StationService.socketAccepts(exceptOnlySocket(), () -> false, () -> DERIVED_ROWS,
                () -> fallbackTakes, itemId, null, null, null);
    }

    @Test
    void anExceptOnlyInput_atThePlacementSite_narrowsWhatTheStationDerives() {
        assertTrue(placesAtExceptOnlySocket("Fixture_Sword", false), "a piece a derived row takes places");
        assertFalse(placesAtExceptOnlySocket("Fixture_Shield", false),
                "a piece nothing derives is refused: the hole never opens the station to everything");
        assertFalse(placesAtExceptOnlySocket("Fixture_Grimoire", true),
                "the hole refuses even what a fallback route would take");
        assertTrue(placesAtExceptOnlySocket("Fixture_Shield", true), "a fallback route's piece places");
    }

    @Test
    void aCountPile_refusesInstanceData_aSingleItemSocketTakesIt() {
        Custody.ResolvedSocket pile = Custody.of(100, ActionInput.of("Fixture_Sword", null, null, null), null)
                .effectiveSockets().get(0);
        Custody.ResolvedSocket single = Custody.of(1, ActionInput.of("Fixture_Sword", null, null, null), null)
                .effectiveSockets().get(0);
        assertFalse(StationService.socketAccepts(pile, () -> true, () -> DERIVED_ROWS, () -> false,
                "Fixture_Sword", null, null, null));
        assertTrue(StationService.socketAccepts(single, () -> true, () -> DERIVED_ROWS, () -> false,
                "Fixture_Sword", null, null, null));
    }

    @Test
    void instanceData_isWearTrackedOrMetadata_readOffTheRawValues() {
        assertFalse(StationCustody.carriesInstanceData(0.0, false, Set.of()), "a bare stack");
        assertTrue(StationCustody.carriesInstanceData(120.0, false, Set.of()), "a breakable stack tracks wear");
        assertFalse(StationCustody.carriesInstanceData(120.0, true, Set.of()), "an unbreakable one does not");
        assertTrue(StationCustody.carriesInstanceData(0.0, false, Set.of("Fixture_Key")), "any metadata key");
        assertTrue(StationCustody.carriesInstanceData(0.0, true, null), "an unreadable stack is refused");
        assertFalse(StationCustody.carriesInstanceData(null), "a null stack carries nothing");
    }

    @Test
    void aProtectedPiece_isRefusedAtAnEmptyStation_andJudgedAsAToolAtALoadedOne() {
        StationCustody.PlacementDenial protectedDenial = StationCustody.PlacementDenial.PROTECTED;
        assertEquals("ui.station.protected",
                StationService.unplacedPressRefusal(false, false, false, protectedDenial));
        assertNull(StationService.unplacedPressRefusal(true, false, false, protectedDenial),
                "a loaded station falls through to the engage gates: the held trophy works as a tool");
        assertNull(StationService.unplacedPressRefusal(false, true, false, protectedDenial),
                "an idle-capable empty station falls through, like every other placement denial");
        assertEquals("ui.station.no_materials",
                StationService.unplacedPressRefusal(false, false, false, StationCustody.PlacementDenial.WRONG_INPUT));
        assertNull(StationService.unplacedPressRefusal(true, false, true, StationCustody.PlacementDenial.WRONG_INPUT));
    }

    @Test
    void thePress_answersAnUnplacedPieceOnlyThroughThatOneDecision() throws IOException {
        String toggle = SourcePins.methodBody(SourcePins.read("StationService.java"), "public void toggle(");
        assertEquals(1, SourcePins.count(toggle, "unplacedPressRefusal("),
                "every press that placed nothing is answered by the one decision");
        assertFalse(toggle.contains("PlacementDenial.PROTECTED"),
                "no Protected early return bypasses it, so a loaded station judges the held item as a tool");
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
