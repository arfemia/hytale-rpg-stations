package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
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
 *
 * <p><b>Scope-2 rewrite (leg A4):</b> every fixture touching {@code StationStep} (no more
 * {@code Type} union - orthogonal phases), {@code Loot} (now {@link LootRef}), a
 * {@code Roll.Chance.AddFactors}/{@code Roll.Ladder.Values} ({@link FactorRef}s now), and
 * {@code Conversion.Input/Output} (now top-level {@link Ingredient}) is rebuilt against the
 * A-SCHEMA leg's rewritten codecs. New sections cover {@code ACTION_REF_UNKNOWN},
 * {@code ANCHOR_STATION_UNKNOWN}, {@code WALK_TARGET_UNKNOWN_ANCHOR},
 * {@code STEP_AT_UNKNOWN_ANCHOR}, {@code WALK_REQUIRES_PUPPET},
 * {@code LOOT_DUPLICATE_FACTOR}, the reshaped {@code Stamp.Caps.Budgets[]} checks, and the two new
 * standalone-collection validators ({@link StationValidator#validateActionAssets} /
 * {@link StationValidator#validateExtensions}) covering {@code EXTENSION_TARGET_UNKNOWN},
 * {@code EXTENSION_PAYLOAD_MISMATCH}, {@code EXTENSION_KEY_COLLISION},
 * {@code EXTENSION_ANCHOR_MISSING}, and {@code EXTENSION_STEP_MISSING_ID} (built via
 * {@code ActionAsset.CODEC}/{@code ExtensionAsset.CODEC} fixture decode, mirroring
 * {@code asset.ActionAssetCodecTest}/{@code asset.ExtensionAssetCodecTest}'s own pattern, since
 * neither new asset type exposes a Java-side payload builder). Dropped tests: the retired
 * {@code UNIMPLEMENTED_STEP_TYPE} (schema-reserved {@code Mount} type is GONE),
 * {@code UNIMPLEMENTED_CONSUME_SOURCE} (no more {@code From} allow-list check), and
 * {@code WAIT_MISSING_DURATION}/the {@code Wait} type entirely (retired for base-field
 * {@code Duration}).
 */
public class StationValidatorTest {

    private static final Predicate<String> ANY_LANG = key -> true;
    private static final Predicate<String> NO_LANG = key -> false;
    private static final Predicate<String> ANY_DROP = id -> true;
    private static final Predicate<String> ANY_FACTOR = id -> true;
    private static final Predicate<String> NO_FACTOR = id -> false;
    private static final Predicate<String> ANY_MODEL = id -> true;
    private static final Predicate<String> NO_MODEL = id -> false;
    private static final Predicate<String> ANY_LOOTABLE = id -> true;
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

    // ==================== Codec-decode fixture helpers (ActionAsset/ExtensionAsset have no
    // Java-side payload builder - the same decode-from-JSON pattern asset.ActionAssetCodecTest/
    // asset.ExtensionAssetCodecTest use) ====================

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

    private static StationAsset.Conversion oakConversion() {
        return StationAsset.Conversion.of(
                Ingredient.item("Wood_Oak_Trunk", 1),
                Ingredient.item("Wood_Hardwood_Planks", 2));
    }

    private static StationAsset.Recipe oakRecipe() {
        return StationAsset.Recipe.of(new StationAsset.Conversion[]{oakConversion()});
    }

    private static StationAsset validStation() {
        return StationAsset.of("sawmill",
                StationAsset.Identity.of("rpgstations.station.sawmill.name", "rpgstations.station.sawmill.desc",
                        "Wood_Hardwood_Planks"),
                StationAsset.Work.of(5000L, 600000L, 1.5, true, new Contribution[]{
                        Contribution.of("yourmod:test", "ALPHA", 8.0)}),
                oakRecipe(),
                StationAsset.Hold.of(true, "RPG_Station_Hold", true),
                null,
                StationAsset.Camera.of("ThirdPerson", true),
                StationAsset.Animation.of("RPG_Emote_Saw"),
                null, null);
    }

    /** An empty, fully-null {@link ActionDef} for tests that fill in only the leaves they need. */
    private static ActionDef actionDef() {
        return ActionDef.of(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static StationAsset stationWithActions(Map<String, ActionDef> actions) {
        StationAsset a = StationAsset.of("multiaction",
                StationAsset.Identity.of("rpgstations.station.multiaction.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null);
        a.withActions(actions);
        return a;
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
                                Ingredient.resource("Wood_Hardwood_Trunk", 1),
                                Ingredient.item("Wood_Hardwood_Planks", 2))}),
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
                                Ingredient.of("Wood_Oak_Trunk", "Wood_Hardwood_Trunk", 1),
                                Ingredient.item("Wood_Hardwood_Planks", 2))}),
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
                                Ingredient.item("Wood_Oak_Trunk", 1),
                                Ingredient.of("Wood_Hardwood_Planks", "Wood_Hardwood_Trunk", 2))}),
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
    void fromCraftingBenchesOnly_isALiveDerivationRoute_noError() {
        // Seam wave (decision 51c): Benches is a legitimate derivation ROUTE the live deriver now
        // executes (StationRecipeDeriver matches Categories OR Benches), so a station scoping by
        // Benches alone (a native recipe carrying no Categories, like CookingFire's Campfire bench)
        // neither errors as dead-derivation NOR warns as not-yet-wired (the transitional
        // FROMCRAFTING_BENCHES_NOT_YET_DERIVED warn was removed once the deriver caught up).
        StationAsset a = StationAsset.of("benchesonly",
                StationAsset.Identity.of("rpgstations.station.benchesonly.name", null, null),
                null,
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(null, null,
                        new String[]{"Campfire"}, new String[]{"Processing"}, null)),
                null, null, null, null, null, null);
        Set<String> codes = codes(validate(a));
        assertFalse(codes.contains("FROMCRAFTING_NO_CATEGORIES"));
        assertFalse(codes.contains("EMPTY_CONVERSIONS"));
        assertFalse(codes.contains("FROMCRAFTING_BENCHES_NOT_YET_DERIVED"));
    }

    @Test
    void fromCraftingUnknownType_flagged() {
        StationAsset a = StationAsset.of("badtype",
                StationAsset.Identity.of("rpgstations.station.badtype.name", null, null),
                null,
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(new String[]{"WoodPlanks"}, null,
                        null, new String[]{"Bogus"}, null)),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("FROMCRAFTING_UNKNOWN_TYPE"));
    }

    @Test
    void fromCraftingNativeTimeNonpositiveScale_flagged() {
        StationAsset a = StationAsset.of("badscale",
                StationAsset.Identity.of("rpgstations.station.badscale.name", null, null),
                null,
                StationAsset.Recipe.of(null, StationAsset.FromCrafting.of(new String[]{"WoodPlanks"}, null,
                        null, null, StationAsset.FromCrafting.NativeTime.of(0.0, null))),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("FROMCRAFTING_NATIVETIME_NONPOSITIVE_SCALE"));
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
                        StationAsset.Conversion.of(null, Ingredient.item("Wood_Hardwood_Planks", 2)),
                        StationAsset.Conversion.of(Ingredient.item("Wood_Oak_Trunk", 1), null),
                        StationAsset.Conversion.of(
                                Ingredient.item("Wood_Oak_Trunk", 0),
                                Ingredient.item("Wood_Hardwood_Planks", 2))}),
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
                                Ingredient.resource("Wood_Hardwood_Trunk", 1),
                                Ingredient.item("Wood_Hardwood_Planks", 2)),
                        StationAsset.Conversion.of(
                                Ingredient.resource("Wood_Hardwood_Trunk", 1),
                                Ingredient.item("Wood_Hardwood_Planks", 4))}),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("DUPLICATE_CONVERSION_INPUT"));
    }

    @Test
    void nonpositiveContributionAmount_flagged() {
        StationAsset a = StationAsset.of("badamount",
                StationAsset.Identity.of("rpgstations.station.badamount.name", null, null),
                StationAsset.Work.of(5000L, null, null, null, new Contribution[]{
                        Contribution.of("yourmod:test", "ALPHA", 0.0)}),
                oakRecipe(),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("NONPOSITIVE_CONTRIBUTION_AMOUNT"));
    }

    @Test
    void missingContributionChannel_flagged() {
        StationAsset a = StationAsset.of("nochannel",
                StationAsset.Identity.of("rpgstations.station.nochannel.name", null, null),
                StationAsset.Work.of(5000L, null, null, null, new Contribution[]{
                        Contribution.of("  ", "ALPHA", 4.0)}),
                oakRecipe(),
                null, null, null, null, null, null);
        assertTrue(codes(validate(a)).contains("MISSING_CONTRIBUTION_CHANNEL"));
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
    void toolTagsEmptyValues_flagged() {
        Map<String, String[]> tags = new LinkedHashMap<>();
        tags.put("Family", new String[0]);
        StationAsset a = StationAsset.of("emptytagvalues",
                StationAsset.Identity.of("rpgstations.station.emptytagvalues.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(tags, null, null),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("TOOL_TAGS_EMPTY_VALUES"));
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

    // ==================== Tool.PowerScale ====================

    @Test
    void deadPowerScale_flagged() {
        StationAsset a = StationAsset.of("deadscale",
                StationAsset.Identity.of("rpgstations.station.deadscale.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("Woods", 0.1), null,
                        StationAsset.Tool.PowerScale.of(null, null, null, null, null)),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("DEAD_POWER_SCALE"));
    }

    @Test
    void powerScaleBadClamp_flagged() {
        StationAsset a = StationAsset.of("badclamp",
                StationAsset.Identity.of("rpgstations.station.badclamp.name", null, null),
                null, oakRecipe(),
                null,
                StationAsset.Tool.of(null, StationAsset.Tool.Gather.of("Woods", 0.1), null,
                        StationAsset.Tool.PowerScale.of(null, 0.2, null, 1.5, 0.75)),
                null, null, null, null);
        assertTrue(codes(validate(a)).contains("POWER_SCALE_BAD_CLAMP"));
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

    // ==================== Loot (scope-2: LootRef + weighted FactorRef vocabulary) ====================

    private static LootRef loot(Roll... rolls) {
        return LootRef.of(null, rolls);
    }

    private static Roll.Ladder.Floor floor(Double min, String dropList) {
        return Roll.Ladder.Floor.of(min, Roll.Grants.of(null, dropList, null), null);
    }

    private static Roll ladderRoll(Roll.Ladder.Floor... floors) {
        return Roll.of(null, null, null,
                Roll.Ladder.of(new FactorRef[]{FactorRef.of("rpgstations:cycle_count", null)}, floors), null);
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
    void ladderMissingValues_flagged() {
        StationAsset a = StationAsset.of("noladdervalues",
                StationAsset.Identity.of("rpgstations.station.noladdervalues.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of(null, null, null, Roll.Ladder.of(null, new Roll.Ladder.Floor[]{floor(50.0, "T1")}), null)));
        assertTrue(codes(validate(a)).contains("LOOT_LADDER_MISSING_VALUE"));
    }

    @Test
    void validLoot_producesNoLootFindings() {
        StationAsset a = StationAsset.of("goodloot",
                StationAsset.Identity.of("rpgstations.station.goodloot.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Cycle", null,
                        Roll.Chance.of(2.0, new FactorRef[]{FactorRef.of("rpgstations:tool_power", null)}, 25.0),
                        null, Roll.Grants.of(1, null, null))));
        assertTrue(validate(a).isEmpty(), "a fully valid Loot roll is clean, got: " + codes(validate(a)));
    }

    @Test
    void unknownLootTable_onlyFlaggedByTheInjectedLootableLookup() {
        StationAsset a = StationAsset.of("unknowntable",
                StationAsset.Identity.of("rpgstations.station.unknowntable.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                LootRef.of(new String[]{"ghost_table"}, null));
        assertFalse(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, id -> true, id -> true))
                .contains("LOOT_UNKNOWN_TABLE"), "a lootableKnown fixture that always answers true never flags");
        assertTrue(codes(StationValidator.validate(List.of(a), ANY_LANG, ANY_DROP, ANY_FACTOR, id -> false, id -> true))
                .contains("LOOT_UNKNOWN_TABLE"));
    }

    // ============ LOOT_DUPLICATE_FACTOR (the generic redundant-reference lint) ============

    @Test
    void lootDuplicateFactorPair_flagged() {
        // The only shape that genuinely fires: a param-less zero-arg engine factor read twice in
        // one roll (once as a gate, once as a chance term) - the same number, counted twice.
        Roll roll = Roll.of("Cycle",
                new Condition[]{Condition.of("rpgstations:cycle_count", null, 5.0, null)},
                Roll.Chance.of(0.0, new FactorRef[]{FactorRef.of("rpgstations:cycle_count", null)}, 90.0),
                null, Roll.Grants.of(null, "T1", null));
        StationAsset a = StationAsset.of("dupefactor",
                StationAsset.Identity.of("rpgstations.station.dupefactor.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, loot(roll));
        assertTrue(codes(validate(a)).contains("LOOT_DUPLICATE_FACTOR"));
    }

    @Test
    void lootSameFactorDifferentParams_notFlagged() {
        // LOAD-BEARING: the lint keys on the (Factor, Param) PAIR, never the factor id alone.
        // Every stat read carries the same "stat" factor id, so a ladder composing two DIFFERENT
        // stat channels is correct, documented, shipped content - and must stay silent.
        Roll roll = Roll.of("Cycle", null, null,
                Roll.Ladder.of(new FactorRef[]{
                        FactorRef.of("stat", "Yourmod_Luck"),
                        FactorRef.of("stat", "Yourmod_Luck_Woodcutting")},
                        new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, Roll.Grants.of(null, "T1", null), null)}),
                null);
        StationAsset a = StationAsset.of("twostats",
                StationAsset.Identity.of("rpgstations.station.twostats.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, loot(roll));
        assertFalse(codes(validate(a)).contains("LOOT_DUPLICATE_FACTOR"));
    }

    @Test
    void lootSingleFactorReference_notFlagged() {
        Roll roll = Roll.of("Cycle", null,
                Roll.Chance.of(0.0, new FactorRef[]{FactorRef.of("stat", "Yourmod_Luck")}, 90.0),
                null, Roll.Grants.of(null, "T1", null));
        StationAsset a = StationAsset.of("onefactor",
                StationAsset.Identity.of("rpgstations.station.onefactor.name", null, null),
                null, oakRecipe(), null, null, null, null, null, null, loot(roll));
        assertFalse(codes(validate(a)).contains("LOOT_DUPLICATE_FACTOR"));
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
                        Roll.Chance.of(2.0, new FactorRef[]{FactorRef.of("rpgstations:tool_power", null)}, 25.0),
                        null, Roll.Grants.of(1, null, null))});
        assertTrue(StationValidator.validateLootables(List.of(table), ANY_DROP, ANY_FACTOR).isEmpty());
    }

    // ==================== Requires.Conditions (unchanged shape) ====================

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
        // Shake is PLAYED at the station-scale choke point this leg - it must never trip the
        // unplayed-leaves check.
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

    // ==================== Camera.FaceBlock / Camera.Recipe ====================

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

    // ==================== Hold.Mount ====================

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

    // ==================== Puppet (round-4 puppet-presentation design, unchanged by scope-2) ====================

    private static StationAsset puppetStation(String id, Puppet puppet, StationAsset.Hold hold) {
        return StationAsset.of(id,
                StationAsset.Identity.of("rpgstations.station." + id + ".name", null, null),
                null, oakRecipe(), hold, null, null, null, null, null)
                .withPuppet(puppet);
    }

    @Test
    void validPuppet_producesNoFindings() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null),
                Puppet.Look.of(Puppet.LOOK_SOURCE_PLAYER_CLONE, null, null), null, null,
                Puppet.Prop.of(Puppet.PROP_SOURCE_MIRROR_HELD, null, null));
        StationAsset a = puppetStation("validpuppet", puppet,
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Entity", null)));
        assertTrue(validate(a).isEmpty(), "a fully valid active puppet is clean, got: " + codes(validate(a)));
    }

    @Test
    void puppetWithoutHide_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_NONE, null), null, null, null, null);
        StationAsset a = puppetStation("puppetnohide", puppet,
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Entity", null)));
        assertTrue(codes(validate(a)).contains("PUPPET_WITHOUT_HIDE"));
    }

    @Test
    void hideWithoutPuppet_flagged() {
        Puppet puppet = Puppet.of(false, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationAsset a = puppetStation("hidenopuppet", puppet, null);
        assertTrue(codes(validate(a)).contains("HIDE_WITHOUT_PUPPET"));
    }

    @Test
    void puppetWithoutHold_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationAsset a = puppetStation("puppetnohold", puppet, StationAsset.Hold.of(false, null, null, null));
        assertTrue(codes(validate(a)).contains("PUPPET_WITHOUT_HOLD"));
    }

    @Test
    void puppetWithHold_movementLockDefaultsTrueWhenHoldOmitted_noFinding() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        // Hold omitted entirely - StationService's own reader default still resolves MovementLock
        // true, so the player is never left un-held.
        StationAsset a = puppetStation("puppetdefaulthold", puppet, null);
        assertFalse(codes(validate(a)).contains("PUPPET_WITHOUT_HOLD"));
    }

    @Test
    void unknownPuppetHideRoute_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of("Invisible", null), null, null, null, null);
        StationAsset a = puppetStation("unknownhideroute", puppet, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_PUPPET_HIDE_ROUTE"));
    }

    @Test
    void puppetHideEffectMissingId_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_EFFECT, null), null, null, null, null);
        StationAsset a = puppetStation("hideeffectnoid", puppet, null);
        assertTrue(codes(validate(a)).contains("PUPPET_HIDE_EFFECT_MISSING_ID"));
    }

    @Test
    void puppetHideEffect_withId_noFinding() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_EFFECT, EffectRef.of("Portal_Teleport")), null, null, null, null);
        StationAsset a = puppetStation("hideeffectwithid", puppet, null);
        assertFalse(codes(validate(a)).contains("PUPPET_HIDE_EFFECT_MISSING_ID"));
    }

    @Test
    void unknownPuppetLookSource_flagged() {
        Puppet puppet = Puppet.of(true, null, Puppet.Look.of("Golem", null, null), null, null, null);
        StationAsset a = puppetStation("unknownlooksource", puppet, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_PUPPET_LOOK_SOURCE"));
    }

    @Test
    void puppetLookModelUnknown_flagged() {
        Puppet puppet = Puppet.of(true, null, Puppet.Look.model("NPC_Ghost", null),
                null, null, null);
        List<Finding> findings = StationValidator.validate(List.of(puppetStation("looksmodelunknown", puppet, null)),
                ANY_LANG, ANY_DROP, ANY_FACTOR, id -> true, id -> true, NO_MODEL);
        assertTrue(codes(findings).contains("PUPPET_LOOK_MODEL_UNKNOWN"));
    }

    @Test
    void puppetLookModelKnown_noFinding() {
        Puppet puppet = Puppet.of(true, null, Puppet.Look.model("NPC_Ghost", null),
                null, null, null);
        List<Finding> findings = StationValidator.validate(List.of(puppetStation("looksmodelknown", puppet, null)),
                ANY_LANG, ANY_DROP, ANY_FACTOR, id -> true, id -> true, ANY_MODEL);
        assertFalse(codes(findings).contains("PUPPET_LOOK_MODEL_UNKNOWN"));
    }

    @Test
    void puppetLookModelUnknown_withFallback_noFinding() {
        Puppet puppet = Puppet.of(true, null,
                Puppet.Look.model("NPC_Ghost", "NPC_Generic_Worker"), null, null, null);
        List<Finding> findings = StationValidator.validate(List.of(puppetStation("looksmodelfallback", puppet, null)),
                ANY_LANG, ANY_DROP, ANY_FACTOR, id -> true, id -> true, NO_MODEL);
        assertFalse(codes(findings).contains("PUPPET_LOOK_MODEL_UNKNOWN"),
                "a FallbackModelId covers an unknown primary ModelId");
    }

    // ==================== NpcRole performer arm (seam wave, decision 47/48, R1 handoff) ====================

    @Test
    void puppetLookNpcRole_isAcceptedSource_noUnknownSourceFinding() {
        Puppet puppet = Puppet.of(true, null,
                Puppet.Look.of(Puppet.LOOK_SOURCE_NPC_ROLE, null,
                        Puppet.Role.of("RPG_Performer_Worker", null, null, null)),
                null, null, null);
        StationAsset a = puppetStation("nporolesource", puppet, null);
        Set<String> codes = codes(validate(a));
        assertFalse(codes.contains("UNKNOWN_PUPPET_LOOK_SOURCE"), "NpcRole is a recognized Look.Source arm");
        assertFalse(codes.contains("PUPPET_LOOK_ROLE_MISSING"), "a non-blank RoleId is authored");
    }

    @Test
    void puppetLookNpcRoleMissingRoleId_flagged() {
        Puppet puppet = Puppet.of(true, null,
                Puppet.Look.of(Puppet.LOOK_SOURCE_NPC_ROLE, null, null), null, null, null);
        StationAsset a = puppetStation("nporolemissing", puppet, null);
        assertTrue(codes(validate(a)).contains("PUPPET_LOOK_ROLE_MISSING"));
    }

    @Test
    void puppetLookNpcRoleUnknownSkinSource_flagged() {
        Puppet puppet = Puppet.of(true, null,
                Puppet.Look.of(Puppet.LOOK_SOURCE_NPC_ROLE, null,
                        Puppet.Role.of("RPG_Performer_Worker", "Bogus", null, null)),
                null, null, null);
        StationAsset a = puppetStation("nporoleskin", puppet, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_PUPPET_ROLE_SKIN_SOURCE"));
    }

    @Test
    void unknownPuppetPropSource_flagged() {
        Puppet puppet = Puppet.of(true, null, null, null, null, Puppet.Prop.of("Wardrobe", null, null));
        StationAsset a = puppetStation("unknownpropsource", puppet, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_PUPPET_PROP_SOURCE"));
    }

    @Test
    void puppetPropItemIdMissing_flagged() {
        Puppet puppet = Puppet.of(true, null, null, null, null, Puppet.Prop.of(Puppet.PROP_SOURCE_ITEM_ID, null, null));
        StationAsset a = puppetStation("propitemmissing", puppet, null);
        assertTrue(codes(validate(a)).contains("PUPPET_PROP_ITEM_ID_MISSING"));
    }

    @Test
    void unknownPuppetPropSlot_flagged() {
        Puppet puppet = Puppet.of(true, null, null, null, null, Puppet.Prop.of(null, null, "OffHand"));
        StationAsset a = puppetStation("unknownpropslot", puppet, null);
        assertTrue(codes(validate(a)).contains("UNKNOWN_PUPPET_PROP_SLOT"));
    }

    @Test
    void puppetSeatMountAdvisory_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationAsset a = puppetStation("puppetseatadvisory", puppet,
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Block", null)));
        assertTrue(codes(validate(a)).contains("PUPPET_SEAT_MOUNT_ADVISORY"));
    }

    @Test
    void puppetEntityMount_noSeatAdvisory() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationAsset a = puppetStation("puppetentitymount", puppet,
                StationAsset.Hold.of(null, null, null, StationAsset.Hold.Mount.of("Entity", null)));
        assertFalse(codes(validate(a)).contains("PUPPET_SEAT_MOUNT_ADVISORY"));
    }

    @Test
    void puppetDisabled_producesNoFindings() {
        Puppet puppet = Puppet.of(false, null, null, null, null, null);
        StationAsset a = puppetStation("puppetdisabled", puppet, null);
        assertTrue(validate(a).isEmpty(),
                "a disabled Puppet with no other leaves authored is clean, got: " + codes(validate(a)));
    }

    // ==================== StationStep.Puppet (per-step override) ====================

    private static StationAsset stepPuppetStation(String id, ActionDef ritualAction) {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", ritualAction);
        StationAsset a = StationAsset.of(id,
                StationAsset.Identity.of("rpgstations.station." + id + ".name", null, null),
                null, null, null, null, null, null, null, null);
        a.withActions(actions);
        return a;
    }

    private static ActionDef ritualActionWithPuppetStep() {
        StationStep step = StationStep.of("strike")
                .withDuration(StationStep.Duration.of(400L))
                .withPuppet(StationStep.PuppetOverride.of("Hammer_Strike", null));
        return actionDef().withSteps(new StationStep[]{step});
    }

    @Test
    void puppetStepOverrideWithoutPuppet_flagged() {
        StationAsset a = stepPuppetStation("nopuppetsteps", ritualActionWithPuppetStep());
        assertTrue(codes(validate(a)).contains("PUPPET_STEP_OVERRIDE_WITHOUT_PUPPET"));
    }

    @Test
    void puppetStepOverride_withActivePuppet_noFinding() {
        Puppet actionPuppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        ActionDef ritual = ritualActionWithPuppetStep().withPuppet(actionPuppet);
        StationAsset a = stepPuppetStation("activepuppetsteps", ritual);
        assertFalse(codes(validate(a)).contains("PUPPET_STEP_OVERRIDE_WITHOUT_PUPPET"));
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

    // ==================== FlairAssets (standalone, unchanged) ====================

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

    // ==================== Actions (design 9.1/9.3, scope-2 Ref/Anchors additions - "never block") ====================

    @Test
    void noActionsMap_neverFlagsActionCodes() {
        // The implicit-single-action path (no Actions authored) must never touch checkActions.
        StationAsset a = StationAsset.of("bare", null, null, oakRecipe(), null, null, null, null, null, null);
        assertFalse(codes(validate(a)).stream().anyMatch(c -> c.startsWith("ACTION")
                || c.contains("STEP") || c.equals("UNREACHABLE_ACTION") || c.equals("AMBIGUOUS_ACTION_INPUT")));
    }

    @Test
    void actionWithNoRecipeOrSteps_flaggedNoBody() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("dead", actionDef());
        assertTrue(codes(validate(stationWithActions(actions))).contains("ACTION_NO_BODY"));
    }

    @Test
    void actionWithRef_notFlaggedNoBody() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("prepfish", actionDef().withRef("prepfish"));
        assertFalse(codes(validate(stationWithActions(actions))).contains("ACTION_NO_BODY"));
    }

    @Test
    void laterCatchAllAction_flaggedUnreachable() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("first", actionDef().withRecipe(oakRecipe()));
        actions.put("second", actionDef().withRecipe(oakRecipe()));
        assertTrue(codes(validate(stationWithActions(actions))).contains("UNREACHABLE_ACTION"));
    }

    @Test
    void duplicateExactItemIdAcrossActions_flaggedAmbiguous() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        ActionInput sameInput = ActionInput.of("Metal_Ingot", null, null, null);
        actions.put("convert1", actionDef().withInput(sameInput).withRecipe(oakRecipe()));
        actions.put("convert2", actionDef().withInput(sameInput).withRecipe(oakRecipe()));
        assertTrue(codes(validate(stationWithActions(actions))).contains("AMBIGUOUS_ACTION_INPUT"));
    }

    @Test
    void duplicateStepId_flagged() {
        StationStep a1 = StationStep.of("dup").withDuration(StationStep.Duration.of(500L));
        StationStep a2 = StationStep.of("dup").withDuration(StationStep.Duration.of(500L));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{a1, a2}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("DUPLICATE_STEP_ID"));
    }

    @Test
    void consumeStepEmpty_flagged() {
        StationStep consume = StationStep.of("c").withConsume(StationStep.Consume.ofOne(null, null, 1, "Inventory"));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{consume}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("CONSUME_STEP_EMPTY"));
    }

    @Test
    void consumeStepFromCustody_withItemId_notFlaggedEmpty() {
        // Custody (design 9.4, phase-2 leg C) is an implemented Consume.From route.
        StationStep consume = StationStep.of("c").withConsume(StationStep.Consume.ofOne("X", null, 1, "Custody"));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{consume}));
        assertFalse(codes(validate(stationWithActions(actions))).contains("CONSUME_STEP_EMPTY"));
    }

    @Test
    void produceStepEmpty_flagged() {
        StationStep produce = StationStep.of("p").withProduce(StationStep.Produce.ofOne(null, 1, "Inventory"));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{produce}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("PRODUCE_STEP_EMPTY"));
    }

    @Test
    void unknownGotoTarget_flagged() {
        StationStep step = StationStep.of("w").withDuration(StationStep.Duration.of(500L))
                .withOnConditionFail(StationStep.OnConditionFail.of(StationStep.OnConditionFail.RESULT_SKIP, "nope"));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("UNKNOWN_GOTO_TARGET"));
    }

    @Test
    void knownGotoTarget_notFlagged() {
        StationStep target = StationStep.of("present").withDuration(StationStep.Duration.of(100L));
        StationStep step = StationStep.of("w").withDuration(StationStep.Duration.of(500L))
                .withOnConditionFail(StationStep.OnConditionFail.of(StationStep.OnConditionFail.RESULT_SKIP, "present"));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step, target}));
        assertFalse(codes(validate(stationWithActions(actions))).contains("UNKNOWN_GOTO_TARGET"));
    }

    // ==================== ACTION_REF_UNKNOWN (scope-2 design 1.5) ====================

    @Test
    void actionRefUnknown_flagged() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("prepfish", actionDef().withRef("prepfish"));
        StationAsset station = stationWithActions(actions);
        assertTrue(codes(validateWithRefs(station, ANY_STATION, NO_ACTION_ASSET)).contains("ACTION_REF_UNKNOWN"));
    }

    @Test
    void actionRefKnown_notFlagged() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("prepfish", actionDef().withRef("prepfish"));
        StationAsset station = stationWithActions(actions);
        assertFalse(codes(validateWithRefs(station, ANY_STATION, ANY_ACTION_ASSET)).contains("ACTION_REF_UNKNOWN"));
    }

    // ==================== ANCHOR_STATION_UNKNOWN (scope-2 design 2.2) ====================

    private static Map<String, ActionDef.Anchor> anchorsOf(String anchorId, String station) {
        Map<String, ActionDef.Anchor> anchors = new LinkedHashMap<>();
        anchors.put(anchorId, ActionDef.Anchor.of(station, 12.0));
        return anchors;
    }

    @Test
    void anchorUnknownStation_flagged() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withRecipe(oakRecipe()).withAnchors(anchorsOf("fire", "ghost_station")));
        StationAsset station = stationWithActions(actions);
        assertTrue(codes(validateWithRefs(station, NO_STATION, ANY_ACTION_ASSET)).contains("ANCHOR_STATION_UNKNOWN"));
    }

    @Test
    void anchorKnownStation_notFlagged() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withRecipe(oakRecipe()).withAnchors(anchorsOf("fire", "cookingfire")));
        StationAsset station = stationWithActions(actions);
        assertFalse(codes(validateWithRefs(station, ANY_STATION, ANY_ACTION_ASSET)).contains("ANCHOR_STATION_UNKNOWN"));
    }

    // ==================== Walk/At + WALK_REQUIRES_PUPPET (design 2.1-2.3, scope-2 wave 3) ====================

    @Test
    void walkWithoutPuppet_flaggedRequiresPuppet() {
        StationStep step = StationStep.of("go").withWalk(StationStep.Walk.of("fire", null));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}).withAnchors(anchorsOf("fire", "cookingfire")));
        Set<String> codes = codes(validate(stationWithActions(actions)));
        assertTrue(codes.contains("WALK_REQUIRES_PUPPET"));
    }

    @Test
    void walkWithActivePuppet_notFlaggedRequiresPuppet() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationStep step = StationStep.of("go").withWalk(StationStep.Walk.of("fire", null));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step})
                .withAnchors(anchorsOf("fire", "cookingfire")).withPuppet(puppet));
        assertFalse(codes(validate(stationWithActions(actions))).contains("WALK_REQUIRES_PUPPET"));
    }

    @Test
    void walkTargetUnknownAnchor_flagged() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationStep step = StationStep.of("go").withWalk(StationStep.Walk.of("ghost", null));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}).withPuppet(puppet));
        assertTrue(codes(validate(stationWithActions(actions))).contains("WALK_TARGET_UNKNOWN_ANCHOR"));
    }

    @Test
    void walkTargetSelf_notFlaggedUnknownAnchor() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationStep step = StationStep.of("go").withWalk(StationStep.Walk.of("self", null));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}).withPuppet(puppet));
        assertFalse(codes(validate(stationWithActions(actions))).contains("WALK_TARGET_UNKNOWN_ANCHOR"));
    }

    @Test
    void walkTargetDeclaredAnchor_notFlaggedUnknownAnchor() {
        Puppet puppet = Puppet.of(true, Puppet.Hide.of(Puppet.HIDE_ROUTE_SCALE, null), null, null, null, null);
        StationStep step = StationStep.of("go").withWalk(StationStep.Walk.of("fire", null));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step})
                .withAnchors(anchorsOf("fire", "cookingfire")).withPuppet(puppet));
        assertFalse(codes(validate(stationWithActions(actions))).contains("WALK_TARGET_UNKNOWN_ANCHOR"));
    }

    @Test
    void stepAtUnknownAnchor_flagged() {
        StationStep step = StationStep.of("cook").withAt("ghost").withDuration(StationStep.Duration.of(2000L));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("STEP_AT_UNKNOWN_ANCHOR"));
    }

    @Test
    void stepAtDeclaredAnchor_notFlagged() {
        StationStep step = StationStep.of("cook").withAt("fire").withDuration(StationStep.Duration.of(2000L));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}).withAnchors(anchorsOf("fire", "cookingfire")));
        assertFalse(codes(validate(stationWithActions(actions))).contains("STEP_AT_UNKNOWN_ANCHOR"));
    }

    @Test
    void grantsContributions_onCompletionTrigger_flagged() {
        // A one-shot contribution rides the cycle-completed event, so a Completion-trigger roll
        // authoring one warns exactly the way BonusOutputCopies already does.
        StationAsset a = StationAsset.of("badposttrigger",
                StationAsset.Identity.of("rpgstations.station.badposttrigger.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Completion", null, null, null, Roll.Grants.of(null, null, null, null,
                        new Contribution[]{Contribution.of("yourmod:test", "ALPHA", 10.0)}))));
        assertTrue(codes(validate(a)).contains("LOOT_CONTRIBUTION_WRONG_TRIGGER"));
    }

    @Test
    void grantsContributions_onCycleTrigger_notFlagged() {
        StationAsset a = StationAsset.of("goodposttrigger",
                StationAsset.Identity.of("rpgstations.station.goodposttrigger.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Cycle", null, null, null, Roll.Grants.of(null, null, null, null,
                        new Contribution[]{Contribution.of("yourmod:test", "ALPHA", 10.0)}))));
        assertFalse(codes(validate(a)).contains("LOOT_CONTRIBUTION_WRONG_TRIGGER"));
    }

    @Test
    void grantsContributions_blankChannel_flagged() {
        StationAsset a = StationAsset.of("blankpostchannel",
                StationAsset.Identity.of("rpgstations.station.blankpostchannel.name", null, null),
                null, oakRecipe(),
                null, null, null, null, null, null,
                loot(Roll.of("Cycle", null, null, null, Roll.Grants.of(null, null, null, null,
                        new Contribution[]{Contribution.of("  ", "ALPHA", 10.0)}))));
        assertTrue(codes(validate(a)).contains("LOOT_CONTRIBUTION_MISSING_CHANNEL"));
    }

    @Test
    void produceToCustody_notFlaggedWave3Pending() {
        // Scope-2 wave 3: Produce.To:Custody EXECUTES now, so the temporary WAVE3_PENDING warn is gone.
        StationStep step = StationStep.of("deposit").withProduce(StationStep.Produce.ofOne("Food_Fish_Grilled", 1, "Custody"));
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("ritual", actionDef().withSteps(new StationStep[]{step}));
        assertFalse(codes(validate(stationWithActions(actions))).contains("WAVE3_PENDING"));
    }

    // ==================== Stamp (design 9.5, scope-2 Caps.Budgets[] reshape) ====================

    private static StationStep stampStep(StationStep.Stamp.Stats.Budget... budgets) {
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(budgets, null, null);
        StationStep.Stamp.Stats stats = StationStep.Stamp.Stats.of(null,
                new StatRollEntry[]{StatRollEntry.of("MMO_CritChance", StatRollEntry.Points.of(1.0, 2.0), 1.0, null)},
                null, null, caps);
        StationStep.Stamp stamp = StationStep.Stamp.of(new Ingredient[]{Ingredient.item("Metal_Bars", 1)}, null, stats);
        return StationStep.of("stamp").withStamp(stamp);
    }

    @Test
    void stampBudgetBadRoute_flagged() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("enhance", actionDef().withSteps(new StationStep[]{
                stampStep(StationStep.Stamp.Stats.Budget.flat(null))}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("STAMP_BUDGET_BAD_ROUTE"));
    }

    @Test
    void stampBudgetNonPositive_flagged() {
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("enhance", actionDef().withSteps(new StationStep[]{
                stampStep(StationStep.Stamp.Stats.Budget.flat(0.0))}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("STAMP_NONPOSITIVE_BUDGET"));
    }

    @Test
    void stampBudgetFactorScaledUnknownFactor_flagged() {
        StationStep.Stamp.Stats.Budget scaled = StationStep.Stamp.Stats.Budget.scaled(0.5,
                new FactorRef[]{FactorRef.stat("MMO_Level_SMITHING")});
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("enhance", actionDef().withSteps(new StationStep[]{stampStep(scaled)}));
        assertTrue(codes(StationValidator.validate(List.of(stationWithActions(actions)), ANY_LANG, ANY_DROP, NO_FACTOR))
                .contains("UNKNOWN_FACTOR"));
    }

    @Test
    void stampBudgetValid_producesNoBudgetFindings() {
        StationStep.Stamp.Stats.Budget budget = StationStep.Stamp.Stats.Budget.flat(30.0);
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("enhance", actionDef().withSteps(new StationStep[]{stampStep(budget)}));
        Set<String> codes = codes(validate(stationWithActions(actions)));
        assertFalse(codes.contains("STAMP_BUDGET_BAD_ROUTE"));
        assertFalse(codes.contains("STAMP_NONPOSITIVE_BUDGET"));
        assertFalse(codes.contains("STAMP_NO_REAGENTS"));
        assertFalse(codes.contains("STAMP_STATS_NO_ENTRIES"));
    }

    @Test
    void stampNoReagents_flagged() {
        StationStep.Stamp stamp = StationStep.Stamp.of(null, StationStep.Stamp.Durability.of(5.0), null);
        StationStep step = StationStep.of("stamp").withStamp(stamp);
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("enhance", actionDef().withSteps(new StationStep[]{step}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("STAMP_NO_REAGENTS"));
    }

    @Test
    void stampNoPayload_flagged() {
        StationStep.Stamp stamp = StationStep.Stamp.of(new Ingredient[]{Ingredient.item("Metal_Bars", 1)}, null, null);
        StationStep step = StationStep.of("stamp").withStamp(stamp);
        Map<String, ActionDef> actions = new LinkedHashMap<>();
        actions.put("enhance", actionDef().withSteps(new StationStep[]{step}));
        assertTrue(codes(validate(stationWithActions(actions))).contains("STAMP_NO_PAYLOAD"));
    }

    // ==================== Custody (design section 9.4, unchanged shape) ====================

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

    // ==================== Custody.Display ====================

    @Test
    void custodyDisplayNonPositiveScale_flagged() {
        StationAsset a = validStation().withCustody(
                Custody.of(100, null, null, Custody.Display.of(null, 0.0, null)));
        assertTrue(codes(validate(a)).contains("CUSTODY_DISPLAY_NON_POSITIVE_SCALE"));
    }

    @Test
    void custodyDisplayPositiveScale_notFlagged() {
        StationAsset a = validStation().withCustody(
                Custody.of(100, null, null, Custody.Display.of(null, 1.5, null)));
        assertFalse(codes(validate(a)).contains("CUSTODY_DISPLAY_NON_POSITIVE_SCALE"));
    }

    @Test
    void noDisplay_neverFlagsDisplayFindings() {
        assertFalse(codes(validate(validStation())).contains("CUSTODY_DISPLAY_NON_POSITIVE_SCALE"));
    }

    // ==================== validateActionAssets (standalone ActionAsset, scope-2 design 1.5) ====================

    @Test
    void validateActionAssets_noBody_flaggedActionNoBody() throws Exception {
        ActionAsset action = actionAsset("empty", "{}");
        List<Finding> findings = StationValidator.validateActionAssets(List.of(action), ANY_DROP, ANY_FACTOR,
                ANY_LOOTABLE, ANY_ROLLPOOL, ANY_MODEL, ANY_STATION);
        assertTrue(codes(findings).contains("ACTION_NO_BODY"));
    }

    @Test
    void validateActionAssets_withSteps_notFlaggedActionNoBody() throws Exception {
        ActionAsset action = actionAsset("prepfish", "{ \"Steps\": [ { \"Id\": \"scale\" } ] }");
        List<Finding> findings = StationValidator.validateActionAssets(List.of(action), ANY_DROP, ANY_FACTOR,
                ANY_LOOTABLE, ANY_ROLLPOOL, ANY_MODEL, ANY_STATION);
        assertFalse(codes(findings).contains("ACTION_NO_BODY"));
    }

    @Test
    void validateActionAssets_unknownAnchorStation_flagged() throws Exception {
        ActionAsset action = actionAsset("prepfish",
                "{ \"Anchors\": { \"fire\": { \"Station\": \"ghost\" } }, \"Steps\": [ { \"Id\": \"s\" } ] }");
        List<Finding> findings = StationValidator.validateActionAssets(List.of(action), ANY_DROP, ANY_FACTOR,
                ANY_LOOTABLE, ANY_ROLLPOOL, ANY_MODEL, NO_STATION);
        assertTrue(codes(findings).contains("ANCHOR_STATION_UNKNOWN"));
    }

    @Test
    void validateActionAssets_knownAnchorStation_notFlagged() throws Exception {
        ActionAsset action = actionAsset("prepfish",
                "{ \"Anchors\": { \"fire\": { \"Station\": \"cookingfire\" } }, \"Steps\": [ { \"Id\": \"s\" } ] }");
        List<Finding> findings = StationValidator.validateActionAssets(List.of(action), ANY_DROP, ANY_FACTOR,
                ANY_LOOTABLE, ANY_ROLLPOOL, ANY_MODEL, ANY_STATION);
        assertFalse(codes(findings).contains("ANCHOR_STATION_UNKNOWN"));
    }

    @Test
    void liveValidate_validatesStandaloneActions_theWiring() throws Exception {
        // Review minor (validator-standalone-action-unwired): the singleton validate() now folds the
        // standalone ActionAsset store through validateActionAssets, so a broken standalone action
        // (the flagship prepfish shape) actually gets its anchor checks at load. Inject a broken
        // action (anchor -> unknown station) into the LIVE ActionCatalog and confirm the singleton
        // full pass surfaces ANCHOR_STATION_UNKNOWN (an empty unit-JVM StationCatalog knows no
        // station, so the unknown-anchor-station check fires). Cleared in finally so the
        // process-wide singleton is not left polluted for other tests.
        ActionAsset broken = actionAsset("brokenwiredaction",
                "{ \"Anchors\": { \"fire\": { \"Station\": \"ghoststation\" } }, \"Steps\": [ { \"Id\": \"s\" } ] }");
        try {
            ActionCatalog.getInstance().fold(Map.of("brokenwiredaction", broken), true);
            Set<String> codes = codes(StationValidator.validate());
            assertTrue(codes.contains("ANCHOR_STATION_UNKNOWN"),
                    "the live validate() pass validates standalone actions, got: " + codes);
        } finally {
            ActionCatalog.getInstance().fold(Map.of(), true);
        }
    }

    // ==================== validateExtensions (scope-2 design 1.8/1.9) ====================

    @Test
    void extensionTargetAmbiguous_flagged() throws Exception {
        ExtensionAsset ext = extensionAsset("badtarget", "{ \"Target\": {} }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void extensionTargetUnknownStation_flagged() throws Exception {
        ExtensionAsset ext = extensionAsset("ghosttarget", "{ \"Target\": { \"Station\": \"ghost_station\" } }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void extensionTargetKnownStation_notFlagged() throws Exception {
        ExtensionAsset ext = extensionAsset("sawmillext", "{ \"Target\": { \"Station\": \"sawmill\" } }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(validStation()), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_TARGET_UNKNOWN"));
    }

    @Test
    void extensionPayloadMismatch_flagged() throws Exception {
        // A Lootable target can only carry Rolls[] - PerCycleContributions is not legal for it.
        ExtensionAsset ext = extensionAsset("badpayload", "{ \"Target\": { \"Lootable\": \"sawmillfinds\" },"
                + " \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\", \"Amount\": 5 } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_PAYLOAD_MISMATCH"));
    }

    @Test
    void extensionPayloadAllowed_notFlagged() throws Exception {
        ExtensionAsset ext = extensionAsset("goodpayload", "{ \"Target\": { \"Lootable\": \"sawmillfinds\" },"
                + " \"Rolls\": [ { \"Trigger\": \"Cycle\", \"Grants\": { \"DropList\": \"T1\" } } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_PAYLOAD_MISMATCH"));
    }

    @Test
    void extensionActionKeyCollidesWithBase_flagged() throws Exception {
        Map<String, ActionDef> baseActions = new LinkedHashMap<>();
        baseActions.put("convert", actionDef().withRecipe(oakRecipe()));
        StationAsset station = stationWithActions(baseActions);
        ExtensionAsset ext = extensionAsset("collideext", "{ \"Target\": { \"Station\": \"multiaction\" },"
                + " \"Actions\": { \"convert\": { \"Work\": { \"CycleMs\": 1000 } } } }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(station), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_KEY_COLLISION"));
    }

    @Test
    void extensionActionNewKey_notFlaggedCollision() throws Exception {
        Map<String, ActionDef> baseActions = new LinkedHashMap<>();
        baseActions.put("convert", actionDef().withRecipe(oakRecipe()));
        StationAsset station = stationWithActions(baseActions);
        ExtensionAsset ext = extensionAsset("addsomething", "{ \"Target\": { \"Station\": \"multiaction\" },"
                + " \"Actions\": { \"extra\": { \"Work\": { \"CycleMs\": 1000 } } } }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(station), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_KEY_COLLISION"));
    }

    @Test
    void extensionActionKeyCollidesWithAnotherExtension_loserFlagged() throws Exception {
        Map<String, ActionDef> baseActions = new LinkedHashMap<>();
        baseActions.put("convert", actionDef().withRecipe(oakRecipe()));
        StationAsset station = stationWithActions(baseActions);
        ExtensionAsset first = extensionAsset("first_ext", "{ \"Target\": { \"Station\": \"multiaction\" },"
                + " \"Priority\": 0, \"Actions\": { \"extra\": { \"Work\": { \"CycleMs\": 1000 } } } }");
        ExtensionAsset second = extensionAsset("second_ext", "{ \"Target\": { \"Station\": \"multiaction\" },"
                + " \"Priority\": 5, \"Actions\": { \"extra\": { \"Work\": { \"CycleMs\": 2000 } } } }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(first, second), List.of(station), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_KEY_COLLISION"));
    }

    @Test
    void extensionStepInsertion_ambiguousAnchor_flagged() throws Exception {
        ActionAsset action = actionAsset("prepfish", "{ \"Steps\": [ { \"Id\": \"scale\" } ] }");
        ExtensionAsset ext = extensionAsset("noanchor", "{ \"Target\": { \"Action\": \"prepfish\" },"
                + " \"Steps\": [ { \"Insert\": [ { \"Id\": \"extra\" } ] } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(action),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_ANCHOR_MISSING"));
    }

    @Test
    void extensionStepInsertion_danglingAfterStepId_flagged() throws Exception {
        ActionAsset action = actionAsset("prepfish", "{ \"Steps\": [ { \"Id\": \"scale\" } ] }");
        ExtensionAsset ext = extensionAsset("dangling", "{ \"Target\": { \"Action\": \"prepfish\" },"
                + " \"Steps\": [ { \"Anchor\": { \"After\": \"ghost_step\" }, \"Insert\": [ { \"Id\": \"extra\" } ] } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(action),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_ANCHOR_MISSING"));
    }

    @Test
    void extensionStepInsertion_knownAfterStepId_notFlaggedAnchorMissing() throws Exception {
        ActionAsset action = actionAsset("prepfish", "{ \"Steps\": [ { \"Id\": \"scale\" } ] }");
        ExtensionAsset ext = extensionAsset("valid", "{ \"Target\": { \"Action\": \"prepfish\" },"
                + " \"Steps\": [ { \"Anchor\": { \"After\": \"scale\" }, \"Insert\": [ { \"Id\": \"extra\" } ] } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(action),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_ANCHOR_MISSING"));
    }

    @Test
    void extensionStepInsertion_missingId_flagged() throws Exception {
        ExtensionAsset ext = extensionAsset("noid", "{ \"Target\": { \"Action\": \"prepfish\" },"
                + " \"Steps\": [ { \"Anchor\": { \"AtEnd\": true }, \"Insert\": [ { \"Commands\": [\"say hi\"] } ] } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertTrue(codes(findings).contains("EXTENSION_STEP_MISSING_ID"));
    }

    @Test
    void extensionStepInsertion_withId_notFlaggedMissingId() throws Exception {
        ExtensionAsset ext = extensionAsset("hasid", "{ \"Target\": { \"Action\": \"prepfish\" },"
                + " \"Steps\": [ { \"Anchor\": { \"AtEnd\": true }, \"Insert\": [ { \"Id\": \"extra\" } ] } ] }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(ext), List.of(), List.of(),
                ANY_DROP, ANY_FACTOR, ANY_LOOTABLE, ANY_ROLLPOOL);
        assertFalse(codes(findings).contains("EXTENSION_STEP_MISSING_ID"));
    }
}
