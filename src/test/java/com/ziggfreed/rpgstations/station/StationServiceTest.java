package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Pure tests for {@link StationService}'s extracted decision cores. Ported (trimmed) from the
 * MMO's {@code StationServiceTest} (RPG Stations extraction leg 2): the MMO-specific XP-factor-
 * breakdown tests and the {@code unlockedFlairIdsFrom}/{@code PlayerDataRepository} test are
 * dropped (that bookkeeping moved off {@code StationSession}, see its javadoc); the Requires
 * {@link Condition} evaluation tests are NEW this leg (design section 4.4.2).
 */
public class StationServiceTest {

    // ==================== Delayed swing-impact due-time ====================

    @Test
    void scheduleImpactAt_addsTheDelayToNow() {
        assertEquals(1140L, StationService.scheduleImpactAt(1000L, 140L));
    }

    @Test
    void impactDue_falseBeforeTheDueTime() {
        assertFalse(StationService.impactDue(999L, 1000L));
    }

    @Test
    void impactDue_trueAtExactlyTheDueTime() {
        assertTrue(StationService.impactDue(1000L, 1000L));
    }

    @Test
    void impactDue_trueAfterTheDueTime() {
        assertTrue(StationService.impactDue(1500L, 1000L));
    }

    @Test
    void impactDue_falseWhenNothingIsPending() {
        assertFalse(StationService.impactDue(999_999L, 0L));
    }

    // ==================== Item-ledger tally ====================

    @Test
    void tallyConsumedResource_sumsPerItemIdAcrossSlots() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> slots = List.of(
                new StationService.ConsumedSlot("Wood_Oak_Log", 2),
                new StationService.ConsumedSlot("Wood_Birch_Log", 1),
                new StationService.ConsumedSlot("Wood_Oak_Log", 3));

        StationService.tallyConsumedResource(tally, slots, "Wood_Hardwood_Trunk");

