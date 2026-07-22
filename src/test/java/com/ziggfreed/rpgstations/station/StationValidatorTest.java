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

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.validation.Finding;

/**
 * Exercises the singleton-free {@link StationValidator} core. Ported (reshaped) from the
 * MMO's {@code StationValidatorTest} (RPG Stations extraction leg 2): the skill-registry
 * tests ({@code UNKNOWN_XP_SKILL}/{@code UNKNOWN_LUCK_SKILL}/{@code UNKNOWN_SKILLTIER_SKILL})
 * are DROPPED (skill ids are not this engine's business, see {@link StationValidator}'s
 * javadoc); a new {@code UNKNOWN_FACTOR} test covers the Requires/Condition gate leg 2
 * introduces. Leg 3 replaces the old Luck.Tiers section with the {@link Roll}-based Loot
 * section (the M3 critique fixes' validator coverage: floor-Grants-only, duplicate floors,
 * empty rolls, {@code BonusOutputCopies} under a non-Cycle trigger, unknown ladder factors).
 */
public class StationValidatorTest {

    private static final Predicate<String> ANY_LANG = key -> true;
    private static final Predicate<String> NO_LANG = key -> false;
    private static final Predicate<String> ANY_DROP = id -> true;
    private static final Predicate<String> ANY_FACTOR = id -> true;
    private static final Predicate<String> NO_FACTOR = id -> false;

