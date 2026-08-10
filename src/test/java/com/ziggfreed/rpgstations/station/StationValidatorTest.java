package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.validation.Finding;
import com.ziggfreed.rpgstations.validation.Severity;

/**
 * The singleton-free {@link StationValidator} core under the ACTION-FIRST schema: a station is
 * checked for its own four groups plus its ORDERED {@code Actions} list, and every job-shaped check
 * runs against ONE self-contained action's own groups. Fixtures author their own values throughout
 * (no production balance number appears here).
 *
 * <p>The posture is warn-only with ONE exception, covered below: a station with no actions at all is
 * an ERROR, because every group that makes a station DO something now lives inside an action.
 *
 * <p>Two cases are deliberately NOT singleton-free: the {@code Ref}-shaped
 * {@code LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT} pair resolves its base action through the live
 * {@link ActionCatalog} (the same lookup the runtime resolves a {@code Ref} with), so they fold a
 * fixture into it and clear it again in a {@code finally}.
 */
public class StationValidatorTest {

    private static final Predicate<String> ANY_LANG = key -> true;
    private static final Predicate<String> NO_LANG = key -> false;
    private static final Predicate<String> ANY_DROP = id -> true;
    private static final Predicate<String> NO_DROP = id -> false;
    private static final Predicate<String> ANY_FACTOR = id -> true;
    private static final Predicate<String> NO_FACTOR = id -> false;
    private static final Predicate<String> ANY_MODEL = id -> true;
    private static final Predicate<String> ANY_LOOTABLE = id -> true;
    private static final Predicate<String> NO_LOOTABLE = id -> false;
    private static final Predicate<String> ANY_ROLLPOOL = id -> true;
    private static final Predicate<String> ANY_STATION = id -> true;
    private static final Predicate<String> NO_STATION = id -> false;
    private static final Predicate<String> ANY_ACTION_ASSET = id -> true;
    private static final Predicate<String> NO_ACTION_ASSET = id -> false;