        assertEquals(2, tally.size());
        assertEquals(5, tally.get("Wood_Oak_Log"));
        assertEquals(1, tally.get("Wood_Birch_Log"));
    }

    @Test
    void tallyConsumedResource_accumulatesAcrossCallsIntoTheSameTally() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        StationService.tallyConsumedResource(tally,
                List.of(new StationService.ConsumedSlot("Wood_Oak_Log", 1)), "Wood_Hardwood_Trunk");
        StationService.tallyConsumedResource(tally,
                List.of(new StationService.ConsumedSlot("Wood_Oak_Log", 4)), "Wood_Hardwood_Trunk");

        assertEquals(5, tally.get("Wood_Oak_Log"));
    }

    @Test
    void tallyConsumedResource_fallsBackToResourceTypeIdWhenNoSlotHasAnItemId() {
        Map<String, Integer> tally = new LinkedHashMap<>();

        StationService.tallyConsumedResource(tally, List.of(), "Wood_Hardwood_Trunk");

        assertEquals(1, tally.get("Wood_Hardwood_Trunk"));
    }

    @Test
    void tallyConsumedResource_ignoresAZeroOrNegativeConsumedSlot() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> slots = List.of(
                new StationService.ConsumedSlot("Wood_Oak_Log", 0),
                new StationService.ConsumedSlot(null, 3));

        StationService.tallyConsumedResource(tally, slots, "Wood_Hardwood_Trunk");

        assertFalse(tally.containsKey("Wood_Oak_Log"));
        assertEquals(1, tally.get("Wood_Hardwood_Trunk"));
    }

    // ==================== Stamp-reagent consumed tally (shared mergeConsumedSlots core) ====================
    // The pure core StationService#tallyConsumedStacks folds a Stamp step's committed reagents into
    // s.consumedItems so the enhance summary shows a CONSUMED row per input stack (e.g. the 2
    // sharpened bars). The ItemStack-reading adapter itself needs a live JVM (ItemStack's static
    // init fails in unit tests, per StationStampMutationTest), so these target its shared fold core.

    @Test
    void mergeConsumedSlots_sumsReagentStacksPerItemIdAndReportsAny() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> reagents = List.of(
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 1),
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 1));

        boolean any = StationService.mergeConsumedSlots(tally, reagents);

        assertTrue(any);
        assertEquals(1, tally.size());
        assertEquals(2, tally.get("MMO_Sharpened_Iron_Bar"));
    }

    @Test
    void mergeConsumedSlots_mixedReagentFamilyKeepsDistinctDrainedIds() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> reagents = List.of(
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 1),
                new StationService.ConsumedSlot("MMO_Sharpened_Gold_Bar", 1));

        StationService.mergeConsumedSlots(tally, reagents);

        assertEquals(2, tally.size());
        assertEquals(1, tally.get("MMO_Sharpened_Iron_Bar"));
        assertEquals(1, tally.get("MMO_Sharpened_Gold_Bar"));
    }

    @Test
    void mergeConsumedSlots_accumulatesOntoAnExistingConsumedLedger() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        tally.put("Wood_Oak_Log", 4); // a prior implicit-program Consume already tallied here
        StationService.mergeConsumedSlots(tally,
                List.of(new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 2)));

        assertEquals(4, tally.get("Wood_Oak_Log"));
        assertEquals(2, tally.get("MMO_Sharpened_Iron_Bar"));
    }

    @Test
    void mergeConsumedSlots_emptyOrUnusable_reportsNoneAndAddsNoFallback() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> slots = List.of(
                new StationService.ConsumedSlot(null, 3),
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 0));

        boolean any = StationService.mergeConsumedSlots(tally, slots);

        assertFalse(any);
        assertTrue(tally.isEmpty()); // no raw-resource-type fallback on this path, unlike tallyConsumedResource
    }

    // ==================== Held-tool gate decision ====================

    @Test
    void toolGateStopReason_matchingAndNotBroken_continues() {
        assertNull(StationService.toolGateStopReason(true, false));
    }

    @Test
    void toolGateStopReason_notMatching_stopsToolChanged() {
        assertEquals(StationService.StopReason.TOOL_CHANGED, StationService.toolGateStopReason(false, false));
    }

    @Test
    void toolGateStopReason_matchingButBroken_stopsToolBroken() {
        assertEquals(StationService.StopReason.TOOL_BROKEN, StationService.toolGateStopReason(true, true));
    }

    @Test
    void toolGateStopReason_notMatchingAndBroken_toolChangedWins() {
        assertEquals(StationService.StopReason.TOOL_CHANGED, StationService.toolGateStopReason(false, true));
    }

    // ==================== Seat-mode heartbeat decision ====================

    @Test
    void seatModeShouldStop_effectMode_neverStops() {
        assertFalse(StationService.seatModeShouldStop(false, true));
        assertFalse(StationService.seatModeShouldStop(false, false));
    }

    @Test
    void seatModeShouldStop_seatedAndStillMounted_continues() {
        assertFalse(StationService.seatModeShouldStop(true, true));
    }

    @Test
    void seatModeShouldStop_seatedButNoLongerMounted_stops() {
        assertTrue(StationService.seatModeShouldStop(true, false));
    }

    // ==================== Seat-vs-effect swing-route decision ====================

    @Test
    void useActionSlotForSwing_seatMode_usesActionSlot() {
        assertTrue(StationService.useActionSlotForSwing(true));
    }

    @Test
    void useActionSlotForSwing_effectMode_usesEmoteSlot() {
        assertFalse(StationService.useActionSlotForSwing(false));
    }

    // ==================== Session-completion presentation gate ====================

    @Test
    void shouldPlayCompletion_nonSilentWithCycles_true() {
        assertTrue(StationService.shouldPlayCompletion(false, 1));
        assertTrue(StationService.shouldPlayCompletion(false, 5));
    }

    @Test
    void shouldPlayCompletion_nonSilentZeroCycles_false() {
        assertFalse(StationService.shouldPlayCompletion(false, 0));
    }

    @Test
    void shouldPlayCompletion_silentWithCycles_false() {
        assertFalse(StationService.shouldPlayCompletion(true, 5));
    }

    @Test
    void shouldPlayCompletion_silentZeroCycles_false() {
        assertFalse(StationService.shouldPlayCompletion(true, 0));
    }

    // ==================== Requires.Condition evaluation (new this leg, design section 4.4.2) ====================

    @Test
    void conditionPasses_blankFactor_passesVacuously() {
        Condition c = Condition.of("", null, null, null);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 999.0));
    }

    @Test
    void conditionPasses_unregisteredFactor_failsClosed() {
        Condition c = Condition.of("rpgstations:tool_power", null, null, null);
        assertFalse(StationService.conditionPasses(c, (factorId, param) -> null));
    }

    @Test
    void conditionPasses_belowMin_fails() {
        Condition c = Condition.of("mmoskilltree:skill_level", "WOODCUTTING", 15.0, null);
        assertFalse(StationService.conditionPasses(c, (factorId, param) -> 10.0));
    }

    @Test
    void conditionPasses_atOrAboveMin_passes() {
        Condition c = Condition.of("mmoskilltree:skill_level", "WOODCUTTING", 15.0, null);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 15.0));
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 20.0));
    }

    @Test
    void conditionPasses_aboveMax_fails() {
        Condition c = Condition.of("rpgstations:cycle_count", null, null, 10.0);
        assertFalse(StationService.conditionPasses(c, (factorId, param) -> 11.0));
    }

    @Test
    void conditionPasses_atOrBelowMax_passes() {
        Condition c = Condition.of("rpgstations:cycle_count", null, null, 10.0);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 10.0));
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 5.0));
    }

    @Test
    void conditionPasses_paramForwardedToTheLookup() {
        Condition c = Condition.of("mmoskilltree:skill_level", "MINING", 5.0, null);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> {
            assertEquals("mmoskilltree:skill_level", factorId);
            assertEquals("MINING", param);
            return 5.0;
        }));
    }

    // ==================== Selection wave (decision 50/56): routing + category filter ====================

    private static StationAsset.Conversion conv(String outItem, String category) {
        return StationAsset.Conversion.of(Ingredient.resource("Wood_Trunk", 1),
                Ingredient.item(outItem, 1), null, category);
    }

    @Test
    void decideRoute_plainPress_alwaysToggles() {
        assertEquals(StationService.Route.TOGGLE, StationService.decideRoute(false, 5));
        assertEquals(StationService.Route.TOGGLE, StationService.decideRoute(false, 0));
    }

    @Test
    void decideRoute_sneakMultiCategory_opensPicker() {
        assertEquals(StationService.Route.PICKER, StationService.decideRoute(true, 2));
        assertEquals(StationService.Route.PICKER, StationService.decideRoute(true, 9));
    }

    @Test
    void decideRoute_sneakSingleOrZeroCategory_toggles() {
        assertEquals(StationService.Route.TOGGLE, StationService.decideRoute(true, 1));
        assertEquals(StationService.Route.TOGGLE, StationService.decideRoute(true, 0));
    }

    @Test
    void conversionsForCategory_nullChosen_returnsAllUnchanged() {
        StationAsset.Conversion[] all = {conv("Oak", "WoodPlanks"), conv("Iron", "Metal")};
        assertSame(all, StationService.conversionsForCategory(all, null));
    }

    @Test
    void conversionsForCategory_blankChosen_returnsAllUnchanged() {
        // "stop clears" -> the field is back to null/blank -> the filter yields ALL again.
        StationAsset.Conversion[] all = {conv("Oak", "WoodPlanks"), conv("Iron", "Metal")};
        assertSame(all, StationService.conversionsForCategory(all, "   "));
    }

    @Test
    void conversionsForCategory_nullArray_returnsNull() {
        assertNull(StationService.conversionsForCategory(null, "WoodPlanks"));
    }

    @Test
    void conversionsForCategory_chosen_keepsOnlyMatching_caseInsensitive() {
        StationAsset.Conversion[] all = {conv("Oak", "WoodPlanks"), conv("Iron", "Metal"),
                conv("Birch", "woodplanks"), conv("Untagged", null)};
        StationAsset.Conversion[] filtered = StationService.conversionsForCategory(all, "WoodPlanks");
        assertEquals(2, filtered.length);
        assertEquals("Oak", filtered[0].primaryOutput().getItemId());
        assertEquals("Birch", filtered[1].primaryOutput().getItemId());
    }

    @Test
    void conversionsForCategory_chosen_excludesUntaggedConversions() {
        StationAsset.Conversion[] all = {conv("Untagged", null)};
        assertEquals(0, StationService.conversionsForCategory(all, "WoodPlanks").length);
    }

    @Test
    void distinctConversionCategories_orderedDistinctCaseInsensitive_untaggedIgnored() {
        StationAsset.Conversion[] all = {conv("Oak", "WoodPlanks"), conv("Iron", "Metal"),
                conv("Birch", "woodplanks"), conv("Untagged", null), conv("Steel", "Metal")};
        assertEquals(List.of("WoodPlanks", "Metal"), StationService.distinctConversionCategories(all));
    }

    @Test
    void distinctConversionCategories_nullOrAllUntagged_isEmpty() {
        assertTrue(StationService.distinctConversionCategories(null).isEmpty());
        assertTrue(StationService.distinctConversionCategories(
                new StationAsset.Conversion[]{conv("A", null), conv("B", "  ")}).isEmpty());
    }

    @Test
    void representativeOutputFor_firstMatchingOutputItem() {
        StationAsset.Conversion[] all = {conv("Oak", "WoodPlanks"), conv("Birch", "WoodPlanks"),
                conv("Iron", "Metal")};
        assertEquals("Oak", StationService.representativeOutputFor(all, "WoodPlanks"));
        assertEquals("Iron", StationService.representativeOutputFor(all, "metal"));
        assertNull(StationService.representativeOutputFor(all, "Unknown"));
        assertNull(StationService.representativeOutputFor(null, "WoodPlanks"));
    }

    // ============ Picker cost/name wave (maintainer smoke fix): representative conversion + cost line ============

    @Test
    void representativeConversionFor_firstMatchingConversion_sameOneOutputForDerivesFrom() {
        StationAsset.Conversion[] all = {conv("Oak", "WoodPlanks"), conv("Birch", "WoodPlanks"),
                conv("Iron", "Metal")};
        StationAsset.Conversion rep = StationService.representativeConversionFor(all, "WoodPlanks");
        assertEquals("Oak", rep.primaryOutput().getItemId());
        // representativeOutputFor is a thin wrapper over the SAME scan: must agree with it.
        assertEquals(StationService.representativeOutputFor(all, "WoodPlanks"), rep.primaryOutput().getItemId());
        assertNull(StationService.representativeConversionFor(all, "Unknown"));
        assertNull(StationService.representativeConversionFor(null, "WoodPlanks"));
    }

    // ==== Decision 66 (2026-07-29 smoke fix): the picker previews the PLACED species, not blackwood ====

    /** One derived conversion: {@code <species>_Trunk} family in, {@code outItem} out, tagged {@code category}. */
    private static StationAsset.Conversion convFrom(String inResourceTypeId, String outItem, String category) {
        return StationAsset.Conversion.of(Ingredient.resource(inResourceTypeId, 1),
                Ingredient.item(outItem, 1), null, category);
    }

    /**
     * The real shipped shape this decision fixes: one conversion per SPECIES per category, sorted by
     * output item id (the deriver's own order), so Blackwood always sorts first within a category.
     */
    private static StationAsset.Conversion[] multiSpeciesConversions() {
        return new StationAsset.Conversion[]{
                convFrom("Wood_Blackwood_Trunk", "Wood_Blackwood_Decorative", "DecorativePlanks"),
                convFrom("Wood_Hardwood_Trunk", "Wood_Hardwood_Decorative", "DecorativePlanks"),
                convFrom("Wood_Blackwood_Trunk", "Wood_Blackwood_Planks", "WoodPlanks"),
                convFrom("Wood_Hardwood_Trunk", "Wood_Hardwood_Planks", "WoodPlanks")};
    }

    @Test
    void representativeConversionFor_noPreference_keepsFirstMatch_byteIdenticalToPreDecision65() {
        StationAsset.Conversion[] all = multiSpeciesConversions();
        // Both the 2-arg overload and an explicitly-null preference must answer the old first-match.
        assertEquals("Wood_Blackwood_Planks",
                StationService.representativeConversionFor(all, "WoodPlanks").primaryOutput().getItemId());
        assertEquals("Wood_Blackwood_Planks",
                StationService.representativeConversionFor(all, "WoodPlanks", null, null).primaryOutput().getItemId());
        assertEquals("Wood_Blackwood_Planks",
                StationService.representativeConversionFor(all, "WoodPlanks", "  ", null).primaryOutput().getItemId());
    }

    @Test
    void representativeConversionFor_preferredResourceFamily_winsOverTheAlphabeticalFirst() {
        StationAsset.Conversion[] all = multiSpeciesConversions();
        String[] hardwood = {"Wood_Hardwood_Trunk"};
        // THE BUG: a sawmill loaded with hardwood used to preview Blackwood in every tab.
        assertEquals("Wood_Hardwood_Planks", StationService.representativeConversionFor(
                all, "WoodPlanks", "Wood_Hardwood_Log", hardwood).primaryOutput().getItemId());
        // ... and the bias applies per category, so the whole strip stays on one species.
        assertEquals("Wood_Hardwood_Decorative", StationService.representativeConversionFor(
                all, "DecorativePlanks", "Wood_Hardwood_Log", hardwood).primaryOutput().getItemId());
    }

    @Test
    void representativeConversionFor_preferredExactItemId_matchesAnItemIdInput() {
        StationAsset.Conversion[] all = {
                StationAsset.Conversion.of(Ingredient.item("Food_Fish_Raw", 1),
                        Ingredient.item("Food_Fish_Grilled", 1), null, "Cook"),
                StationAsset.Conversion.of(Ingredient.item("Food_Meat_Raw", 1),
                        Ingredient.item("Food_Meat_Grilled", 1), null, "Cook")};
        assertEquals("Food_Meat_Grilled", StationService.representativeConversionFor(
                all, "Cook", "Food_Meat_Raw", null).primaryOutput().getItemId());
    }

    @Test
    void representativeConversionFor_preferredMatchesNothingInCategory_fallsBackToFirstUsable() {
        StationAsset.Conversion[] all = multiSpeciesConversions();
        // A category the loaded material cannot produce must still render a tab (never vanish),
        // so an unmatched preference degrades to the plain first-match.
        assertEquals("Wood_Blackwood_Planks", StationService.representativeConversionFor(
                all, "WoodPlanks", "Metal_Iron_Ore", new String[]{"Metal_Iron"}).primaryOutput().getItemId());
        assertNull(StationService.representativeConversionFor(all, "Unknown", "Wood_Hardwood_Log",
                new String[]{"Wood_Hardwood_Trunk"}));
        assertNull(StationService.representativeConversionFor(null, "WoodPlanks", "Wood_Hardwood_Log",
                new String[]{"Wood_Hardwood_Trunk"}));
    }

    @Test
    void matchesConversionInput_familyAndExactRoutes_agreeWithMatchesAnyConversionInput() {
        StationAsset.Conversion family = convFrom("Wood_Hardwood_Trunk", "Wood_Hardwood_Planks", "WoodPlanks");
        StationAsset.Conversion exact = StationAsset.Conversion.of(Ingredient.item("Food_Fish_Raw", 1),
                Ingredient.item("Food_Fish_Grilled", 1), null, "Cook");
        String[] hardwood = {"Wood_Hardwood_Trunk"};

        assertTrue(StationCustody.matchesConversionInput(family, "Wood_Hardwood_Log", hardwood));
        assertFalse(StationCustody.matchesConversionInput(family, "Wood_Hardwood_Log", new String[]{"Wood_Softwood_Trunk"}));
        assertFalse(StationCustody.matchesConversionInput(family, "Wood_Hardwood_Log", null));
        assertTrue(StationCustody.matchesConversionInput(exact, "Food_Fish_Raw", null));
        assertTrue(StationCustody.matchesConversionInput(exact, "food_fish_raw", null));
        assertFalse(StationCustody.matchesConversionInput(exact, "Food_Meat_Raw", null));
        assertFalse(StationCustody.matchesConversionInput(null, "Food_Fish_Raw", null));

        // The extracted single-conversion core must agree with the any-of loop it was pulled out of.
        StationAsset.Conversion[] both = {family, exact};
        assertTrue(StationCustody.matchesAnyConversionInput(both, "Food_Fish_Raw", null));
        assertTrue(StationCustody.matchesAnyConversionInput(both, "Wood_Hardwood_Log", hardwood));
        assertFalse(StationCustody.matchesAnyConversionInput(both, "Metal_Iron_Ore", new String[]{"Metal_Iron"}));
    }

    @Test
    void pickerCostLine_resourceTypeInput_resolvesBothSidesAsNestedItemNameMessages() {
        // conv()'s fixture Input is Ingredient.resource("Wood_Trunk", 1) - the sawmill's real
        // "any Trunk of this species" shape (asset.Ingredient's ResourceTypeId route, no exact
        // item id) - so this exercises the exact case the maintainer's own "1x Trunk -> 4x Planks"
        // example describes.
        StationAsset.Conversion c = conv("Wood_Oak_Planks", "WoodPlanks");
        Message cost = StationService.pickerCostLine(c);
        assertEquals("rpgstations.ui.station.picker.cost", cost.getMessageId());
    }

    @Test
    void pickerCostLine_missingInputOrOutput_returnsNull() {
        StationAsset.Conversion noInput = StationAsset.Conversion.of(null, Ingredient.item("Planks", 4));
        assertNull(StationService.pickerCostLine(noInput));

        StationAsset.Conversion noOutput = StationAsset.Conversion.of(Ingredient.resource("Wood_Trunk", 1), null);
        assertNull(StationService.pickerCostLine(noOutput));

        StationAsset.Conversion blankInputIds = StationAsset.Conversion.of(
                Ingredient.of(null, null, 1), Ingredient.item("Planks", 4));
        assertNull(StationService.pickerCostLine(blankInputIds));
    }

    // ============ Selection-wave SEAL (decision 57): first-authored-category default ============

    /** The shipped multi-category sawmill shape: 3 derived output categories, WoodPlanks authored first. */
    private static StationAsset.FromCrafting sawmillFromCrafting() {
        return StationAsset.FromCrafting.of(
                new String[]{"WoodPlanks", "DecorativePlanks", "OrnatePlanks"});
    }

    /** Derived conversions arrive alphabetically by output id (Decorative < Ornate < Planks). */
    private static StationAsset.Conversion[] sawmillConversions() {
        return new StationAsset.Conversion[]{
                conv("Oak_Decorative", "DecorativePlanks"),
                conv("Oak_Ornate", "OrnatePlanks"),
                conv("Oak_Planks", "WoodPlanks")};
    }

    @Test
    void effectiveCategory_multiCategoryNoChoice_defaultsToFirstAuthored() {
        // The deriver's alphabetical output-id sort would make Decorative the first RUNNABLE, but
        // decision 57 defaults to the first AUTHORED FromCrafting category (WoodPlanks = Planks).
        assertEquals("WoodPlanks",
                StationService.effectiveCategory(null, sawmillFromCrafting(), sawmillConversions()));
    }

    @Test
    void effectiveCategory_singleCategoryNoChoice_returnsNull_byteIdenticalAllPass() {
        StationAsset.Conversion[] all = {conv("Oak_Planks", "WoodPlanks"),
                conv("Birch_Planks", "woodplanks")};
        StationAsset.FromCrafting fc = StationAsset.FromCrafting.of(new String[]{"WoodPlanks"});
        assertNull(StationService.effectiveCategory(null, fc, all));
        // The all-pass stays byte-identical: the same array reference flows through unchanged.
        assertSame(all, StationService.conversionsForCategory(all,
                StationService.effectiveCategory(null, fc, all)));
    }

    @Test
    void effectiveCategory_explicitChoiceOverridesDefault() {
        assertEquals("DecorativePlanks", StationService.effectiveCategory(
                "DecorativePlanks", sawmillFromCrafting(), sawmillConversions()));
    }

    @Test
    void effectiveCategory_stopClearResetsToDefault() {
        // A live session with an explicit pick honours it; after stop (the field is nulled) the
        // next resolve re-derives the first-authored default.
        assertEquals("OrnatePlanks", StationService.effectiveCategory(
                "OrnatePlanks", sawmillFromCrafting(), sawmillConversions()));
        assertEquals("WoodPlanks",
                StationService.effectiveCategory(null, sawmillFromCrafting(), sawmillConversions()));
    }

    @Test
    void effectiveCategory_noFromCrafting_multiCategory_fallsBackToFirstDerived() {
        // Purely hand-authored multi-category conversions (no FromCrafting): the default is the
        // FIRST derived conversion's own category, in array order.
        StationAsset.Conversion[] all = {conv("A", "Beta"), conv("B", "Alpha")};
        assertEquals("Beta", StationService.effectiveCategory(null, null, all));
    }

    @Test
    void effectiveCategory_firstAuthoredCategoryDerivedNothing_skipsToNextPresent() {
        // "Empty" is authored first but derived no conversion; the default is the first authored
        // category actually PRESENT in the derived set, so a dead first entry cannot blank the loop.
        StationAsset.FromCrafting fc = StationAsset.FromCrafting.of(
                new String[]{"Empty", "WoodPlanks", "DecorativePlanks"});
        assertEquals("WoodPlanks", StationService.effectiveCategory(null, fc, sawmillConversions()));
    }

    @Test
    void effectiveCategory_blankChoiceFallsThroughToDefault() {
        // A blank chosen category is treated as "no choice" (mirrors conversionsForCategory), so the
        // first-authored default still applies.
        assertEquals("WoodPlanks",
                StationService.effectiveCategory("  ", sawmillFromCrafting(), sawmillConversions()));
    }

    // ============ Work-site contribution forwarding (the deliberately weak filter) ============

    @Test
    void contributionsFrom_zeroAmountEntryStillForwards() {
        // LOAD-BEARING asymmetry: the one-shot grants site gates on Contribution#isPostable (a
        // positive amount), the per-cycle site deliberately does NOT. A zero-amount per-cycle entry
        // must still reach a listener so it can render as a visible zero row in a breakdown rather
        // than vanishing without explanation.
        List<StationContribution> out = StationService.contributionsFrom(
                new Contribution[]{Contribution.of("yourmod:test", "ALPHA", 0.0)}, false, 0.1);
        assertEquals(1, out.size());
        assertEquals("yourmod:test", out.get(0).channel());
        assertEquals("ALPHA", out.get(0).param());
        assertEquals(0.0, out.get(0).amount(), 1e-9);
    }

    @Test
    void contributionsFrom_nullAmountReadsAsZero() {
        List<StationContribution> out = StationService.contributionsFrom(
                new Contribution[]{Contribution.of("yourmod:test", null, null)}, false, 0.1);
        assertEquals(1, out.size());
        assertNull(out.get(0).param());
        assertEquals(0.0, out.get(0).amount(), 1e-9);
    }

    @Test
    void contributionsFrom_blankChannelIsSkipped() {
        List<StationContribution> out = StationService.contributionsFrom(new Contribution[]{
                Contribution.of("  ", "ALPHA", 5.0),
                Contribution.of("yourmod:test", "BETA", 5.0)}, false, 0.1);
        assertEquals(1, out.size());
        assertEquals("BETA", out.get(0).param());
    }

    @Test
    void contributionsFrom_idleCyclePreScalesByTheIdleFraction() {
        List<StationContribution> out = StationService.contributionsFrom(
                new Contribution[]{Contribution.of("yourmod:test", "ALPHA", 8.0)}, true, 0.25);
        assertEquals(2.0, out.get(0).amount(), 1e-9);
    }
}
