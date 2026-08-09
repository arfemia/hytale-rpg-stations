package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;

/**
 * THE SMART-CUE RULE (see {@code asset.Roll}): a celebration never plays over nothing. A
 * {@code Presentation} authored BESIDE a grants group rides out only once applying that group
 * actually produced something; one authored with no grants beside it is a pure cue and rides on the
 * plain hit or floor reach. Both altitudes obey it - a reached {@code Ladder.Floor}'s own cue and
 * the roll's own top-level cue - and both land in the one earned-cue transport
 * {@code GrantResult.getFloorPresentations()}.
 *
 * <p>Driven through the seam-injected {@link LootEngine#rollAndGrant} core with a PINNED drop-table
 * outcome ({@link DropListGranters}), so "the table paid" and "the table rolled empty" are decided
 * facts rather than sampled ones. Every fixture value here is this test's own.
 */
class LootEngineCuePresentationTest {

    /** Every factor resolves to 0, which still REACHES a {@code Min: 0} floor (the shared ladder rule). */
    private static final RollEvaluator.FactorLookup ZERO_FACTORS = (factorId, param) -> 0.0;

    /** No fixture roll authors a Chance, so this sample is never drawn; it passes anyway if it were. */
    private static final DoubleSupplier ALWAYS_PASSES = () -> 0.0;

    private static final CommandRewardExecutor.Placeholders PLACEHOLDERS =
            new CommandRewardExecutor.Placeholders("Fixture_Worker", "fixture-uuid", "fixturestation", "Work", 1);

    private static Roll floorRoll(Roll.Grants floorGrants) {
        return Roll.of(Roll.TRIGGER_CYCLE, null, null,
                Roll.Ladder.of(null, new Roll.Ladder.Floor[] {
                        Roll.Ladder.Floor.of(0.0, floorGrants, Presentation.ofSound("Fixture_Fanfare"))}),
                null);
    }

    private static LootEngine.GrantResult pass(Roll roll, LootEngine.DropListGranter dropLists) {
        return LootEngine.rollAndGrant(List.of(roll), Roll.TRIGGER_CYCLE, ZERO_FACTORS, ALWAYS_PASSES,
                PLACEHOLDERS, dropLists);
    }

    // ==================== Floor-level cues ====================

    /** (a) The find that was not found: the floor is reached, its table hands over nothing, so nothing celebrates. */
    @Test
    void floorCue_isNotCollected_whenItsDropTableResolvesEmpty() {
        LootEngine.GrantResult result = pass(floorRoll(Roll.Grants.ofDropList("Fixture_Table")),
                DropListGranters.empty());

        assertTrue(result.getFloorPresentations().isEmpty(),
                "a table whose own internal weights rolled empty produced nothing to celebrate");
        assertTrue(result.getDropListItems().isEmpty());
    }

    /** (b) The same floor, the same authoring - the table pays this time, so the cue is earned. */
    @Test
    void floorCue_isCollected_whenItsDropTablePays() {
        LootEngine.GrantResult result = pass(floorRoll(Roll.Grants.ofDropList("Fixture_Table")),
                DropListGranters.paying("Fixture_Find", 2));

        assertEquals(1, result.getFloorPresentations().size(), "the grant landed, so the cue is earned");
        assertEquals("Fixture_Fanfare", result.getFloorPresentations().get(0).getSounds()[0]);
        assertEquals(2, result.getDropListItems().get("Fixture_Find"));
    }

    /** (c) A cue with NO grants beside it is pure presentation and plays on the reach, unchanged. */
    @Test
    void floorCue_withNoGrantsBesideIt_isCollectedOnReach() {
        LootEngine.GrantResult result = pass(floorRoll(null), DropListGranters.empty());

        assertEquals(1, result.getFloorPresentations().size(),
                "a pure cue has no grant to judge, so reaching the floor is the whole condition");
    }

    /** An empty (authored but leaf-less) grants group reads as no grants at all, same as absent. */
    @Test
    void floorCue_withAnEmptyGrantsGroup_isTreatedAsAPureCue() {
        LootEngine.GrantResult result = pass(floorRoll(Roll.Grants.of(null, null)), DropListGranters.empty());

        assertEquals(1, result.getFloorPresentations().size());
    }

    // ==================== Roll-level cues ====================

    /** (d) The plain-chance trophy shape: a command grant always produces, so its cue always accompanies the win. */
    @Test
    void rollCue_withACommandGrant_isCollectedOnHit() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null,
                Roll.Grants.of(null, new String[] {"give {player} Fixture_Trophy"}),
                Presentation.ofSound("Fixture_Fanfare"));

        LootEngine.GrantResult result = pass(roll, DropListGranters.empty());

        assertEquals(1, result.getCommandsRun(), "the command ran, which is a produced grant");
        assertEquals(1, result.getFloorPresentations().size(), "so the roll-level cue is earned");
        assertEquals("Fixture_Fanfare", result.getFloorPresentations().get(0).getSounds()[0]);
    }

    /** The roll-level cue obeys the same rule its floor sibling does when its own table pays nothing. */
    @Test
    void rollCue_isNotCollected_whenItsOwnDropTableResolvesEmpty() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null,
                Roll.Grants.ofDropList("Fixture_Table"), Presentation.ofSound("Fixture_Fanfare"));

        assertTrue(pass(roll, DropListGranters.empty()).getFloorPresentations().isEmpty());
    }

    /** No Ladder needed for a roll-level cue: that is the whole reason the leaf exists. */
    @Test
    void rollCue_withNoGrantsBesideIt_isCollectedOnHit() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null, null,
                Presentation.ofSound("Fixture_Fanfare"));

        assertEquals(1, pass(roll, DropListGranters.empty()).getFloorPresentations().size());
    }

    /** Both altitudes are judged independently, and both can ride the same pass - roll cue first. */
    @Test
    void bothAltitudes_areJudgedIndependently() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null,
                Roll.Ladder.of(null, new Roll.Ladder.Floor[] {
                        Roll.Ladder.Floor.of(0.0, Roll.Grants.ofDropList("Fixture_Table"),
                                Presentation.ofSound("Fixture_Floor_Cue"))}),
                Roll.Grants.of(null, new String[] {"give {player} Fixture_Trophy"}),
                Presentation.ofSound("Fixture_Roll_Cue"));

        LootEngine.GrantResult earnedOnlyByTheCommand = pass(roll, DropListGranters.empty());
        assertEquals(1, earnedOnlyByTheCommand.getFloorPresentations().size(),
                "the command produced, the floor's table did not");
        assertEquals("Fixture_Roll_Cue", earnedOnlyByTheCommand.getFloorPresentations().get(0).getSounds()[0]);

        LootEngine.GrantResult bothEarned = pass(roll, DropListGranters.paying("Fixture_Find", 1));
        assertEquals(List.of("Fixture_Roll_Cue", "Fixture_Floor_Cue"),
                List.of(bothEarned.getFloorPresentations().get(0).getSounds()[0],
                        bothEarned.getFloorPresentations().get(1).getSounds()[0]),
                "the roll-level cue is collected before the floor's, matching the grant order");
    }
}