    private static Set<String> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).collect(Collectors.toSet());
    }

    private static List<Finding> validate(StationAsset a) {
        return StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR);
    }

    private static List<Finding> validateWithRefs(StationAsset a, Predicate<String> stationKnown,
            Predicate<String> actionAssetKnown) {
        return StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL,
                ANY_MODEL, stationKnown, actionAssetKnown);
    }

    // ==================== Fixture helpers ====================

    private static ActionAsset actionAsset(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ActionAsset.class, id, null);
        return ActionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    private static ExtensionAsset extensionAsset(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, id, null);
        return ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    private static StationAsset.Conversion trunkConversion() {
        return StationAsset.Conversion.of(
                Ingredient.item("Fixture_Trunk", 1),
                Ingredient.item("Fixture_Plank", 2));
    }

    private static StationAsset.Recipe trunkRecipe() {
        return StationAsset.Recipe.of(new StationAsset.Conversion[] {trunkConversion()});
    }

    /** A station carrying exactly the actions handed in, with a valid Identity. */
    private static StationAsset station(String id, ActionDef... actions) {
        return StationAsset.of(id,
                StationAsset.Identity.of("rpgstations.station." + id + ".name",
                        "rpgstations.station." + id + ".desc", "Fixture_Icon"),
                actions);
    }

    /** The canonical clean fixture: one action that converts, works, and holds. */
    private static StationAsset validStation() {
        return station("fixturemill", ActionDef.of("Mill")
                .withRecipe(trunkRecipe())
                .withWork(StationAsset.Work.of(5000L, 600000L, 1.5, new Contribution[] {
                        Contribution.of("yourmod:test", "ALPHA", 8.0)}))
                .withWorker(ActionDef.Worker.of(
                        StationAsset.Hold.of(true, "Fixture_Hold", true), null,
                        StationAsset.Animation.of("Fixture_Emote"), null)));
    }

    // ==================== The one ERROR: a station with no actions ====================

    @Test
    void validStation_producesNoFindings() {
        List<Finding> findings = validate(validStation());
        assertTrue(findings.isEmpty(), "a fully valid station is clean, got: " + codes(findings));
    }

    @Test
    void stationWithNoActions_isAnError() {
        List<Finding> findings = validate(station("empty"));
        assertTrue(codes(findings).contains("STATION_NO_ACTIONS"));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("STATION_NO_ACTIONS")
                        && f.severity() == Severity.ERROR),
                "an inert station is the one thing this validator refuses to shrug at");
    }

    @Test
    void everyOtherFinding_staysAdvisory() {
        StationAsset a = station("advisory", ActionDef.of("Mill")
                .withRecipe(StationAsset.Recipe.of(new StationAsset.Conversion[0]))
                .withCustody(Custody.of(-4, null, null)));
        assertTrue(validate(a).stream()
                        .filter(f -> !f.code().equals("STATION_NO_ACTIONS"))
                        .noneMatch(f -> f.severity() == Severity.ERROR),
                "the never-block posture holds for every check except the empty-Actions one");
    }

    // ==================== Identity ====================

    @Test
    void missingNameKey_flagged() {
        StationAsset a = StationAsset.of("bare", null, ActionDef.of("Mill").withRecipe(trunkRecipe()));
        assertTrue(codes(validate(a)).contains("MISSING_NAME_KEY"));
    }

    @Test
    void missingLangEntries_flagged() {
        List<Finding> findings = StationValidator.validate(List.of(validStation()), NO_LANG, ANY_DROP, ANY_FACTOR);
        assertTrue(codes(findings).contains("MISSING_NAME_LANG"));
        assertTrue(codes(findings).contains("MISSING_DESC_LANG"));
    }

    // ==================== The Actions contract: ids, order, bodies ====================

    @Test
    void actionWithoutAnId_warns() {
        StationAsset a = station("noid", new ActionDef().withRecipe(trunkRecipe()));
        assertTrue(codes(validate(a)).contains("ACTION_MISSING_ID"));
    }

    @Test
    void duplicateActionIds_warn_caseInsensitively() {
        StationAsset a = station("dupes",
                ActionDef.of("Mill").withRecipe(trunkRecipe()),
                ActionDef.of("mill").withRecipe(trunkRecipe()));
        assertTrue(codes(validate(a)).contains("ACTION_DUPLICATE_ID"));
    }

    @Test
    void actionWithNoRefRecipeOrSteps_warns() {
        StationAsset a = station("bodyless", ActionDef.of("Mill"));
        assertTrue(codes(validate(a)).contains("ACTION_NO_BODY"));
    }

    @Test
    void nullActionEntry_warns() {
        StationAsset a = station("holey", ActionDef.of("Mill").withRecipe(trunkRecipe()), null);
        assertTrue(codes(validate(a)).contains("EMPTY_ACTION_ENTRY"));
    }

    @Test
    void catchAllBeforeAnotherAction_makesTheLaterOneUnreachable() {
        StationAsset a = station("shadowed",
                ActionDef.of("Any").withRecipe(trunkRecipe()),
                ActionDef.of("Specific").withRecipe(trunkRecipe()));
        assertTrue(codes(validate(a)).contains("UNREACHABLE_ACTION"),
                "authored order IS selection priority, so nothing after a catch-all can run");
    }

    @Test
    void twoActionsSharingAnExactSelectRoute_flagAsAmbiguous() {
        StationAsset a = station("ambig",
                ActionDef.of("First").withRecipe(trunkRecipe())
                        .withSelect(ActionInput.of("Fixture_Trunk", null, null, null)),
                ActionDef.of("Second").withRecipe(trunkRecipe())
                        .withSelect(ActionInput.of("Fixture_Trunk", null, null, null)));
        assertTrue(codes(validate(a)).contains("AMBIGUOUS_ACTION_INPUT"));
    }

    @Test
    void unknownSelectFunction_flagged() {
        StationAsset a = station("badfn", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withSelect(ActionInput.of(null, null, null, "Vehicle")));
        assertTrue(codes(validate(a)).contains("UNKNOWN_ACTION_FUNCTION"));
    }

    @Test
    void danglingActionRef_flagged() {
        StationAsset a = station("reffed", ActionDef.of("Prep", "MissingAction"));
        assertTrue(codes(validateWithRefs(a, ANY_STATION, NO_ACTION_ASSET)).contains("ACTION_REF_UNKNOWN"));
        assertFalse(codes(validateWithRefs(a, ANY_STATION, ANY_ACTION_ASSET)).contains("ACTION_REF_UNKNOWN"));
    }

    // ==================== Recipe / conversions ====================

    @Test
    void resourceTypeInput_isValid() {
        StationAsset a = station("nativemill", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {
                        StationAsset.Conversion.of(
                                Ingredient.resource("Fixture_Family", 1),
                                Ingredient.item("Fixture_Plank", 2))})));
        assertTrue(validate(a).isEmpty(), "a native resource-type input is clean, got: " + codes(validate(a)));
    }

    @Test
    void ambiguousConversionInput_flagged() {
        StationAsset a = station("ambigin", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {
                        StationAsset.Conversion.of(
                                Ingredient.of("Fixture_Trunk", "Fixture_Family", 1),
                                Ingredient.item("Fixture_Plank", 2))})));
        assertTrue(codes(validate(a)).contains("AMBIGUOUS_CONVERSION_INPUT"));
    }

    @Test
    void outputWithResourceType_flagged() {
        StationAsset a = station("badout", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {
                        StationAsset.Conversion.of(
                                Ingredient.item("Fixture_Trunk", 1),
                                Ingredient.of("Fixture_Plank", "Fixture_Family", 2))})));
        assertTrue(codes(validate(a)).contains("OUTPUT_RESOURCE_TYPE"));
    }

    @Test
    void recipeWithNeitherConversionsNorFromCrafting_flagged() {
        StationAsset a = station("emptyrecipe", ActionDef.of("Mill")
                .withRecipe(StationAsset.Recipe.of(new StationAsset.Conversion[0])));
        assertTrue(codes(validate(a)).contains("RECIPE_ENTRY_EMPTY"));
    }

    @Test
    void duplicateConversionInput_flagged() {
        StationAsset a = station("dupin", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {
                        trunkConversion(),
                        StationAsset.Conversion.of(Ingredient.item("Fixture_Trunk", 1),
                                Ingredient.item("Fixture_Beam", 1))})));
        assertTrue(codes(validate(a)).contains("DUPLICATE_CONVERSION_INPUT"));
    }

    @Test
    void fromCraftingWithNeitherCategoriesNorBenches_flagged() {
        StationAsset a = station("noderive", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(new String[0]))));
        assertTrue(codes(validate(a)).contains("FROMCRAFTING_NO_CATEGORIES"));
    }

    @Test
    void fromCraftingUnknownType_flagged() {
        StationAsset a = station("badtype", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(
                        new String[] {"FixturePlanks"}, null, new String[] {"Smelting"}, null))));
        assertTrue(codes(validate(a)).contains("FROMCRAFTING_UNKNOWN_TYPE"));
    }

    // ==================== Yield: the four deterministic leaves ====================

    @Test
    void yieldNonPositiveBase_flagged() {
        StationAsset a = station("badbase", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {trunkConversion()}, null,
                        StationAsset.Yield.of(0, null, null, null))));
        assertTrue(codes(validate(a)).contains("YIELD_NONPOSITIVE_BASE"));
    }

    @Test
    void yieldNonPositiveScale_flagged() {
        StationAsset a = station("badscale", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {trunkConversion()}, null,
                        StationAsset.Yield.of(1, -1.0, null, null))));
        assertTrue(codes(validate(a)).contains("YIELD_NONPOSITIVE_SCALE"));
    }

    @Test
    void yieldMinAboveMax_flagged() {
        StationAsset a = station("badclamp", ActionDef.of("Mill").withRecipe(
                StationAsset.Recipe.of(new StationAsset.Conversion[] {trunkConversion()}, null,
                        StationAsset.Yield.of(1, null, 9, 2))));
        assertTrue(codes(validate(a)).contains("YIELD_MIN_ABOVE_MAX"));
    }

    // ==================== ContributionScale ====================

    @Test
    void contributionScaleEmpty_flagged() {
        StationAsset a = station("emptyscale", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withContributionScale(ContributionScale.of(null, null)));
        assertTrue(codes(validate(a)).contains("CONTRIBUTION_SCALE_EMPTY"));
    }

    @Test
    void contributionScaleFloorsWithoutFactors_flagged() {
        StationAsset a = station("floorsonly", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withContributionScale(ContributionScale.of(null,
                        new ContributionScale.Floor[] {ContributionScale.Floor.of(5.0, 2.0)})));
        assertTrue(codes(validate(a)).contains("CONTRIBUTION_SCALE_FLOORS_WITHOUT_FACTORS"));
    }

    @Test
    void contributionScaleFactorsWithoutFloors_flagged() {
        StationAsset a = station("factorsonly", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withContributionScale(ContributionScale.of(
                        new FactorRef[] {FactorRef.of("yourmod:axis", null, 1.0)}, null)));
        assertTrue(codes(validate(a)).contains("CONTRIBUTION_SCALE_FACTORS_WITHOUT_FLOORS"));
    }

    @Test
    void contributionScaleUnknownFactor_flagged() {
        StationAsset a = station("unknownaxis", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withContributionScale(ContributionScale.of(
                        new FactorRef[] {FactorRef.of("yourmod:axis", null, 1.0)},
                        new ContributionScale.Floor[] {ContributionScale.Floor.of(5.0, 2.0)})));
        List<Finding> findings = StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, NO_FACTOR);
        assertTrue(codes(findings).contains("UNKNOWN_FACTOR"));
    }

    @Test
    void contributionScaleDuplicateFloorMin_flagged() {
        StationAsset a = station("dupfloor", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withContributionScale(ContributionScale.of(
                        new FactorRef[] {FactorRef.of("yourmod:axis", null, 1.0)},
                        new ContributionScale.Floor[] {
                                ContributionScale.Floor.of(5.0, 2.0),
                                ContributionScale.Floor.of(5.0, 3.0)})));
        assertTrue(codes(validate(a)).contains("LADDER_DUPLICATE_FLOOR_MIN"));
    }

    // ==================== Work + contributions ====================

    @Test
    void nonPositiveCycleMs_flagged() {
        StationAsset a = station("nocycle", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWork(StationAsset.Work.of(0L, null, null, null)));
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_CYCLE_MS"));
    }

    @Test
    void blankContributionChannel_flagged() {
        StationAsset a = station("blankchan", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWork(StationAsset.Work.of(5000L, null, null,
                        new Contribution[] {Contribution.of("  ", "ALPHA", 5.0)})));
        assertTrue(codes(validate(a)).contains("MISSING_CONTRIBUTION_CHANNEL"));
    }

    @Test
    void nonPositiveContributionAmount_flagged() {
        StationAsset a = station("zeroamt", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWork(StationAsset.Work.of(5000L, null, null,
                        new Contribution[] {Contribution.of("yourmod:test", "ALPHA", 0.0)})));
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_CONTRIBUTION_AMOUNT"));
    }

    @Test
    void idleFractionOutsideTheTinyContract_flagged() {
        StationAsset a = station("bigidle", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWork(StationAsset.Work.of(5000L, null, null, null,
                        StationAsset.Work.Idle.of(true, 20000L, 0.9))));
        assertTrue(codes(validate(a)).contains("IDLE_FRACTION_RANGE"));
    }

    // ==================== Tool (the action's ONE gate) ====================

    @Test
    void emptyToolGate_flagged() {
        StationAsset a = station("notool", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withTool(StationAsset.Tool.of(null, null, null)));
        assertTrue(codes(validate(a)).contains("EMPTY_TOOL_GATE"));
    }

    @Test
    void blankGatherType_flagged() {
        StationAsset a = station("blankgather", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withTool(StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("  ", 0.5),
                        new String[] {"Fixture_Tool"})));
        assertTrue(codes(validate(a)).contains("BLANK_GATHER_TYPE"));
    }

    @Test
    void emptyTagValues_flagged() {
        StationAsset a = station("emptytags", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withTool(StationAsset.Tool.of(Map.of("Family", new String[0]), null,
                        new String[] {"Fixture_Tool"})));
        assertTrue(codes(validate(a)).contains("TOOL_TAGS_EMPTY_VALUES"));
    }

    @Test
    void minStartPercentOutsideRange_flagged() {
        StationAsset a = station("badwear", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withTool(StationAsset.Tool.of(null, null, new String[] {"Fixture_Tool"},
                        StationAsset.Tool.Durability.of(null, null, 150.0))));
        assertTrue(codes(validate(a)).contains("TOOL_MIN_DURABILITY_OUT_OF_RANGE"));
    }

    @Test
    void deadDurabilityGroup_flagged() {
        StationAsset a = station("deadwear", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withTool(StationAsset.Tool.of(null, null, new String[] {"Fixture_Tool"},
                        StationAsset.Tool.Durability.of(0, 0))));
        assertTrue(codes(validate(a)).contains("DEAD_DURABILITY_GROUP"));
    }

    // ==================== Requires: station gate AND action gate ====================

    @Test
    void unknownFactor_flaggedOnBothTheStationAndTheActionGate() {
        Requires reqs = Requires.of(null, new Condition[] {Condition.of("yourmod:missing", null, 1.0, null)});
        StationAsset stationGate = station("stationgate", ActionDef.of("Mill").withRecipe(trunkRecipe()))
                .withRequires(reqs);
        StationAsset actionGate = station("actiongate",
                ActionDef.of("Mill").withRecipe(trunkRecipe()).withRequires(reqs));

        assertTrue(codes(StationValidator.validate(List.of(stationGate), ANY_LANG, ANY_DROP, NO_FACTOR))
                .contains("UNKNOWN_FACTOR"));
        assertTrue(codes(StationValidator.validate(List.of(actionGate), ANY_LANG, ANY_DROP, NO_FACTOR))
                .contains("UNKNOWN_FACTOR"));
    }

    // ==================== Bonus (the LootRef vocabulary) ====================

    @Test
    void unknownLootable_flagged() {
        StationAsset a = station("badtable", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(new String[] {"MissingTable"}, null)));
        List<Finding> findings = StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR,
                NO_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("LOOT_UNKNOWN_TABLE"));
    }

    @Test
    void blankLootableEntry_flagged() {
        StationAsset a = station("blanktable", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(new String[] {"  "}, null)));
        assertTrue(codes(validate(a)).contains("LOOT_BLANK_TABLE"));
    }

    @Test
    void unknownDropList_flagged() {
        StationAsset a = station("baddrop", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {
                        Roll.of(null, null, null, null, Roll.Grants.ofDropList("Missing_Drops"))})));
        List<Finding> findings = StationValidator.validate(List.of(a), ANY_LANG, NO_DROP, ANY_FACTOR);
        assertTrue(codes(findings).contains("LOOT_UNKNOWN_DROPLIST"));
    }

    @Test
    void emptyRoll_flagged() {
        StationAsset a = station("emptyroll", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {Roll.of(null, null, null, null, null)})));
        assertTrue(codes(validate(a)).contains("LOOT_ROLL_EMPTY"));
    }

    @Test
    void duplicateLadderFloorMin_flagged() {
        Roll roll = Roll.of(null, null, null,
                Roll.Ladder.of(new FactorRef[] {FactorRef.of("yourmod:axis", null, 1.0)},
                        new Roll.Ladder.Floor[] {
                                Roll.Ladder.Floor.of(5.0, Roll.Grants.ofOutputItems(1.0), null),
                                Roll.Ladder.Floor.of(5.0, Roll.Grants.ofOutputItems(2.0), null)}),
                null);
        StationAsset a = station("dupladder", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {roll})));
        assertTrue(codes(validate(a)).contains("LADDER_DUPLICATE_FLOOR_MIN"));
    }

    @Test
    void outputItemsOnACompletionRoll_flagged() {
        Roll roll = Roll.of(Roll.TRIGGER_COMPLETION, null, null, null, Roll.Grants.ofOutputItems(2.0));
        StationAsset a = station("badtrigger", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {roll})));
        assertTrue(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_WRONG_TRIGGER"),
                "a Completion roll has no cycle output to add items to, so the grant is dropped");
    }

    /**
     * The amount is fractional, so an amount BELOW one whole item is still a real grant and still
     * carries every trigger rule. A check that truncated to an int would silently stop warning here.
     */
    @Test
    void aFractionOnlyOutputItemsAmountOnACompletionRoll_isStillFlagged() {
        Roll roll = Roll.of(Roll.TRIGGER_COMPLETION, null, null, null, Roll.Grants.ofOutputItems(0.5));
        StationAsset a = station("badtriggerfraction", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {roll})));
        assertTrue(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_WRONG_TRIGGER"));
    }

    @Test
    void outputItemsOnACycleRoll_isClean() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null, Roll.Grants.ofOutputItems(2.0));
        StationAsset a = station("goodtrigger", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {roll})));
        assertFalse(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_WRONG_TRIGGER"));
        assertFalse(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"),
                "a recipe-driven action HAS a cycle output to add items to");
    }

    @Test
    void outputItemsOnAStepsActionBonus_flagged() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null, Roll.Grants.ofOutputItems(2.0));
        StationAsset a = station("ritualbonus", ActionDef.of("Enhance")
                .withSteps(new StationStep[] {StationStep.of("Beat")})
                .withBonus(LootRef.of(null, new Roll[] {roll})));
        assertTrue(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"),
                "an authored program has no single cycle output, so the grant is dropped at runtime");
    }

    @Test
    void outputItemsOnAStepRollPhase_flagged() {
        Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null, Roll.Grants.ofOutputItems(2.0));
        StationAsset a = station("ritualstep", ActionDef.of("Enhance")
                .withSteps(new StationStep[] {
                        StationStep.of("Beat").withRoll(LootRef.of(null, new Roll[] {roll}))}));
        assertTrue(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"),
                "a step's own Roll phase has the same missing cycle output");
    }

    @Test
    void outputItemsOnAnInlineEntryRefingASteppedAction_flagged() throws Exception {
        // The entry authors no Steps of its own - it Refs a stepped ActionAsset and overrides only
        // Bonus - so the program it actually runs is the base's, and its OutputItems grant has just
        // as little to add to as an inline Steps action's would.
        ActionAsset base = actionAsset("ritualfixture",
                "{ \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        ActionCatalog.getInstance().fold(Map.of("ritualfixture", base), true);
        try {
            Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null, Roll.Grants.ofOutputItems(2.0));
            StationAsset a = station("refbonus", ActionDef.of("Enhance", "ritualfixture")
                    .withBonus(LootRef.of(null, new Roll[] {roll})));
            assertTrue(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"),
                    "the effective program comes from the Ref base, so the grant is dropped there too");
        } finally {
            ActionCatalog.getInstance().fold(Map.of(), true);
        }
    }

    @Test
    void outputItemsOnAnInlineEntryRefingARecipeAction_isClean() throws Exception {
        // The mirror case: a Ref'd base that runs the recipe-driven convert loop HAS a cycle
        // output, so the same Bonus is fine.
        ActionAsset base = actionAsset("millfixture", "{ \"Recipe\": { \"Conversions\": [ {"
                + " \"Input\": [{ \"ItemId\": \"Fixture_Trunk\", \"Quantity\": 1 }],"
                + " \"Output\": [{ \"ItemId\": \"Fixture_Plank\", \"Quantity\": 2 }] } ] } }");
        ActionCatalog.getInstance().fold(Map.of("millfixture", base), true);
        try {
            Roll roll = Roll.of(Roll.TRIGGER_CYCLE, null, null, null, Roll.Grants.ofOutputItems(2.0));
            StationAsset a = station("refmill", ActionDef.of("Mill", "millfixture")
                    .withBonus(LootRef.of(null, new Roll[] {roll})));
            assertFalse(codes(validate(a)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"));
        } finally {
            ActionCatalog.getInstance().fold(Map.of(), true);
        }
    }

    @Test
    void contributionsOnACompletionRoll_flagged() {
        Roll roll = Roll.of(Roll.TRIGGER_COMPLETION, null, null, null,
                Roll.Grants.of(null, null, null,
                        new Contribution[] {Contribution.of("yourmod:test", "ALPHA", 5.0)}));
        StationAsset a = station("badpost", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withBonus(LootRef.of(null, new Roll[] {roll})));
        assertTrue(codes(validate(a)).contains("LOOT_CONTRIBUTION_WRONG_TRIGGER"));
    }

    // ==================== Custody ====================

    @Test
    void custodyWithNoInputMatcherAndNoRecipe_flagged() {
        StationAsset a = station("nomatcher", ActionDef.of("Prep")
                .withSteps(new StationStep[] {StationStep.of("Beat")})
                .withCustody(Custody.of(10, null, null)));
        assertTrue(codes(validate(a)).contains("CUSTODY_NO_INPUT_MATCHER"));
    }

    @Test
    void custodyDerivingFromItsOwnRecipe_isClean() {
        StationAsset a = station("derived", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withCustody(Custody.of(10, null, null)));
        assertFalse(codes(validate(a)).contains("CUSTODY_NO_INPUT_MATCHER"));
    }

    @Test
    void custodyNonPositiveMaxQuantity_flagged() {
        StationAsset a = station("badmax", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withCustody(Custody.of(0, null, null)));
        assertTrue(codes(validate(a)).contains("CUSTODY_NON_POSITIVE_MAX"));
    }

    @Test
    void singleFamilyWithACapacityOfOne_isRedundant() {
        StationAsset a = station("redundant", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withCustody(Custody.of(1, true, null, null, null)));
        assertTrue(codes(validate(a)).contains("CUSTODY_SINGLE_FAMILY_REDUNDANT"));
    }

    // ==================== Worker: camera, mount, animation, puppet ====================

    @Test
    void cameraRecipeWithCameraDisabled_flagged() {
        StationAsset a = station("deadcam", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(null,
                        StationAsset.Camera.of(false, true, "LookRot"), null, null)));
        assertTrue(codes(validate(a)).contains("CAMERA_RECIPE_WITHOUT_CAMERA"));
    }

    @Test
    void unknownCameraRecipe_flagged() {
        StationAsset a = station("badcam", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(null,
                        StationAsset.Camera.of(true, true, "Nonsense"), null, null)));
        assertTrue(codes(validate(a)).contains("UNKNOWN_CAMERA_RECIPE"));
    }

    @Test
    void unknownMountSurface_flagged() {
        StationAsset.Hold hold = StationAsset.Hold.of(true, "Fixture_Hold", true,
                StationAsset.Hold.Mount.of("Hovercraft", null));
        StationAsset a = station("badmount", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(hold, null, null, null)));
        assertTrue(codes(validate(a)).contains("UNKNOWN_MOUNT_SURFACE"));
    }

    @Test
    void entityGroupUnderABlockSurface_flagged() {
        StationAsset.Hold hold = StationAsset.Hold.of(true, "Fixture_Hold", true,
                StationAsset.Hold.Mount.of("Block",
                        StationAsset.Hold.Mount.Entity.of(null, null, null)));
        StationAsset a = station("ignoredentity", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(hold, null, null, null)));
        assertTrue(codes(validate(a)).contains("MOUNT_ENTITY_GROUP_IGNORED"));
    }

    @Test
    void mountPlusFixedLookCamera_conflicts() {
        StationAsset.Hold hold = StationAsset.Hold.of(true, "Fixture_Hold", true,
                StationAsset.Hold.Mount.of("Block", null));
        StationAsset a = station("conflict", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(hold,
                        StationAsset.Camera.of(true, true, "LookRot"), null, null)));
        assertTrue(codes(validate(a)).contains("MOUNT_FACE_BLOCK_CONFLICT"));
    }

    @Test
    void actionClipWithoutSwing_flagged() {
        StationAsset a = station("noclipswing", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(null, null,
                        StationAsset.Animation.of("Fixture_Emote", null, "Chop"), null)));
        assertTrue(codes(validate(a)).contains("ACTION_CLIP_WITHOUT_SWING"));
    }

    @Test
    void swingWithNonPositiveInterval_flagged() {
        StationAsset a = station("badswing", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(null, null,
                        StationAsset.Animation.of("Fixture_Emote",
                                StationAsset.Animation.Swing.of(0L)), null)));
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_SWING_INTERVAL"));
    }

    @Test
    void puppetWithNoMountAndMovementLockOff_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationAsset a = station("holdless", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(StationAsset.Hold.of(false, "Fixture_Hold", true),
                        null, null, puppet)));
        assertTrue(codes(validate(a)).contains("PUPPET_WITHOUT_HOLD"),
                "an unheld player can walk away from their own puppet");
    }

    @Test
    void unknownPuppetHideRoute_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of("Vanish", null), null, null, null, null);
        StationAsset a = station("badhide", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(StationAsset.Hold.of(true, "Fixture_Hold", true),
                        null, null, puppet)));
        assertTrue(codes(validate(a)).contains("UNKNOWN_PUPPET_HIDE_ROUTE"));
    }

    private static StationAsset stationWithPuppet(String id, Puppet.Look look, Rotation rotation) {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), look, null, rotation, null);
        return station(id, ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(StationAsset.Hold.of(true, "Fixture_Hold", true),
                        null, null, puppet)));
    }

    private static Puppet.Look npcRoleLook() {
        return Puppet.Look.of(Puppet.LOOK_SOURCE_NPC_ROLE, null,
                Puppet.Role.of("Fixture_Role", null, null, null));
    }

    @Test
    void npcRolePuppetWithAnAuthoredRoll_flagged() {
        StationAsset a = stationWithPuppet("rolledrole", npcRoleLook(), Rotation.of(null, null, 15.0));
        assertTrue(codes(validate(a)).contains("PUPPET_NPC_ROLE_ROLL_DROPPED"),
                "the NpcRole performer's leash carries heading and pitch only, so the bank never renders");
    }

    @Test
    void npcRolePuppetWithYawAndPitchOnly_notFlagged() {
        StationAsset a = stationWithPuppet("uprightrole", npcRoleLook(), Rotation.of(90.0, 12.0, null));
        assertFalse(codes(validate(a)).contains("PUPPET_NPC_ROLE_ROLL_DROPPED"),
                "yaw and pitch both survive the NpcRole performer");
    }

    @Test
    void npcRolePuppetWithAZeroRoll_notFlagged() {
        StationAsset a = stationWithPuppet("levelrole", npcRoleLook(), Rotation.of(null, null, 0.0));
        assertFalse(codes(validate(a)).contains("PUPPET_NPC_ROLE_ROLL_DROPPED"),
                "a level pose renders identically under every performer, so there is nothing to warn about");
    }

    @Test
    void playerClonePuppetWithAnAuthoredRoll_notFlagged() {
        StationAsset a = stationWithPuppet("rolledclone",
                Puppet.Look.of(Puppet.LOOK_SOURCE_PLAYER_CLONE, null, null), Rotation.of(null, null, 15.0));
        assertFalse(codes(validate(a)).contains("PUPPET_NPC_ROLE_ROLL_DROPPED"),
                "the bare-Holder performer carries the full roll axis");
    }

    // ==================== Anchors + steps ====================

    @Test
    void unknownAnchorStation_flagged() {
        StationAsset a = station("badanchor", ActionDef.of("Prep").withRecipe(trunkRecipe())
                .withAnchors(Map.of("Fire", ActionDef.Anchor.of("MissingStation", 8.0))));
        assertTrue(codes(validateWithRefs(a, NO_STATION, ANY_ACTION_ASSET)).contains("ANCHOR_STATION_UNKNOWN"));
    }

    @Test
    void walkToAnUndeclaredAnchor_flagged() {
        StationAsset a = station("badwalk", ActionDef.of("Prep").withSteps(new StationStep[] {
                StationStep.of("Go").withWalk(StationStep.Walk.of("Nowhere", null))}));
        assertTrue(codes(validate(a)).contains("WALK_TARGET_UNKNOWN_ANCHOR"));
    }

    @Test
    void walkWithoutAnEnabledPuppet_flagged() {
        StationAsset a = station("nopuppetwalk", ActionDef.of("Prep")
                .withAnchors(Map.of("Fire", ActionDef.Anchor.of("FixtureFire", 8.0)))
                .withSteps(new StationStep[] {
                        StationStep.of("Go").withWalk(StationStep.Walk.of("Fire", null))}));
        assertTrue(codes(validate(a)).contains("WALK_REQUIRES_PUPPET"));
    }

    @Test
    void duplicateStepIds_flagged() {
        StationAsset a = station("dupsteps", ActionDef.of("Prep").withSteps(new StationStep[] {
                StationStep.of("Beat").withDuration(StationStep.Duration.of(100L)),
                StationStep.of("Beat").withDuration(StationStep.Duration.of(100L))}));
        assertTrue(codes(validate(a)).contains("DUPLICATE_STEP_ID"));
    }

    // ==================== Standalone ActionAsset coverage ====================

    @Test
    void standaloneActionAsset_getsTheSameBodyChecks() throws Exception {
        ActionAsset asset = actionAsset("lonely", "{ \"Custody\": { \"MaxQuantity\": 10 },"
                + " \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        List<Finding> findings = StationValidator.validateActionAssets(List.of(asset), ANY_DROP, ANY_FACTOR,
                ANY_LOOTABLE, ANY_ROLLPOOL, ANY_MODEL, ANY_STATION);
        assertTrue(codes(findings).contains("CUSTODY_NO_INPUT_MATCHER"),
                "a standalone action is checked with its OWN groups, with no station to fall back on");
    }

    @Test
    void standaloneActionAsset_withARealBody_isClean() throws Exception {
        ActionAsset asset = actionAsset("prepfixture", "{ \"Label\": \"action.prepfixture.label\","
                + " \"Custody\": { \"MaxQuantity\": 10, \"Input\": { \"ResourceTypeId\": \"Fixture_Family\" } },"
                + " \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        List<Finding> findings = StationValidator.validateActionAssets(List.of(asset), ANY_DROP, ANY_FACTOR,
                ANY_LOOTABLE, ANY_ROLLPOOL, ANY_MODEL, ANY_STATION);
        assertTrue(findings.isEmpty(), "expected a clean standalone action, got: " + codes(findings));
    }

    // ==================== ExtensionAsset coverage ====================

    private static List<Finding> validateExtensions(ExtensionAsset ext, StationAsset station,
            ActionAsset action) {
        return StationValidator.validateExtensions(List.of(ext),
                station == null ? List.of() : List.of(station),
                action == null ? List.of() : List.of(action),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
    }

    @Test
    void extensionWithNoTarget_isAnError() throws Exception {
        ExtensionAsset e = extensionAsset("targetless", "{ \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        assertTrue(codes(validateExtensions(e, null, null)).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void extensionTargetingAnUnknownStation_flagged() throws Exception {
        ExtensionAsset e = extensionAsset("orphan", "{ \"Target\": { \"Station\": \"nosuch\" },"
                + " \"Actions\": [ { \"Id\": \"Extra\" } ] }");
        assertTrue(codes(validateExtensions(e, null, null)).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void stationTargetCarryingAnActionScopedPayload_isAMismatch() throws Exception {
        ExtensionAsset e = extensionAsset("mismatch", "{ \"Target\": { \"Station\": \"fixturemill\" },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_PAYLOAD_MISMATCH"),
                "a station holds no Bonus group any more, so the payload belongs on the ACTION");
    }

    @Test
    void actionTargetCarryingTheActionScopedPayloads_isClean() throws Exception {
        ActionAsset base = actionAsset("prepfixture", "{ \"Custody\": { \"MaxQuantity\": 10,"
                + " \"Input\": { \"ResourceTypeId\": \"Fixture_Family\" } },"
                + " \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        ExtensionAsset e = extensionAsset("progression", "{ \"Target\": { \"Action\": \"prepfixture\" },"
                + " \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\", \"Param\": \"ALPHA\","
                + "   \"Amount\": 4.0 } ],"
                + " \"ContributionScale\": { \"Factors\": [ { \"Factor\": \"yourmod:axis\" } ],"
                + "   \"Floors\": [ { \"Min\": 2, \"Scale\": 1.5 } ] },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        List<Finding> findings = validateExtensions(e, null, base);
        assertFalse(codes(findings).contains("EXTENSION_PAYLOAD_MISMATCH"));
        assertFalse(codes(findings).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    // ---------- the station-SCOPED Action target ----------

    @Test
    void scopedTargetNamingItsStationsOwnAction_isClean() throws Exception {
        ExtensionAsset e = extensionAsset("scoped", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        List<Finding> findings = validateExtensions(e, validStation(), null);
        assertFalse(codes(findings).contains("EXTENSION_TARGET_UNKNOWN"),
                "the station exists and resolves that action id, got: " + codes(findings));
        assertFalse(codes(findings).contains("EXTENSION_PAYLOAD_MISMATCH"),
                "a scoped target carries the same payload set a bare Action target does");
    }

    @Test
    void scopedTargetWhoseStationDoesNotResolveThatAction_flagged() throws Exception {
        ExtensionAsset e = extensionAsset("scoped", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"NoSuchAction\" },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_TARGET_UNKNOWN"),
                "a scoped target must resolve BOTH halves: the station, and that station's action id");
    }

    @Test
    void scopedTargetWhoseStationIsUnknown_flagged() throws Exception {
        ExtensionAsset e = extensionAsset("scoped", "{ \"Target\": { \"Station\": \"nosuchstation\","
                + " \"Action\": \"Mill\" },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void illegalTargetPairing_isAnError() throws Exception {
        ExtensionAsset e = extensionAsset("twoTargets", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Lootable\": \"FixtureFinds\" } }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_TARGET_UNKNOWN"),
                "Station+Action is the ONE legal pairing; every other combination names no single target");
    }

    @Test
    void twoExtensionsClaimingOneKeyOnDifferentStations_doNotCollide() throws Exception {
        // Both claim the anchor key "Well" on an action id "Mill", but scoped to different
        // stations - at runtime they never meet, so reporting a collision would be a false alarm.
        ExtensionAsset mine = extensionAsset("a_ext", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" }, \"Anchors\": { \"Well\": { \"Station\": \"fixturemill\" } } }");
        ExtensionAsset yours = extensionAsset("b_ext", "{ \"Target\": { \"Station\": \"otherbench\","
                + " \"Action\": \"Mill\" }, \"Anchors\": { \"Well\": { \"Station\": \"otherbench\" } } }");
        StationAsset other = station("otherbench", ActionDef.of("Mill").withRecipe(trunkRecipe()));

        List<Finding> findings = StationValidator.validateExtensions(List.of(mine, yours),
                List.of(validStation(), other), List.of(), ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_KEY_COLLISION"),
                "same key, same action id, different stations: no collision, got: " + codes(findings));
    }

    @Test
    void twoExtensionsClaimingOneKeyOnTheSameScopedTarget_stillCollide() throws Exception {
        ExtensionAsset first = extensionAsset("a_ext", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" }, \"Anchors\": { \"Well\": { \"Station\": \"fixturemill\" } } }");
        ExtensionAsset second = extensionAsset("b_ext", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" }, \"Anchors\": { \"Well\": { \"Station\": \"fixturemill\" } } }");

        List<Finding> findings = StationValidator.validateExtensions(List.of(first, second),
                List.of(validStation()), List.of(), ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_KEY_COLLISION"),
                "scoping narrows the claim, it never stops one being a claim");
    }

    @Test
    void aBareAndAScopedExtensionClaimingOneKey_collide() throws Exception {
        // The other half of the scoping rule: these two DO meet - the bare claim applies on every
        // station resolving "Mill", including the one the scoped claim names.
        ExtensionAsset bare = extensionAsset("a_ext", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Anchors\": { \"Well\": { \"Station\": \"fixturemill\" } } }");
        ExtensionAsset scoped = extensionAsset("b_ext", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" }, \"Anchors\": { \"Well\": { \"Station\": \"fixturemill\" } } }");

        List<Finding> findings = StationValidator.validateExtensions(List.of(bare, scoped),
                List.of(validStation()), List.of(), ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_KEY_COLLISION"),
                "a bare claim reaches the scoped station too, got: " + codes(findings));
    }

    @Test
    void aBareAndAScopedExtensionAppendingOneContributionPair_areFlagged() throws Exception {
        // Same overlap, on the UNKEYED payload: both apply at the sawmill, so the amounts sum.
        ExtensionAsset bare = extensionAsset("a_ext", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\", \"Param\": \"BETA\","
                + "   \"Amount\": 4.0 } ] }");
        ExtensionAsset scoped = extensionAsset("b_ext", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" }, \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\","
                + "   \"Param\": \"BETA\", \"Amount\": 4.0 } ] }");

        List<Finding> findings = StationValidator.validateExtensions(List.of(bare, scoped),
                List.of(validStation()), List.of(), ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_CONTRIBUTION_DUPLICATE"),
                "bare plus scoped both apply on that station, got: " + codes(findings));
    }

    @Test
    void twoScopedExtensionsOnDifferentStationsAppendingOnePair_areNotFlagged() throws Exception {
        ExtensionAsset mine = extensionAsset("a_ext", "{ \"Target\": { \"Station\": \"fixturemill\","
                + " \"Action\": \"Mill\" }, \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\","
                + "   \"Param\": \"BETA\", \"Amount\": 4.0 } ] }");
        ExtensionAsset yours = extensionAsset("b_ext", "{ \"Target\": { \"Station\": \"otherbench\","
                + " \"Action\": \"Mill\" }, \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\","
                + "   \"Param\": \"BETA\", \"Amount\": 4.0 } ] }");
        StationAsset other = station("otherbench", ActionDef.of("Mill").withRecipe(trunkRecipe()));

        List<Finding> findings = StationValidator.validateExtensions(List.of(mine, yours),
                List.of(validStation(), other), List.of(), ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_CONTRIBUTION_DUPLICATE"),
                "the two never meet at runtime, so their amounts never sum, got: " + codes(findings));
    }

    @Test
    void extensionActionIdCollidingWithABaseAction_flagged() throws Exception {
        ExtensionAsset e = extensionAsset("clash", "{ \"Target\": { \"Station\": \"fixturemill\" },"
                + " \"Actions\": [ { \"Id\": \"mill\" } ] }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_KEY_COLLISION"));
    }

    @Test
    void insertedStepWithoutAnId_flagged() throws Exception {
        ActionAsset base = actionAsset("prepfixture",
                "{ \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        ExtensionAsset e = extensionAsset("inserter", "{ \"Target\": { \"Action\": \"prepfixture\" },"
                + " \"Steps\": [ { \"Anchor\": { \"After\": \"Beat\" },"
                + "   \"Insert\": [ { \"Duration\": { \"Ms\": 50 } } ] } ] }");
        assertTrue(codes(validateExtensions(e, null, base)).contains("EXTENSION_STEP_MISSING_ID"));
    }

    @Test
    void insertionAnchoredOnAnUnknownStep_degradesWithAWarning() throws Exception {
        ActionAsset base = actionAsset("prepfixture",
                "{ \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        ExtensionAsset e = extensionAsset("inserter", "{ \"Target\": { \"Action\": \"prepfixture\" },"
                + " \"Steps\": [ { \"Anchor\": { \"After\": \"Nowhere\" },"
                + "   \"Insert\": [ { \"Id\": \"Extra\", \"Duration\": { \"Ms\": 50 } } ] } ] }");
        assertTrue(codes(validateExtensions(e, null, base)).contains("EXTENSION_ANCHOR_MISSING"));
    }

    @Test
    void extensionReDeclaringItsTargetsOwnContributionPair_flagged() throws Exception {
        ActionAsset base = actionAsset("prepfixture", "{ \"Work\": { \"PerCycleContributions\": ["
                + " { \"Channel\": \"yourmod:test\", \"Param\": \"ALPHA\", \"Amount\": 4.0 } ] },"
                + " \"Steps\": [ { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 100 } } ] }");
        ExtensionAsset e = extensionAsset("doubler", "{ \"Target\": { \"Action\": \"prepfixture\" },"
                + " \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\", \"Param\": \"ALPHA\","
                + "   \"Amount\": 4.0 } ] }");
        assertTrue(codes(validateExtensions(e, null, base)).contains("EXTENSION_CONTRIBUTION_DUPLICATE"),
                "PerCycleContributions is append-only, so a re-declared pair silently sums");
    }

    // ==================== Action targets: inline action ids count too ====================

    @Test
    void extensionTargetingAStationsInlineActionId_isNotUnknown() throws Exception {
        // validStation() authors an INLINE "Mill" action with no Ref, and no standalone ActionAsset
        // exists - the shape every Action-targeted extension a pack can currently write must take.
        ExtensionAsset e = extensionAsset("progression", "{ \"Target\": { \"Action\": \"mill\" },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        assertFalse(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_TARGET_UNKNOWN"),
                "an inline Actions entry's own id is the runtime's Action-target identity");
    }

    @Test
    void extensionTargetingNoActionAtAll_staysUnknown() throws Exception {
        ExtensionAsset e = extensionAsset("progression", "{ \"Target\": { \"Action\": \"nosuchaction\" },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] } }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void extensionRedeclaringAnInlineActionsContributionPair_isFlagged() throws Exception {
        // The silent half of the same gap: base-resolution checks used to no-op for an inline
        // target, so a doubled (Channel, Param) went unreported. validStation()'s Mill posts
        // (yourmod:test, ALPHA) on its own Work.PerCycleContributions.
        ExtensionAsset e = extensionAsset("progression", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\", \"Param\": \"ALPHA\","
                + "   \"Amount\": 4.0 } ] }");
        assertTrue(codes(validateExtensions(e, validStation(), null)).contains("EXTENSION_CONTRIBUTION_DUPLICATE"),
                "appending a pair the inline base action already declares SUMS the amounts");
    }

    @Test
    void extensionAnchoringOnAnInlineActionsUnknownStepId_isFlagged() throws Exception {
        StationAsset a = station("ritualbench", ActionDef.of("Enhance")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        ExtensionAsset e = extensionAsset("extrabeat", "{ \"Target\": { \"Action\": \"Enhance\" },"
                + " \"Steps\": [ { \"Anchor\": { \"After\": \"NoSuchStep\" },"
                + "   \"Insert\": [ { \"Id\": \"Extra\" } ] } ] }");
        assertTrue(codes(validateExtensions(e, a, null)).contains("EXTENSION_ANCHOR_MISSING"),
                "the inline action's own step program is resolvable, so a dangling anchor is catchable");
    }

    // ==================== An action's own Moments map ====================

    @Test
    void unknownMomentIdOnAnActionsMoments_flagged_theSameTypoWarnAFlairMapGets() {
        StationAsset a = station("typomoment", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withMoments(Map.of("cyclee", Presentation.ofSound("Fixture_Sound"))));
        assertTrue(codes(validate(a)).contains("UNKNOWN_MOMENT_ID"));
    }

    @Test
    void wellKnownAndStepMomentIds_pass_whateverTheirCasing() {
        StationAsset a = station("goodmoments", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withMoments(Map.of(
                        "Cycle", Presentation.ofSound("Fixture_A"),
                        "swing", Presentation.ofSound("Fixture_B"),
                        "IMPACT", Presentation.ofSound("Fixture_C"),
                        "step:mill:beat", Presentation.ofSound("Fixture_D"))));
        assertFalse(codes(validate(a)).contains("UNKNOWN_MOMENT_ID"));
    }

    @Test
    void impactMomentHeldPastAWholeSwing_flagged() {
        StationAsset a = station("lateimpact", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(null, null,
                        StationAsset.Animation.of("Fixture_Emote", StationAsset.Animation.Swing.of(900L)), null))
                .withMoments(Map.of("impact",
                        Presentation.of(null, null, null, null, null, 900L))));
        assertTrue(codes(validate(a)).contains("IMPACT_OVERLAPS_NEXT_SWING"));
    }

    @Test
    void impactMomentComfortablyInsideItsSwing_notFlagged() {
        StationAsset a = station("goodimpact", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withWorker(ActionDef.Worker.of(null, null,
                        StationAsset.Animation.of("Fixture_Emote", StationAsset.Animation.Swing.of(900L)), null))
                .withMoments(Map.of("impact",
                        Presentation.of(null, null, null, null, null, 140L))));
        assertFalse(codes(validate(a)).contains("IMPACT_OVERLAPS_NEXT_SWING"));
    }

    @Test
    void soundsEntryWithNoEventId_flagged() {
        StationAsset a = station("blanksound", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withMoments(Map.of("cycle", Presentation.of(
                        new Presentation.SoundCue[] {Presentation.SoundCue.of(null, 40L)},
                        null, null, null, null))));
        assertTrue(codes(validate(a)).contains("PRESENTATION_SOUND_MISSING_EVENT_ID"));
    }

    @Test
    void rareFindOnAnActionsMoments_flagged_thatCueComesFromTheRollThatEarnsIt() {
        StationAsset a = station("rarefindmoment", ActionDef.of("Mill").withRecipe(trunkRecipe())
                .withMoments(Map.of("Rare_Find", Presentation.ofSound("Fixture_Sound"))));
        assertTrue(codes(validate(a)).contains("RARE_FIND_MOMENT_NEVER_PLAYS"),
                "an action can never supply rare_find, so an entry keyed by it decodes and then never plays");
    }

    // ==================== Flairs (station scope) ====================

    @Test
    void emptyFlairMoments_flagged() {
        StationAsset a = station("flairless", ActionDef.of("Mill").withRecipe(trunkRecipe()))
                .withFlairs(Map.of("gilded", StationAsset.Flair.of(null)));
        assertTrue(codes(validate(a)).contains("EMPTY_FLAIR"));
    }

    @Test
    void unknownFlairMomentId_flagged() {
        StationAsset a = station("typoflair", ActionDef.of("Mill").withRecipe(trunkRecipe()))
                .withFlairs(Map.of("gilded", StationAsset.Flair.of(
                        Map.of("cyclee", Presentation.ofSound("Fixture_Sound")))));
        assertTrue(codes(validate(a)).contains("UNKNOWN_MOMENT_ID"));
    }
}
