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

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
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
        assertFalse(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, id -> true))
                .contains("LOOT_UNKNOWN_TABLE"), "a lootableKnown fixture that always answers true never flags");
        assertTrue(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, id -> false))
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

    // ==================== Camera.FaceBlock / FaceBlockMode (unchanged) ====================

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
    void unknownFaceBlockMode_flagged() {
        StationAsset a = StationAsset.of("unknownfacemode",
                StationAsset.Identity.of("rpgstations.station.unknownfacemode.name", null, null),
                null, oakRecipe(),
                null, null,
                StationAsset.Camera.of("ThirdPerson", true, true, "not_a_real_preset"),
                null, null, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_FACE_BLOCK_MODE"));
    }

    @Test
    void seatFaceBlockConflict_flagged() {
        StationAsset a = StationAsset.of("seatconflict",
                StationAsset.Identity.of("rpgstations.station.seatconflict.name", null, null),
                null, oakRecipe(),
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Seat.of(true)),
                null,
                StationAsset.Camera.of("ThirdPerson", true, true),
                null, null, null);
        assertTrue(codes(validate(a)).contains("SEAT_FACE_BLOCK_CONFLICT"));
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
                Map.of("dead_flair", StationAsset.Flair.of(null, null)));
        assertTrue(codes(validate(a)).contains("EMPTY_FLAIR"));
    }
}