    private static Set<String> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).collect(Collectors.toSet());
    }

    private static List<Finding> validate(StationAsset a) {
        return StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR);
    }

    private static StationAsset.Conversion oakConversion() {
        return StationAsset.Conversion.of(
                StationAsset.Ingredient.item("Wood_Oak_Trunk", 1),
                StationAsset.Ingredient.item("Wood_Hardwood_Planks", 2));
    }

    private static StationAsset.Recipe oakRecipe() {
        return StationAsset.Recipe.of(new StationAsset.Conversion[]{oakConversion()});
    }

    private static StationAsset validStation() {
        return StationAsset.of("sawmill",
                StationAsset.Identity.of("rpgstations.station.sawmill.name", "rpgstations.station.sawmill.desc",
                        "Wood_Hardwood_Planks"),
                StationAsset.Work.of(5000L, 600000L, 1.5, true, new StationAsset.WorkXp[]{
                        StationAsset.WorkXp.of("WOODCUTTING", 8.0)}),
                oakRecipe(),
                StationAsset.Hold.of(true, "RPG_Station_Hold", true),
                null,
                StationAsset.Camera.of("ThirdPerson", true),
                StationAsset.Animation.of("RPG_Emote_Saw"),
                null, null);
    }

    @Test
    void validStation_producesNoFindings() {
        List<Finding> findings = validate(validStation());
        assertTrue(findings.isEmpty(), "a fully valid station is clean, got: " + codes(findings));
    }

    @Test
    void resourceTypeInput_isValid() {
        StationAsset a = StationAsset.of("nativesaw",
                StationAsset.Identity.of("rpgstations.station.nativesaw.name", null, null),
                null,
                StationAsset.Recipe.of(new StationAsset.Conversion[]{
                        StationAsset.Conversion.of(
                                StationAsset.Ingredient.resource("Wood_Hardwood_Trunk", 1),
                                StationAsset.Ingredient.item("Wood_Hardwood_Planks", 2))}),
                null, null, null, null, null, null);
        assertTrue(validate(a).isEmpty(), "a native resource-type input is clean, got: " + codes(validate(a)));
    }

    @Test
    void ambiguousInput_flagged() {
        StationAsset a = StationAsset.of("ambiguous",
                StationAsset.Identity.of("rpgstations.station.ambiguous.name", null, null),
                null,
                StationAsset.Recipe.of(new StationAsset.Conversion[]{
                        StationAsset.Conversion.of(
                                StationAsset.Ingredient.of("Wood_Oak_Trunk", "Wood_Hardwood_Trunk", 1),
                                StationAsset.Ingredient.item("Wood_Hardwood_Planks", 2))}),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("AMBIGUOUS_CONVERSION_INPUT"));
    }

    @Test
    void outputWithResourceType_flagged() {
        StationAsset a = StationAsset.of("badoutput",
                StationAsset.Identity.of("rpgstations.station.badoutput.name", null, null),
                null,
                StationAsset.Recipe.of(new StationAsset.Conversion[]{
                        StationAsset.Conversion.of(
                                StationAsset.Ingredient.item("Wood_Oak_Trunk", 1),
                                StationAsset.Ingredient.of("Wood_Hardwood_Planks", "Wood_Hardwood_Trunk", 2))}),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("OUTPUT_RESOURCE_TYPE"));
    }

    @Test
    void missingNameKey_flagged() {
        StationAsset a = StationAsset.of("bare", null, null, oakRecipe(), null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("MISSING_NAME_KEY"));
    }

    @Test
    void missingLangEntries_flagged() {
        List<Finding> findings = StationValidator.validate(List.of(validStation()), NO_LANG, ANY_DROP, ANY_FACTOR);
        assertTrue(codes(findings).contains("MISSING_NAME_LANG"));
        assertTrue(codes(findings).contains("MISSING_DESC_LANG"));
    }

    @Test
    void emptyConversions_flagged() {
        StationAsset a = StationAsset.of("noconvert",
                StationAsset.Identity.of("rpgstations.station.noconvert.name", null, null),
                null, StationAsset.Recipe.of(new StationAsset.Conversion[0]),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("EMPTY_CONVERSIONS"));

        StationAsset noRecipe = StationAsset.of("norecipe",
                StationAsset.Identity.of("rpgstations.station.norecipe.name", null, null),
                null, null, null, null, null, null, null, null);
        assertTrue(codes(validate(noRecipe)).contains("EMPTY_CONVERSIONS"));
    }

    @Test
    void fromCraftingOnly_isValid() {
        StationAsset a = StationAsset.of("crafter",
                StationAsset.Identity.of("rpgstations.station.crafter.name", null, null),
                null,
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(new String[]{"WoodPlanks"}, null)),
                null, null, null, null, null, null);
        List<Finding> findings = validate(a);
        assertFalse(codes(findings).contains("EMPTY_CONVERSIONS"));
        assertTrue(findings.isEmpty(), "a FromCrafting-only station is clean, got: " + codes(findings));
    }

    @Test
    void fromCraftingNoCategories_flagged() {
        StationAsset a = StationAsset.of("nocats",
                StationAsset.Identity.of("rpgstations.station.nocats.name", null, null),
                null,
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(new String[]{"  "}, null)),
                null, null, null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertTrue(codes.contains("FROMCRAFTING_NO_CATEGORIES"));
        assertFalse(codes.contains("EMPTY_CONVERSIONS"));
    }

    @Test
    void fromCraftingNonpositiveOutputPerInput_flagged() {
        StationAsset a = StationAsset.of("badmult",
                StationAsset.Identity.of("rpgstations.station.badmult.name", null, null),
                null,
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(new String[]{"WoodPlanks"}, 0)),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_OUTPUT_PER_INPUT"));
    }

    @Test
    void malformedConversions_flagged() {
        StationAsset a = StationAsset.of("broken",
                StationAsset.Identity.of("rpgstations.station.broken.name", null, null),
                null,
                StationAsset.Recipe.of(new StationAsset.Conversion[]{
                        StationAsset.Conversion.of(null, StationAsset.Ingredient.item("Wood_Hardwood_Planks", 2)),
                        StationAsset.Conversion.of(StationAsset.Ingredient.item("Wood_Oak_Trunk", 1), null),
                        StationAsset.Conversion.of(
                                StationAsset.Ingredient.item("Wood_Oak_Trunk", 0),
                                StationAsset.Ingredient.item("Wood_Hardwood_Planks", 2))}),
                null, null, null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertTrue(codes.contains("MISSING_CONVERSION_INPUT"));
        assertTrue(codes.contains("MISSING_CONVERSION_OUTPUT"));
        assertTrue(codes.contains("NONPOSITIVE_CONVERSION_COUNT"));
    }

    @Test
    void duplicateConversionInput_flagged() {
        StationAsset a = StationAsset.of("dupes",
                StationAsset.Identity.of("rpgstations.station.dupes.name", null, null),
                null,
                StationAsset.Recipe.of(new StationAsset.Conversion[]{
                        StationAsset.Conversion.of(
                                StationAsset.Ingredient.resource("Wood_Hardwood_Trunk", 1),
                                StationAsset.Ingredient.item("Wood_Hardwood_Planks", 2)),
                        StationAsset.Conversion.of(
                                StationAsset.Ingredient.resource("Wood_Hardwood_Trunk", 1),
                                StationAsset.Ingredient.item("Wood_Hardwood_Planks", 4))}),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("DUPLICATE_CONVERSION_INPUT"));
    }

    @Test
    void nonpositivePerCycle_flagged() {
        StationAsset a = StationAsset.of("badxp",
                StationAsset.Identity.of("rpgstations.station.badxp.name", null, null),
                StationAsset.Work.of(5000L, null, null, null, new StationAsset.WorkXp[]{
                        StationAsset.WorkXp.of("WOODCUTTING", 0.0)}),
                oakRecipe(),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_XP_PER_CYCLE"));
    }

    @Test
    void nonpositiveCycleMs_flagged() {
        StationAsset a = StationAsset.of("badcycle",
                StationAsset.Identity.of("rpgstations.station.badcycle.name", null, null),
                StationAsset.Work.of(0L, null, null, null, null),
                oakRecipe(),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_CYCLE_MS"));
    }

    @Test
    void blankAssetRefs_flagged() {
        StationAsset a = StationAsset.of("blankrefs",
                StationAsset.Identity.of("rpgstations.station.blankrefs.name", null, null),
                null,
                oakRecipe(),
                StationAsset.Hold.of(true, "", true),
                null,
                null,
                StationAsset.Animation.of(""),
                null, null);
        Set<String> codes = codes(validate(a));
        assertTrue(codes.contains("BLANK_EMOTE_ID"));
        assertTrue(codes.contains("BLANK_EFFECT_ID"));
    }

    @Test
    void emptyToolGate_flagged() {
        StationAsset a = StationAsset.of("noroute",
                StationAsset.Identity.of("rpgstations.station.noroute.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(Map.of(), null, new String[]{"", "  "}),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("EMPTY_TOOL_GATE"));
    }

    @Test
    void blankGatherType_flagged() {
        StationAsset a = StationAsset.of("blankgather",
                StationAsset.Identity.of("rpgstations.station.blankgather.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("  ", 0.1), new String[]{"Hatchet"}),
                null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertTrue(codes.contains("BLANK_GATHER_TYPE"));
        assertFalse(codes.contains("EMPTY_TOOL_GATE"));
    }

    @Test
    void validToolRoutes_producesNoToolFindings() {
        StationAsset a = StationAsset.of("gooltool",
                StationAsset.Identity.of("rpgstations.station.gooltool.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(Map.of("Family", new String[]{"Hatchet"}),
                        StationAsset.Tool.Gather.of("Woods", 0.1), null),
                null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertFalse(codes.contains("EMPTY_TOOL_GATE"));
        assertFalse(codes.contains("BLANK_GATHER_TYPE"));
    }

    // ==================== Tool.XpScale ====================

    @Test
    void deadXpScale_flagged() {
        StationAsset a = StationAsset.of("deadscale",
                StationAsset.Identity.of("rpgstations.station.deadscale.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("Woods", 0.1), null,
                        StationAsset.Tool.XpScale.of(null, null, null, null, null)),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("DEAD_XP_SCALE"));
    }

    @Test
    void xpScaleBadClamp_flagged() {
        StationAsset a = StationAsset.of("badclamp",
                StationAsset.Identity.of("rpgstations.station.badclamp.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("Woods", 0.1), null,
                        StationAsset.Tool.XpScale.of(null, 0.2, null, 1.5, 0.75)),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("XP_SCALE_BAD_CLAMP"));
    }

    // ==================== Tool.Durability ====================

    @Test
    void deadDurabilityGroup_flagged() {
        StationAsset a = StationAsset.of("deaddurability",
                StationAsset.Identity.of("rpgstations.station.deaddurability.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("Woods", 0.1), null, null,
                        StationAsset.Tool.Durability.of(0, -1)),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("DEAD_DURABILITY_GROUP"));
    }

    @Test
    void validDurability_producesNoDurabilityFindings() {
        StationAsset a = StationAsset.of("gentlewear",
                StationAsset.Identity.of("rpgstations.station.gentlewear.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("Woods", 0.1), null, null,
                        StationAsset.Tool.Durability.of(null, 1)),
                null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertFalse(codes.contains("DEAD_DURABILITY_GROUP"));
        assertFalse(codes.contains("DURABILITY_PERSWING_ADVISORY"));
    }

    // ==================== Loot (leg 3, REPLACES the Luck.Tiers section) ====================

    private static StationAsset.Loot loot(Roll... rolls) {
        return StationAsset.Loot.of(null, rolls);
    }

    private static Roll.Ladder.Floor floor(Double min, String dropList) {
        return Roll.Ladder.Floor.of(min, Roll.Grants.of(null, dropList, null), null);
    }

    private static Roll ladderRoll(Roll.Ladder.Floor... floors) {
        return Roll.of(null, null, null,
                Roll.Ladder.of(Condition.of("rpgstations:cycle_count", null, null, null), floors), null);
    }

    @Test
    void ladderFloorMissingMin_flagged() {
        StationAsset a = StationAsset.of("badtierfloor",
                StationAsset.Identity.of("rpgstations.station.badtierfloor.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(ladderRoll(floor(null, "T1"))));
        assertTrue(codes(validate(a)).contains("LOOT_LADDER_FLOOR_MISSING_MIN"));
    }

    @Test
    void ladderFloorEmptyGrants_flagged() {
        // M3 fix 2: a floor's ONLY reward path is its own Grants - null Grants is an error.
        StationAsset a = StationAsset.of("badtierdrop",
                StationAsset.Identity.of("rpgstations.station.badtierdrop.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(ladderRoll(Roll.Ladder.Floor.of(50.0, null, null))));
        assertTrue(codes(validate(a)).contains("LOOT_LADDER_FLOOR_EMPTY_GRANTS"));
    }

    @Test
    void ladderDuplicateFloor_flagged() {
        StationAsset a = StationAsset.of("dupefloor",
                StationAsset.Identity.of("rpgstations.station.dupefloor.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(ladderRoll(floor(50.0, "T1"), floor(50.0, "T1b"))));
        assertTrue(codes(validate(a)).contains("LOOT_LADDER_DUPLICATE_FLOOR"));
    }

    @Test
    void unknownDropList_onlyFlaggedByTheInjectedDropListLookup() {
        StationAsset a = StationAsset.of("unknowndrop",
                StationAsset.Identity.of("rpgstations.station.unknowndrop.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(ladderRoll(floor(50.0, "Ghost_Drop"))));
        assertFalse(codes(validate(a)).contains("LOOT_UNKNOWN_DROPLIST"), "the ANY_DROP fixture never flags");
        Set<String> unrelatedDrops = Set.of("Real_Drop");
        assertTrue(codes(StationValidator.validate(List.of(a), ANY_LANG, unrelatedDrops::contains, ANY_FACTOR))
                .contains("LOOT_UNKNOWN_DROPLIST"));
    }

    @Test
    void rollWithNeitherGrantsNorLadder_flaggedEmpty() {
        StationAsset a = StationAsset.of("emptyroll",
                StationAsset.Identity.of("rpgstations.station.emptyroll.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Cycle", null, null, null, null)));
        assertTrue(codes(validate(a)).contains("LOOT_ROLL_EMPTY"));
    }

    @Test
    void bonusOutputCopiesUnderCompletionTrigger_flagged() {
        // M3 fix 5: BonusOutputCopies makes sense only under a Cycle-trigger roll.
        StationAsset a = StationAsset.of("badbonustrigger",
                StationAsset.Identity.of("rpgstations.station.badbonustrigger.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Completion", null, null, null, Roll.Grants.of(1, null, null))));
        assertTrue(codes(validate(a)).contains("LOOT_BONUS_COPIES_WRONG_TRIGGER"));
    }

    @Test
    void bonusOutputCopiesUnderCycleTrigger_notFlagged() {
        StationAsset a = StationAsset.of("goodbonustrigger",
                StationAsset.Identity.of("rpgstations.station.goodbonustrigger.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Cycle", null, null, null, Roll.Grants.of(1, null, null))));
        assertFalse(codes(validate(a)).contains("LOOT_BONUS_COPIES_WRONG_TRIGGER"));
    }

    @Test
    void ladderValueUnknownFactor_flagged() {
        StationAsset a = StationAsset.of("badladderfactor",
                StationAsset.Identity.of("rpgstations.station.badladderfactor.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(ladderRoll(floor(50.0, "T1"))));
        assertTrue(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, NO_FACTOR))
                .contains("UNKNOWN_FACTOR"));
    }

    @Test
    void validLoot_producesNoLootFindings() {
        StationAsset a = StationAsset.of("goodloot",
                StationAsset.Identity.of("rpgstations.station.goodloot.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Cycle", null,
                        Roll.Chance.of(2.0, new Condition[]{Condition.of("rpgstations:tool_power", null, null, null)}, 25.0),
                        null, Roll.Grants.of(1, null, null))));
        assertTrue(validate(a).isEmpty(), "a fully valid Loot roll is clean, got: " + codes(validate(a)));
    }

    @Test
    void unknownLootTable_onlyFlaggedByTheInjectedLootableLookup() {
        StationAsset a = StationAsset.of("unknowntable",
                StationAsset.Identity.of("rpgstations.station.unknowntable.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                StationAsset.Loot.of(new String[]{"ghost_table"}, null));
        assertFalse(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, id -> true, id -> true))
                .contains("LOOT_UNKNOWN_TABLE"), "a lootableKnown fixture that always answers true never flags");
        assertTrue(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, id -> false, id -> true))
                .contains("LOOT_UNKNOWN_TABLE"));
    }

    // ==================== validateLootables (standalone LootableAsset content) ====================

    @Test
    void validateLootables_emptyTable_flagged() {
        com.ziggfreed.rpgstations.asset.LootableAsset table =
                com.ziggfreed.rpgstations.asset.LootableAsset.of("sawmillfinds", null);
        List<Finding> findings = StationValidator.validateLootables(List.of(table), ANY_DROP, ANY_FACTOR);
        assertTrue(findings.stream().map(Finding::code).anyMatch("LOOT_EMPTY_TABLE"::equals));
    }

    @Test
    void validateLootables_validRolls_producesNoFindings() {
        com.ziggfreed.rpgstations.asset.LootableAsset table = com.ziggfreed.rpgstations.asset.LootableAsset.of(
                "sawmillfinds", new Roll[]{Roll.of("Cycle", null,
                        Roll.Chance.of(2.0, new Condition[]{Condition.of("rpgstations:tool_power", null, null, null)}, 25.0),
                        null, Roll.Grants.of(1, null, null))});
        assertTrue(StationValidator.validateLootables(List.of(table), ANY_DROP, ANY_FACTOR).isEmpty());
    }

    // ==================== Requires.Conditions (new this leg) ====================

    @Test
    void unknownFactor_flagged() {
        StationAsset a = StationAsset.of("gatedstation",
                StationAsset.Identity.of("rpgstations.station.gatedstation.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null,
                Requires.of(null, new Condition[]{Condition.of("mmoskilltree:skill_level", "WOODCUTTING", 15.0, null)}));
        assertTrue(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, NO_FACTOR))
                .contains("UNKNOWN_FACTOR"));
        assertFalse(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR))
                .contains("UNKNOWN_FACTOR"), "a known-factor fixture never flags");
    }

    @Test
    void noRequires_noFactorFindings() {
        assertTrue(validate(validStation()).isEmpty());
    }

    // ==================== Animation.Swing ====================

    @Test
    void nonpositiveSwingInterval_flagged() {
        StationAsset a = StationAsset.of("badswing",
                StationAsset.Identity.of("rpgstations.station.badswing.name", null, null),
                null, oakRecipe(),
                null, null, null,
                StationAsset.Animation.of("RPG_Emote_Saw",
                        StationAsset.Animation.Swing.of(0L, Presentation.ofSound("SFX_Tool_T1_Swing"))),
                null, null);
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_SWING_INTERVAL"));
    }

    @Test
    void swingUnplayedLeaves_flagged() {
        StationAsset a = StationAsset.of("swingunplayed",
                StationAsset.Identity.of("rpgstations.station.swingunplayed.name", null, null),
                null, oakRecipe(),
                null, null, null,
                StationAsset.Animation.of("RPG_Emote_Saw",
                        StationAsset.Animation.Swing.of(1000L,
                                Presentation.of("SFX_Tool_T1_Swing", null, "SomeAnim", null, null, null, null))),
                null, null);
        assertTrue(codes(validate(a)).contains("SWING_UNPLAYED_LEAVES"));
    }

    @Test
    void swingWithShakeLeaf_isNotFlaggedAsUnplayed() {
        // Shake is PLAYED at the station-scale choke point this leg (unlike the MMO's Feedback,
        // which was never played there) - it must never trip the unplayed-leaves check.
        StationAsset a = StationAsset.of("swingshake",
                StationAsset.Identity.of("rpgstations.station.swingshake.name", null, null),
                null, oakRecipe(),
                null, null, null,
                StationAsset.Animation.of("RPG_Emote_Saw",
                        StationAsset.Animation.Swing.of(1000L,
                                Presentation.of("SFX_Tool_T1_Swing", null, null, null, null, null,
                                        Presentation.Shake.of("Damage_Shake", 0.4)))),
                null, null);
        assertFalse(codes(validate(a)).contains("SWING_UNPLAYED_LEAVES"));
    }

    @Test
    void validSwing_producesNoSwingFindings() {
        StationAsset a = StationAsset.of("goodswing",
                StationAsset.Identity.of("rpgstations.station.goodswing.name", null, null),
                null, oakRecipe(),
                null, null, null,
                StationAsset.Animation.of("RPG_Emote_Saw",
                        StationAsset.Animation.Swing.of(933L,
                                Presentation.of("SFX_Tool_T1_Swing", "Block_Hit_Wood"))),
                null, null);
        Set<String> codes = codes(validate(a));
        assertFalse(codes.contains("NONPOSITIVE_SWING_INTERVAL"));
        assertFalse(codes.contains("SWING_INTERVAL_SPAM"));
        assertFalse(codes.contains("SWING_WITHOUT_EMOTE"));
        assertFalse(codes.contains("SWING_UNPLAYED_LEAVES"));
    }

    // ==================== Camera.FaceBlock / Camera.Recipe (design 9.7 rename) ====================

    @Test
    void faceBlockWithoutCamera_flagged() {
        StationAsset a = StationAsset.of("facewithoutcam",
                StationAsset.Identity.of("rpgstations.station.facewithoutcam.name", null, null),
                null, oakRecipe(),
                null, null,
                StationAsset.Camera.of("None", null, true),
                null, null, null);
        assertTrue(codes(validate(a)).contains("FACE_BLOCK_WITHOUT_CAMERA"));
    }

    @Test
    void unknownCameraRecipe_flagged() {
        StationAsset a = StationAsset.of("unknownfacemode",
                StationAsset.Identity.of("rpgstations.station.unknownfacemode.name", null, null),
                null, oakRecipe(),
                null, null,
                StationAsset.Camera.of("ThirdPerson", true, true, "not_a_real_preset"),
                null, null, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_CAMERA_RECIPE"));
    }

    @Test
    void mountFaceBlockConflict_flagged_blockSurface() {
        StationAsset a = StationAsset.of("mountconflictblock",
                StationAsset.Identity.of("rpgstations.station.mountconflictblock.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Block", null)),
                null,
                StationAsset.Camera.of("ThirdPerson", true, true),
                null, null, null);
        assertTrue(codes(validate(a)).contains("MOUNT_FACE_BLOCK_CONFLICT"));
    }

    @Test
    void mountFaceBlockConflict_flagged_entitySurface() {
        StationAsset a = StationAsset.of("mountconflictentity",
                StationAsset.Identity.of("rpgstations.station.mountconflictentity.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Entity", null)),
                null,
                StationAsset.Camera.of("ThirdPerson", true, true),
                null, null, null);
        assertTrue(codes(validate(a)).contains("MOUNT_FACE_BLOCK_CONFLICT"));
    }

    // ==================== Hold.Mount (design 9.2, phase 2 leg D) ====================

    @Test
    void unknownMountSurface_flagged() {
        StationAsset a = StationAsset.of("unknownsurface",
                StationAsset.Identity.of("rpgstations.station.unknownsurface.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Chair", null)),
                null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_MOUNT_SURFACE"));
    }

    @Test
    void mountSurfaceBlock_noFinding() {
        StationAsset a = StationAsset.of("blocksurface",
                StationAsset.Identity.of("rpgstations.station.blocksurface.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Block", null)),
                null, null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertFalse(codes.contains("UNKNOWN_MOUNT_SURFACE"));
        assertFalse(codes.contains("MOUNT_ENTITY_GROUP_IGNORED"));
        assertFalse(codes.contains("MOUNT_STEERABLE_UNTESTED"));
    }

    @Test
    void mountEntityGroupIgnored_flagged_whenSurfaceIsBlock() {
        StationAsset.Hold.Mount.Entity entity = StationAsset.Hold.Mount.Entity.of(null, null, null);
        StationAsset a = StationAsset.of("entitygroupignored",
                StationAsset.Identity.of("rpgstations.station.entitygroupignored.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Block", entity)),
                null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("MOUNT_ENTITY_GROUP_IGNORED"));
    }

    @Test
    void mountSteerableUntested_flagged() {
        StationAsset.Hold.Mount.Entity entity = StationAsset.Hold.Mount.Entity.of(null, null, true);
        StationAsset a = StationAsset.of("steerable",
                StationAsset.Identity.of("rpgstations.station.steerable.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Entity", entity)),
                null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("MOUNT_STEERABLE_UNTESTED"));
    }

    @Test
    void mountSteerableDefault_noFinding() {
        StationAsset.Hold.Mount.Entity entity = StationAsset.Hold.Mount.Entity.of(null, null, null);
        StationAsset a = StationAsset.of("nonsteerable",
                StationAsset.Identity.of("rpgstations.station.nonsteerable.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Entity", entity)),
                null, null, null, null, null);
        assertFalse(codes(validate(a)).contains("MOUNT_STEERABLE_UNTESTED"));
    }

    // ==================== Completion / Flairs (unchanged) ====================

    @Test
    void completionUnplayedLeaves_flagged() {
        StationAsset a = StationAsset.of("completionunplayed",
                StationAsset.Identity.of("rpgstations.station.completionunplayed.name", null, null),
                null, oakRecipe(), null, null, null, null,
                Presentation.of("SFX_A", null, "SomeAnim", null, null, null, null), null, null, null,
                Presentation.of("SFX_B", null, "SomeAnim", null, null, null, null));
        assertTrue(codes(validate(a)).contains("COMPLETION_UNPLAYED_LEAVES"));
    }

    @Test
    void emptyFlair_flagged() {
        StationAsset a = StationAsset.of("emptyflair",
                StationAsset.Identity.of("rpgstations.station.emptyflair.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, null,
                Map.of("dead_flair", StationAsset.Flair.of(null)));
        assertTrue(codes(validate(a)).contains("EMPTY_FLAIR"));
    }

    @Test
    void flair_unknownMomentId_warned() {
        StationAsset a = StationAsset.of("unknownmoment",
                StationAsset.Identity.of("rpgstations.station.unknownmoment.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, null,
                Map.of("golden_saw", StationAsset.Flair.of(Map.of("cycel", Presentation.ofSound("SFX_Golden")))));
        assertTrue(codes(validate(a)).contains("UNKNOWN_FLAIR_MOMENT_ID"));
    }

    @Test
    void flair_knownMomentId_notWarned() {
        StationAsset a = StationAsset.of("knownmoment",
                StationAsset.Identity.of("rpgstations.station.knownmoment.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, null,
                Map.of("golden_saw", StationAsset.Flair.of(Map.of("swing", Presentation.ofSound("SFX_Golden")))));
        assertFalse(codes(validate(a)).contains("UNKNOWN_FLAIR_MOMENT_ID"));
    }

    @Test
    void flair_perStepMomentId_notWarned() {
        StationAsset a = StationAsset.of("stepmoment",
                StationAsset.Identity.of("rpgstations.station.stepmoment.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, null,
                Map.of("golden_saw", StationAsset.Flair.of(
                        Map.of("step:enhance:stamp", Presentation.ofSound("SFX_Golden")))));
        assertFalse(codes(validate(a)).contains("UNKNOWN_FLAIR_MOMENT_ID"));
    }

    // ==================== FlairAssets (design 9.6, phase 2 leg F) ====================

    @Test
    void flairAsset_emptyMoments_flagged() {
        com.ziggfreed.rpgstations.asset.FlairAsset fa =
                com.ziggfreed.rpgstations.asset.FlairAsset.of("dead_flair", null, null);
        List<Finding> findings = StationValidator.validateFlairAssets(List.of(fa), id -> true);
        assertTrue(codes(findings).contains("EMPTY_FLAIR"));
    }

    @Test
    void flairAsset_unknownMomentId_warned() {
        com.ziggfreed.rpgstations.asset.FlairAsset fa = com.ziggfreed.rpgstations.asset.FlairAsset.of(
                "golden_saw", null, Map.of("cycel", Presentation.ofSound("SFX_Golden")));
        List<Finding> findings = StationValidator.validateFlairAssets(List.of(fa), id -> true);
        assertTrue(codes(findings).contains("UNKNOWN_FLAIR_MOMENT_ID"));
    }

    @Test
    void flairAsset_unknownStation_warned() {
        com.ziggfreed.rpgstations.asset.FlairAsset fa = com.ziggfreed.rpgstations.asset.FlairAsset.of(
                "golden_saw", new String[]{"nonexistent_station"},
                Map.of("swing", Presentation.ofSound("SFX_Golden")));
        List<Finding> findings = StationValidator.validateFlairAssets(List.of(fa), id -> false);
        assertTrue(codes(findings).contains("FLAIR_ASSET_UNKNOWN_STATION"));
    }

    @Test
    void flairAsset_knownStation_notWarned() {
        com.ziggfreed.rpgstations.asset.FlairAsset fa = com.ziggfreed.rpgstations.asset.FlairAsset.of(
                "golden_saw", new String[]{"sawmill"},
                Map.of("swing", Presentation.ofSound("SFX_Golden")));
        List<Finding> findings = StationValidator.validateFlairAssets(List.of(fa), id -> true);
        assertFalse(codes(findings).contains("FLAIR_ASSET_UNKNOWN_STATION"));
    }

    @Test
    void flairAsset_nullStations_neverFlagged() {
        com.ziggfreed.rpgstations.asset.FlairAsset fa = com.ziggfreed.rpgstations.asset.FlairAsset.of(
                "golden_saw", null, Map.of("swing", Presentation.ofSound("SFX_Golden")));
        List<Finding> findings = StationValidator.validateFlairAssets(List.of(fa), id -> false);
        assertFalse(codes(findings).contains("FLAIR_ASSET_UNKNOWN_STATION"));
    }

    // ==================== Actions (design 9.1/9.3, phase 2 leg B - "never block") ====================

    private static StationAsset stationWithActions(Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions) {
        StationAsset a = StationAsset.of("multiaction",
                StationAsset.Identity.of("rpgstations.station.multiaction.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null);
        a.withActions(actions);
        return a;
    }

    @Test
    void noActionsMap_neverFlagsActionCodes() {
        // The implicit-single-action path (no Actions authored) must never touch checkActions.
        StationAsset a = StationAsset.of("bare", null, null, oakRecipe(), null, null, null, null, null, null);
        assertFalse(codes(validate(a)).stream().anyMatch(c -> c.startsWith("ACTION")
                || c.contains("STEP") || c.equals("UNREACHABLE_ACTION") || c.equals("AMBIGUOUS_ACTION_INPUT")));
    }

    @Test
    void actionWithNoRecipeOrSteps_flaggedNoBody() {
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("dead", com.ziggfreed.rpgstations.asset.ActionDef.of(
                null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertTrue(codes(validate(stationWithActions(actions))).contains("ACTION_NO_BODY"));
    }

    @Test
    void laterCatchAllAction_flaggedUnreachable() {
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("first", com.ziggfreed.rpgstations.asset.ActionDef.of(
                null, null, null, oakRecipe(), null, null, null, null, null, null, null, null, null));
        actions.put("second", com.ziggfreed.rpgstations.asset.ActionDef.of(
                null, null, null, oakRecipe(), null, null, null, null, null, null, null, null, null));
        assertTrue(codes(validate(stationWithActions(actions))).contains("UNREACHABLE_ACTION"));
    }

    @Test
    void duplicateExactItemIdAcrossActions_flaggedAmbiguous() {
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        com.ziggfreed.rpgstations.asset.ActionInput sameInput =
                com.ziggfreed.rpgstations.asset.ActionInput.of("Metal_Ingot", null, null, null);
        actions.put("convert1", com.ziggfreed.rpgstations.asset.ActionDef.of(
                null, sameInput, null, oakRecipe(), null, null, null, null, null, null, null, null, null));
        actions.put("convert2", com.ziggfreed.rpgstations.asset.ActionDef.of(
                null, sameInput, null, oakRecipe(), null, null, null, null, null, null, null, null, null));
        assertTrue(codes(validate(stationWithActions(actions))).contains("AMBIGUOUS_ACTION_INPUT"));
    }

    @Test
    void reservedStepType_flaggedUnimplemented() {
        // Stamp lands phase 2 leg E (design 9.5); Mount is the one type still schema-reserved.
        StationStep mount = StationStep.of("pose", StationStep.TYPE_MOUNT);
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("anvil", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{mount}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("UNIMPLEMENTED_STEP_TYPE"));
    }

    @Test
    void duplicateStepId_flagged() {
        StationStep a1 = StationStep.of("dup", StationStep.TYPE_WAIT)
                .withWait(StationStep.Wait.ofDurationMs(500L));
        StationStep a2 = StationStep.of("dup", StationStep.TYPE_WAIT)
                .withWait(StationStep.Wait.ofDurationMs(500L));
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("ritual", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{a1, a2}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("DUPLICATE_STEP_ID"));
    }

    @Test
    void consumeStepUnimplementedSource_flagged() {
        // "Custody" landed phase-2 leg C (design 9.4) and is no longer an unimplemented route -
        // use a genuinely unrecognized value to keep exercising the "unknown From" finding.
        StationStep consume = StationStep.of("c", StationStep.TYPE_CONSUME)
                .withConsume(StationStep.Consume.of("X", null, 1, "Bench"));
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("ritual", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{consume}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("UNIMPLEMENTED_CONSUME_SOURCE"));
    }

    @Test
    void consumeStepFromCustody_notFlagged() {
        // Custody is now an implemented route (design 9.4, phase-2 leg C) - it must NOT trip
        // UNIMPLEMENTED_CONSUME_SOURCE.
        StationStep consume = StationStep.of("c", StationStep.TYPE_CONSUME)
                .withConsume(StationStep.Consume.of("X", null, 1, "Custody"));
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("ritual", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{consume}));
        assertFalse(codes(validate(stationWithActions(actions))).contains("UNIMPLEMENTED_CONSUME_SOURCE"));
    }

    @Test
    void waitStepMissingDuration_flagged() {
        StationStep wait = StationStep.of("w", StationStep.TYPE_WAIT).withWait(StationStep.Wait.ofDurationMs(null));
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("ritual", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{wait}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("WAIT_MISSING_DURATION"));
    }

    @Test
    void unknownGotoTarget_flagged() {
        StationStep step = StationStep.of("w", StationStep.TYPE_WAIT)
                .withWait(StationStep.Wait.ofDurationMs(500L))
                .withOnConditionFail(StationStep.OnConditionFail.of(StationStep.OnConditionFail.RESULT_SKIP, "nope"));
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("ritual", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{step}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("UNKNOWN_GOTO_TARGET"));
    }

    @Test
    void knownGotoTarget_notFlagged() {
        StationStep target = StationStep.of("present", StationStep.TYPE_PRESENT);
        StationStep step = StationStep.of("w", StationStep.TYPE_WAIT)
                .withWait(StationStep.Wait.ofDurationMs(500L))
                .withOnConditionFail(StationStep.OnConditionFail.of(StationStep.OnConditionFail.RESULT_SKIP, "present"));
        Map<String, com.ziggfreed.rpgstations.asset.ActionDef> actions = new java.util.LinkedHashMap<>();
        actions.put("ritual", com.ziggfreed.rpgstations.asset.ActionDef.of(null, null, null, null, null, null,
                null, null, null, null, null, null, new StationStep[]{step, target}));
        assertFalse(codes(validate(stationWithActions(actions))).contains("UNKNOWN_GOTO_TARGET"));
    }

    // ==================== Custody (design section 9.4, phase-2 leg C) ====================

    @Test
    void custodyWithRecipeAndNoInput_notFlagged() {
        // The sawmill's own shape: Custody authors no explicit Input, but a Recipe exists to
        // derive placement acceptance from (the "logs by ResourceTypeId family" fallback).
        StationAsset a = validStation().withCustody(Custody.of(100, null, null));
        assertFalse(codes(validate(a)).contains("CUSTODY_NO_INPUT_MATCHER"));
    }

    @Test
    void custodyWithExplicitInputAndNoRecipe_notFlagged() {
        StationAsset a = StationAsset.of("anvil",
                        StationAsset.Identity.of("rpgstations.station.anvil.name", null, null),
                        StationAsset.Work.of(3000L, 600000L, 1.5, true, null),
                        null, null, null, null, null, null, null)
                .withCustody(Custody.of(1, ActionInput.of(null, null, null, "Weapon"), null));
        assertFalse(codes(validate(a)).contains("CUSTODY_NO_INPUT_MATCHER"));
    }

    @Test
    void custodyWithNoInputAndNoRecipe_flagged() {
        StationAsset a = StationAsset.of("anvil",
                        StationAsset.Identity.of("rpgstations.station.anvil.name", null, null),
                        StationAsset.Work.of(3000L, 600000L, 1.5, true, null),
                        null, null, null, null, null, null, null)
                .withCustody(Custody.of(1, null, null));
        assertTrue(codes(validate(a)).contains("CUSTODY_NO_INPUT_MATCHER"));
    }

    @Test
    void custodyNonPositiveMaxQuantity_flagged() {
        StationAsset a = validStation().withCustody(Custody.of(0, null, null));
        assertTrue(codes(validate(a)).contains("CUSTODY_NON_POSITIVE_MAX"));
    }

    @Test
    void noCustody_neverFlagsCustodyFindings() {
        assertFalse(codes(validate(validStation())).contains("CUSTODY_NO_INPUT_MATCHER"));
    }
}
