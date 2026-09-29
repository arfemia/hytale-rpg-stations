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
import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.validation.Finding;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.Pace;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The validator's coverage of the beat-level knobs, the pace, the ritual queue, the fallback
 * routes and the shared matcher's {@code Except} hole. Every finding is warn-or-info: none blocks
 * a load. Fixtures are authored by this test.
 */
public class StationValidatorConvertPaceTest {

    private static final Predicate<String> ANY = id -> true;

    private static Set<String> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).collect(Collectors.toSet());
    }

    private static Set<String> validate(StationAsset a) {
        return codes(StationValidator.validate(List.of(a), ANY, ANY, ANY));
    }

    private static StationAsset station(String id, ActionDef... actions) {
        return StationAsset.of(id,
                StationAsset.Identity.of("rpgstations.station." + id + ".name",
                        "rpgstations.station." + id + ".desc", "Fixture_Icon"),
                actions);
    }

    private static StationAsset.Recipe recipe() {
        return StationAsset.Recipe.of(new StationAsset.Conversion[] {
                StationAsset.Conversion.of(Ingredient.item("Fixture_Sword", 1), Ingredient.item("Fixture_Bar", 2))});
    }

    private static StationStep beat(String id) {
        return StationStep.of(id).withDuration(StationStep.Duration.of(1000L)).withIsWork(true);
    }

    private static Pace pace() {
        return Pace.of(ContributionScale.of(
                new FactorFormula.Term[] {FactorFormula.Term.of("fixture:proficiency", null, 1.0)},
                new ContributionScale.Floor[] {ContributionScale.Floor.of(10.0, 0.5)}),
                FactorFormula.Clamp.of(0.4, 1.0));
    }

    private static Custody twoSingleSockets() throws Exception {
        String body = "{ \"MaxQuantity\": 3, \"Sockets\": { \"slot_a\": { \"Item\": {}, \"MaxQuantity\": 1 },"
                + " \"slot_b\": { \"Item\": {}, \"MaxQuantity\": 1 } } }";
        return Custody.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static ExtensionAsset extension(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, id, null);
        return ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    /** A clean ritual: a recipe, two single-item sockets, a paced program with a Convert and a RollBonus beat, a Bonus and a Pace. */
    private static ActionDef cleanRitual() throws Exception {
        return ActionDef.of("Unmake")
                .withRecipe(recipe())
                .withCustody(twoSingleSockets())
                .withWork(StationAsset.Work.of(5000L, 600000L, 1.5, null).withLooping(false).withQueue(true))
                .withBonus(LootRef.of(null, new Roll[] {
                        Roll.of("Cycle", null, null, null, LootGrants.ofDropList("Fixture_Drops"), null)}))
                .withPace(pace())
                .withSteps(new StationStep[] {
                        beat("Open"),
                        beat("Kindle").withPaced(true),
                        beat("Unmake").withConvert(StationStep.Convert.on()),
                        beat("Payout").withRollBonus(true)});
    }

    @Test
    void aCleanRitual_raisesNoneOfTheNewFindings() throws Exception {
        Set<String> codes = validate(station("fixturetable", cleanRitual()));
        for (String code : List.of("CONVERT_WITHOUT_RECIPE", "CONVERT_WITH_CONSUME_PRODUCE", "CONVERT_REPEATED",
                "BONUS_AT_BEAT_NO_BONUS", "BONUS_AT_BEAT_REPEATED", "PACED_STEP_WITHOUT_PACE",
                "PACE_WITHOUT_STEPS", "PACE_NO_PACED_STEP", "PACE_UNCLAMPED", "PACE_CLAMP_INVERTED",
                "PACE_FLOOR_NONPOSITIVE",
                "QUEUE_WITHOUT_STEPS", "QUEUE_WITHOUT_SOCKETS", "QUEUE_SOCKET_NOT_SINGLE", "QUEUE_WITH_LOOPING",
                "LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT")) {
            assertFalse(codes.contains(code), code + " must not fire on a clean ritual, got " + codes);
        }
    }

    @Test
    void presentationTargets_needWhatTheyPointAt() throws Exception {
        // A Display target on an action whose sockets show no prop plays at the block; a Puppet
        // target with no active puppet plays on the worker's own body; a Node at the block means
        // nothing; an unknown kind reads as Block; a bad Color is ignored.
        ActionDef a = ActionDef.of("Unmake")
                .withRecipe(recipe())
                .withCustody(twoSingleSockets())
                .withSteps(new StationStep[] {
                        beat("Open").withPresentation(Presentation.of(Presentation.Target.of("Display"), null,
                                new Presentation.ModelParticle[] {Presentation.ModelParticle.of("Fixture_Burst", null,
                                        2.0, null, null, "not-a-hex")}, null, null, null, null)),
                        beat("Kindle").withPresentation(Presentation.of(Presentation.Target.of("Puppet", "Hand_R"),
                                null, null, null, null, EffectRef.of("Fixture_Aura", 500L, "Puppet"), null)),
                        beat("Draw").withPresentation(Presentation.of(Presentation.Target.of("Block", "Hand_R"),
                                null, null, null, null, EffectRef.of("Fixture_Sting", null, "Camera"), null)),
                        beat("Surge").withPresentation(Presentation.of(Presentation.Target.of("Ceiling"),
                                null, null, null, null, null, null))});
        Set<String> codes = validate(station("fixture_targets", a));
        assertTrue(codes.contains("PRESENTATION_DISPLAY_TARGET_NO_DISPLAY"), codes.toString());
        assertTrue(codes.contains("PRESENTATION_PARTICLE_BAD_COLOR"), codes.toString());
        assertTrue(codes.contains("PRESENTATION_ENTITY_TARGET_CAP_IGNORED"), codes.toString());
        assertTrue(codes.contains("PRESENTATION_PUPPET_TARGET_NO_PUPPET"), codes.toString());
        assertTrue(codes.contains("EFFECT_PUPPET_TARGET_NO_PUPPET"), codes.toString());
        assertTrue(codes.contains("PRESENTATION_TARGET_NODE_AT_BLOCK"), codes.toString());
        assertTrue(codes.contains("EFFECT_TARGET_UNKNOWN"), codes.toString());
        assertTrue(codes.contains("PRESENTATION_TARGET_UNKNOWN_KIND"), codes.toString());
    }

    @Test
    void aDisplayTarget_isQuietWhenASocketShowsAProp() throws Exception {
        String body = "{ \"MaxQuantity\": 1, \"Sockets\": { \"slot_a\": { \"Item\": {}, \"MaxQuantity\": 1,"
                + " \"Display\": { \"Offset\": { \"Y\": 0.5 } } } } }";
        Custody withProp = Custody.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
        ActionDef a = ActionDef.of("Unmake").withRecipe(recipe()).withCustody(withProp)
                .withSteps(new StationStep[] {
                        beat("Draw").withPresentation(Presentation.of(Presentation.Target.of("Display"), null,
                                null, null, null, null, null)).withDisplay(Custody.Display.of(null, null, null, true))});
        Set<String> codes = validate(station("fixture_prop", a));
        assertFalse(codes.contains("PRESENTATION_DISPLAY_TARGET_NO_DISPLAY"), codes.toString());
        assertFalse(codes.contains("STEP_DISPLAY_WITHOUT_DISPLAY"), codes.toString());
    }

    @Test
    void aStepsState_needsStatesAndAWorkBeat_andAStepsDisplayNeedsAProp() throws Exception {
        ActionDef a = ActionDef.of("Unmake")
                .withRecipe(recipe())
                .withCustody(twoSingleSockets())
                .withSteps(new StationStep[] {
                        beat("Draw").withState("Drawing").withDisplay(Custody.Display.of(null, null, null, true))});
        Set<String> codes = validate(station("fixture_state", a));
        assertTrue(codes.contains("STEP_STATE_WITHOUT_STATES"), codes.toString());
        assertTrue(codes.contains("STEP_DISPLAY_WITHOUT_DISPLAY"), codes.toString());

        Custody withStates = Custody.of(1, null, null, Custody.States.of("Default", "Loaded", "Working"), null);
        ActionDef b = ActionDef.of("Unmake").withRecipe(recipe()).withCustody(withStates)
                .withSteps(new StationStep[] {
                        StationStep.of("Settle").withDuration(StationStep.Duration.of(1000L)).withState("Drawing"),
                        beat("Draw").withState("Drawing")});
        Set<String> codesB = validate(station("fixture_state_b", b));
        assertFalse(codesB.contains("STEP_STATE_WITHOUT_STATES"), codesB.toString());
        assertTrue(codesB.contains("STEP_STATE_NOT_WORK"), codesB.toString());
    }

    @Test
    void aPreviewNeedsARecipe() {
        Custody preview = Custody.of(1, null, null, true, null, null, null, null, null);
        Set<String> codes = validate(station("fixture_preview", ActionDef.of("Look").withCustody(preview)
                .withSteps(new StationStep[] {beat("Open")})));
        assertTrue(codes.contains("CUSTODY_PREVIEW_WITHOUT_RECIPE"), codes.toString());
        Set<String> quiet = validate(station("fixture_preview_ok", ActionDef.of("Unmake").withRecipe(recipe())
                .withCustody(preview).withSteps(new StationStep[] {beat("Unmake").withConvert(StationStep.Convert.on())})));
        assertFalse(quiet.contains("CUSTODY_PREVIEW_WITHOUT_RECIPE"), quiet.toString());
    }

    @Test
    void anExtensionsCustodyOverlay_getsTheSameExceptChecksTheActionGets() throws Exception {
        ExtensionAsset hollow = extension("fixture_hollow", "{ \"Target\": { \"Action\": \"Unmake\" },"
                + " \"Custody\": { \"Input\": { \"Except\": { } },"
                + " \"Sockets\": { \"slot_a\": { \"Item\": { \"Match\": { \"Except\": { } } } } } } }");
        List<Finding> findings = StationValidator.validateExtensions(List.of(hollow), List.of(), List.of(), ANY, ANY, ANY, ANY);
        Set<String> codes = codes(findings);
        assertEquals(2L, findings.stream()
                .filter(f -> "EXCEPT_CATCH_ALL".equals(f.code())).count(),
                "the overlay's Input hole and its socket's Match hole are each checked: " + codes);
    }

    @Test
    void convertWithoutRecipe_andBesideConsumeProduce_andRepeated() {
        ActionDef noRecipe = ActionDef.of("Unmake").withSteps(new StationStep[] {
                beat("A").withConvert(StationStep.Convert.on()),
                beat("B").withConvert(StationStep.Convert.on())
                        .withConsume(StationStep.Consume.ofOne("Fixture_Salt", null, 1, "Inventory"))});
        Set<String> codes = validate(station("fixturetable", noRecipe));
        assertTrue(codes.contains("CONVERT_WITHOUT_RECIPE"), codes.toString());
        assertTrue(codes.contains("CONVERT_WITH_CONSUME_PRODUCE"), codes.toString());
        assertTrue(codes.contains("CONVERT_REPEATED"), codes.toString());
    }

    @Test
    void aConvertBeat_givesAStepsProgramItsCycleOutput_soOutputItemsIsNoLongerDropped() {
        Roll outputItems = Roll.of("Cycle", null, null, null,
                LootGrants.of(null, null, null, new LootGrants.Reward[] {
                        LootGrants.Reward.of("rpgstations:output_items", Map.of("Count", "1"))}),
                null);
        ActionDef converting = ActionDef.of("Unmake").withRecipe(recipe())
                .withBonus(LootRef.of(null, new Roll[] {outputItems}))
                .withSteps(new StationStep[] {beat("A").withConvert(StationStep.Convert.on())});
        assertFalse(validate(station("fixturetable", converting)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"));
        ActionDef notConverting = ActionDef.of("Unmake").withRecipe(recipe())
                .withBonus(LootRef.of(null, new Roll[] {outputItems}))
                .withSteps(new StationStep[] {beat("A")});
        assertTrue(validate(station("fixturetable", notConverting)).contains("LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT"));
    }

    @Test
    void rollBonus_withoutABonus_andRepeated() {
        ActionDef def = ActionDef.of("Unmake").withRecipe(recipe()).withSteps(new StationStep[] {
                beat("A").withRollBonus(true), beat("B").withRollBonus(true)});
        Set<String> codes = validate(station("fixturetable", def));
        assertTrue(codes.contains("BONUS_AT_BEAT_NO_BONUS"), codes.toString());
        assertTrue(codes.contains("BONUS_AT_BEAT_REPEATED"), codes.toString());
    }

    @Test
    void pace_needsAProgramWithAPacedBeat_andSoundBounds() {
        ActionDef noSteps = ActionDef.of("Mill").withRecipe(recipe()).withPace(pace());
        assertTrue(validate(station("fixturemill", noSteps)).contains("PACE_WITHOUT_STEPS"));
        ActionDef noPacedBeat = ActionDef.of("Unmake").withRecipe(recipe()).withPace(pace())
                .withSteps(new StationStep[] {beat("A").withConvert(StationStep.Convert.on())});
        assertTrue(validate(station("fixturetable", noPacedBeat)).contains("PACE_NO_PACED_STEP"));
        ActionDef inverted = ActionDef.of("Unmake").withRecipe(recipe())
                .withPace(Pace.of(ContributionScale.of(
                        new FactorFormula.Term[] {FactorFormula.Term.of("fixture:p", null, 1.0)},
                        new ContributionScale.Floor[] {ContributionScale.Floor.of(5.0, 0.0)}),
                        FactorFormula.Clamp.of(1.0, 0.5)))
                .withSteps(new StationStep[] {beat("A").withPaced(true).withConvert(StationStep.Convert.on())});
        Set<String> codes = validate(station("fixturetable", inverted));
        assertTrue(codes.contains("PACE_CLAMP_INVERTED"), codes.toString());
        assertTrue(codes.contains("PACE_FLOOR_NONPOSITIVE"), codes.toString());
        ActionDef pacedNoPace = ActionDef.of("Unmake").withRecipe(recipe())
                .withSteps(new StationStep[] {beat("A").withPaced(true).withConvert(StationStep.Convert.on())});
        assertTrue(validate(station("fixturetable", pacedNoPace)).contains("PACED_STEP_WITHOUT_PACE"));
    }

    @Test
    void anActionsPace_mustClampBothSides_sinceItsClampIsTheOneBoundOnTheComposedPace() {
        StationStep[] paced = {beat("A").withPaced(true).withConvert(StationStep.Convert.on())};
        ContributionScale ladder = ContributionScale.of(
                new FactorFormula.Term[] {FactorFormula.Term.of("fixture:p", null, 1.0)},
                new ContributionScale.Floor[] {ContributionScale.Floor.of(10.0, 0.5)});
        ActionDef noClamp = ActionDef.of("Unmake").withRecipe(recipe()).withSteps(paced)
                .withPace(Pace.of(ladder, null));
        assertTrue(validate(station("fixturetable", noClamp)).contains("PACE_UNCLAMPED"), "no Clamp at all");
        ActionDef minOnly = ActionDef.of("Unmake").withRecipe(recipe()).withSteps(paced)
                .withPace(Pace.of(ladder, FactorFormula.Clamp.of(0.4, null)));
        assertTrue(validate(station("fixturetable", minOnly)).contains("PACE_UNCLAMPED"), "a Clamp missing its Max");
        ActionDef maxOnly = ActionDef.of("Unmake").withRecipe(recipe()).withSteps(paced)
                .withPace(Pace.of(ladder, FactorFormula.Clamp.of(null, 1.0)));
        assertTrue(validate(station("fixturetable", maxOnly)).contains("PACE_UNCLAMPED"), "a Clamp missing its Min");
        ActionDef bothSides = ActionDef.of("Unmake").withRecipe(recipe()).withSteps(paced).withPace(pace());
        assertFalse(validate(station("fixturetable", bothSides)).contains("PACE_UNCLAMPED"));
        assertTrue(pace().isFullyClamped());
        assertFalse(Pace.of(ladder, FactorFormula.Clamp.of(0.4, null)).isFullyClamped());
    }

    @Test
    void queue_needsAProgram_twoSingleItemSockets_andNotesLooping() throws Exception {
        ActionDef noSteps = ActionDef.of("Mill").withRecipe(recipe()).withCustody(twoSingleSockets())
                .withWork(StationAsset.Work.of(null, null, null, null).withQueue(true));
        Set<String> codes = validate(station("fixturemill", noSteps));
        assertTrue(codes.contains("QUEUE_WITHOUT_STEPS"), codes.toString());
        assertTrue(codes.contains("QUEUE_WITH_LOOPING"), codes.toString());
        ActionDef oneSocket = ActionDef.of("Unmake").withRecipe(recipe())
                .withCustody(Custody.of(5, null, null))
                .withWork(StationAsset.Work.of(null, null, null, null).withLooping(false).withQueue(true))
                .withSteps(new StationStep[] {beat("A").withConvert(StationStep.Convert.on())});
        codes = validate(station("fixturetable", oneSocket));
        assertTrue(codes.contains("QUEUE_WITHOUT_SOCKETS"), codes.toString());
        assertTrue(codes.contains("QUEUE_SOCKET_NOT_SINGLE"), codes.toString());
        ActionDef noCustody = ActionDef.of("Unmake").withRecipe(recipe())
                .withWork(StationAsset.Work.of(null, null, null, null).withLooping(false).withQueue(true))
                .withSteps(new StationStep[] {beat("A").withConvert(StationStep.Convert.on())});
        assertTrue(validate(station("fixturetable", noCustody)).contains("QUEUE_WITHOUT_SOCKETS"));
    }

    @Test
    void fallback_needsCustody_aRoute_aSaneShare_andNotesACatchAllFilter() {
        StationAsset.Fallback empty = StationAsset.Fallback.of(null, null, null);
        ActionDef noCustody = ActionDef.of("Unmake").withRecipe(recipe().withFallback(empty));
        Set<String> codes = validate(station("fixturetable", noCustody));
        assertTrue(codes.contains("FALLBACK_WITHOUT_CUSTODY"), codes.toString());
        assertTrue(codes.contains("FALLBACK_NO_ROUTE"), codes.toString());
        assertTrue(codes.contains("FALLBACK_INPUT_CATCH_ALL"), codes.toString());
        StationAsset.Fallback tooGenerous = StationAsset.Fallback.of(
                ActionInput.of(null, null, null, "Weapon", ActionInput.of(null, null, null, null)),
                StationAsset.Fallback.CraftingShare.of(1.5), StationAsset.Fallback.EssenceOnly.of(null));
        ActionDef withCustody = ActionDef.of("Unmake").withRecipe(recipe().withFallback(tooGenerous))
                .withCustody(Custody.of(1, null, null));
        codes = validate(station("fixturetable", withCustody));
        assertTrue(codes.contains("FALLBACK_SHARE_OUT_OF_RANGE"), codes.toString());
        assertTrue(codes.contains("EXCEPT_CATCH_ALL"), codes.toString());
        assertFalse(codes.contains("FALLBACK_WITHOUT_CUSTODY"), codes.toString());
        assertFalse(codes.contains("FALLBACK_NO_ROUTE"), codes.toString());
    }

    @Test
    void exceptHole_isCheckedOnSelectAndCustodyInput() {
        ActionInput hollow = ActionInput.of(null, null, null, "Weapon", ActionInput.of(null, null, null, null));
        ActionDef def = ActionDef.of("Unmake").withRecipe(recipe()).withSelect(hollow)
                .withCustody(Custody.of(1, ActionInput.of(null, null, null, "Weapon",
                        ActionInput.of(null, null, null, "Boat")), null));
        Set<String> codes = validate(station("fixturetable", def));
        assertTrue(codes.contains("EXCEPT_CATCH_ALL"), codes.toString());
        assertTrue(codes.contains("UNKNOWN_ACTION_FUNCTION"), codes.toString());
    }

    @Test
    void exceptHole_isCheckedOnEachSocketsMatch_itemAndBlockRoutesAlike() throws Exception {
        String body = "{ \"Sockets\": {"
                + " \"slot_a\": { \"Item\": { \"Match\": { \"Function\": \"Weapon\", \"Except\": {} } } },"
                + " \"vessel\": { \"Block\": { \"At\": { \"Y\": 1 },"
                + " \"Match\": { \"ItemId\": \"Fixture_Pot\", \"Except\": { \"Function\": \"Boat\" } } } },"
                + " \"slot_b\": { \"Item\": { \"Match\": { \"Function\": \"Weapon\","
                + " \"Except\": { \"Function\": \"Tool\" } } } } } }";
        Custody sockets = Custody.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
        ActionDef def = ActionDef.of("Unmake").withRecipe(recipe()).withCustody(sockets);
        List<Finding> findings = StationValidator.validate(List.of(station("fixturetable", def)), ANY, ANY, ANY);
        Set<String> hollow = findings.stream().filter(f -> f.code().equals("EXCEPT_CATCH_ALL"))
                .map(Finding::message).collect(Collectors.toSet());
        assertTrue(hollow.size() == 1 && hollow.iterator().next().contains("Custody.Sockets['slot_a'].Match.Except"),
                "the one hollow Except, on the item socket's Match: " + hollow);
        Set<String> unknown = findings.stream().filter(f -> f.code().equals("UNKNOWN_ACTION_FUNCTION"))
                .map(Finding::message).collect(Collectors.toSet());
        assertTrue(unknown.size() == 1 && unknown.iterator().next().contains("Custody.Sockets['vessel'].Match.Except"),
                "the block socket's Match is checked too: " + unknown);
    }

    @Test
    void anExtensionsPace_isLegalOnAnActionTarget_carriesNoClampLeaf_andAnEmptyGroupWarns() throws Exception {
        StationAsset target = station("fixturetable", ActionDef.of("Unmake").withRecipe(recipe()));
        // The extension payload is typed {Ladder} only: a stray Clamp key is not a leaf of it, so
        // it decodes to nothing (the engine records it as an unknown key and logs one "Unused
        // key(s)" WARNING at load; the asset still loads) and the ladder still lands. There is no
        // Clamp on an extension's pace to note or to ignore.
        ExtensionAsset strayClamp = extension("pack-pace",
                "{ \"Target\": { \"Action\": \"Unmake\" }, \"Pace\": { \"Ladder\": { \"Factors\": ["
                        + " { \"Factor\": \"fixture:level\" } ], \"Floors\": [ { \"Min\": 20, \"Scale\": 0.75 } ] },"
                        + " \"Clamp\": { \"Min\": 0.1 } } }");
        assertTrue(strayClamp.getPace().getClamp() == null, "an extension's pace never decodes a Clamp");
        assertTrue(strayClamp.getPace().getLadder() != null, "its ladder decodes as authored");
        Set<String> codes = codes(StationValidator.validateExtensions(List.of(strayClamp), List.of(target), List.of(),
                ANY, ANY, ANY, ANY));
        assertFalse(codes.contains("EXTENSION_PAYLOAD_MISMATCH"), codes.toString());
        assertFalse(codes.contains("PACE_UNCLAMPED"), "the clamp rule is the ACTION's, never asked of an extension");
        ExtensionAsset onStation = extension("station-pace",
                "{ \"Target\": { \"Station\": \"fixturetable\" }, \"Pace\": { \"Ladder\": { \"Factors\": [] } } }");
        codes = codes(StationValidator.validateExtensions(List.of(onStation), List.of(target), List.of(),
                ANY, ANY, ANY, ANY));
        assertTrue(codes.contains("EXTENSION_PAYLOAD_MISMATCH"), codes.toString());
        ExtensionAsset noLadder = extension("empty-pace",
                "{ \"Target\": { \"Action\": \"Unmake\" }, \"Pace\": { } }");
        codes = codes(StationValidator.validateExtensions(List.of(noLadder), List.of(target), List.of(),
                ANY, ANY, ANY, ANY));
        assertTrue(codes.contains("PACE_EXTENSION_NO_LADDER"), codes.toString());
    }

    @Test
    void theExtensionPayloadTable_admitsPaceOnAnActionTargetOnly() {
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION, ExtensionAsset.PAYLOAD_PACE));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_PACE));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.LOOTABLE, ExtensionAsset.PAYLOAD_PACE));
        Map<String, Object> unused = new LinkedHashMap<>();
        assertTrue(unused.isEmpty());
    }
}
