package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.inventory.DisposableItemMetadata;
import com.ziggfreed.common.recipe.NativeRecipe;
import com.ziggfreed.common.recipe.RecipeBench;
import com.ziggfreed.common.recipe.RecipeMaterial;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE fallback-route cores ({@link StationFallbackRoutes}) and the metadata guard's policy
 * ({@link StationMetadataGuard#acceptsReading}): the gear filter under the shared matcher's one
 * rule (its {@code Except} hole on a route set and on a catch-all alike), the crafting share's
 * stated rounding rule over a recipe's output quantity, the essence-only row's one allowed empty
 * output, the ONE-route rule behind the resolution chain and the ONE row a pile is offered, which
 * selection hands the runnable scan unchanged (the seam that keeps a full inventory from turning a
 * share-eligible piece into an essence-only consumption), and which metadata keys a piece may
 * carry (only keys some mod declared disposable on the library's list). Every fixture value is
 * authored by this test.
 */
public class StationFallbackRoutesTest {

    private static final Map<String, String[]> WEAPON_TAGS = Map.of("Type", new String[] {"Weapon"});
    private static final Map<String, String[]> AMMO_TAGS = Map.of("Type", new String[] {"Ammo"});

    private static ActionInput gearFilter() {
        return ActionInput.of(null, null, Map.of("Type", new String[] {"Weapon", "Armor", "Tool"}), null,
                ActionInput.of(null, null, Map.of("Type", new String[] {"Ammo"}), null));
    }

    /** A recipe making ONE piece per craft. */
    private static NativeRecipe ownRecipe(RecipeMaterial... inputs) {
        return ownRecipe(1, inputs);
    }

    /** A recipe making {@code outputQuantity} pieces per craft (a batch recipe when above 1). */
    private static NativeRecipe ownRecipe(int outputQuantity, RecipeMaterial... inputs) {
        RecipeMaterial product = RecipeMaterial.item("Fixture_Sword", outputQuantity);
        return new NativeRecipe("Fixture_Sword_Recipe_Generated_0", List.of(inputs),
                List.of(product), product,
                List.of(new RecipeBench("Fixture_Bench", "Crafting", List.of(), 0)), 2f, "Fixture_Sword");
    }

    // ==================== the gear filter and its Except hole ====================

    @Test
    void filter_acceptsGear_andTheExceptHoleRefusesAmmo() {
        StationAsset.Fallback fallback = StationAsset.Fallback.of(gearFilter(), null, StationAsset.Fallback.EssenceOnly.of(null));
        assertTrue(StationFallbackRoutes.inFilter(fallback, "Fixture_Sword", null, WEAPON_TAGS, "Weapon"));
        assertFalse(StationFallbackRoutes.inFilter(fallback, "Fixture_Arrow", null, AMMO_TAGS, null),
                "ammunition is outside the Type filter");
        Map<String, String[]> weaponAndAmmo = Map.of("Type", new String[] {"Weapon", "Ammo"});
        assertFalse(StationFallbackRoutes.inFilter(fallback, "Fixture_Dart", null, weaponAndAmmo, "Weapon"),
                "a piece the routes accept is still refused when the Except hole accepts it too");
        assertFalse(StationFallbackRoutes.inFilter(fallback, "Fixture_Rock", null, Map.of(), null));
    }

    @Test
    void filter_absent_acceptsAnything_andACatchAllWithExcept_acceptsAllButTheHole() {
        StationAsset.Fallback open = StationAsset.Fallback.of(null, null, StationAsset.Fallback.EssenceOnly.of(null));
        assertTrue(StationFallbackRoutes.inFilter(open, "Fixture_Rock", null, Map.of(), null));
        ActionInput catchAllWithHole = ActionInput.of(null, null, null, null, ActionInput.of(null, null, AMMO_TAGS, null));
        StationAsset.Fallback holeOnly = StationAsset.Fallback.of(catchAllWithHole,
                null, StationAsset.Fallback.EssenceOnly.of(null));
        assertTrue(StationFallbackRoutes.inFilter(holeOnly, "Fixture_Sword", null, WEAPON_TAGS, "Weapon"));
        assertFalse(StationFallbackRoutes.inFilter(holeOnly, "Fixture_Arrow", null, AMMO_TAGS, null));
        assertFalse(StationFallbackRoutes.inFilter(null, "Fixture_Sword", null, WEAPON_TAGS, "Weapon"),
                "no Fallback group at all falls back to nothing");
        // The filter IS the shared matcher's one rule, so the two answer alike at every site.
        assertEquals(StationCustody.accepts(catchAllWithHole, "Fixture_Arrow", null, AMMO_TAGS, null),
                StationFallbackRoutes.inFilter(holeOnly, "Fixture_Arrow", null, AMMO_TAGS, null));
    }

    // ==================== the crafting share and its rounding rule ====================

    @Test
    void craftingShare_floorsEachLine_dropsWhatRoundsToNothing_andDropsFamilyLines() {
        Ingredient[] shared = StationFallbackRoutes.shareOf(List.of(
                RecipeMaterial.item("Fixture_Bar", 3),
                RecipeMaterial.item("Fixture_Grip", 1),
                RecipeMaterial.resource("Fixture_Wood_Family", 8),
                RecipeMaterial.tagged("Fixture_Tag", 4)), 0.5, 1);
        assertNotNull(shared);
        assertEquals(1, shared.length, "the grip rounds to nothing and the family and tag lines name no item");
        assertEquals("Fixture_Bar", shared[0].getItemId());
        assertEquals(1, shared[0].effectiveQuantity(), "floor(3 x 0.5 / 1) = 1");
    }

    @Test
    void craftingShare_dividesByTheRecipesOutputQuantity_soABatchRecipePaysPerPiece() {
        // One bar makes four darts: a half share of the bar is a quarter dart's worth, nothing.
        assertNull(StationFallbackRoutes.shareOf(List.of(RecipeMaterial.item("Fixture_Bar", 1)), 0.5, 4),
                "floor(1 x 0.5 / 4) = 0: one dart never pays back the whole bar's share");
        // Five essence make two baits: a half share per bait is floor(5 x 0.5 / 2) = 1.
        Ingredient[] perBait = StationFallbackRoutes.shareOf(List.of(RecipeMaterial.item("Fixture_Essence", 5)), 0.5, 2);
        assertNotNull(perBait);
        assertEquals(1, perBait[0].effectiveQuantity(), "floor(5 x 0.5 / 2) = 1");
        // Eight bars make two pieces at a whole share: four bars per piece, never eight.
        Ingredient[] whole = StationFallbackRoutes.shareOf(List.of(RecipeMaterial.item("Fixture_Bar", 8)), 1.0, 2);
        assertNotNull(whole);
        assertEquals(4, whole[0].effectiveQuantity(), "floor(8 x 1.0 / 2) = 4");
        // An output quantity below 1 reads as 1: a recipe always makes at least the piece.
        Ingredient[] guarded = StationFallbackRoutes.shareOf(List.of(RecipeMaterial.item("Fixture_Bar", 2)), 0.5, 0);
        assertNotNull(guarded);
        assertEquals(1, guarded[0].effectiveQuantity(), "floor(2 x 0.5 / max(1, 0)) = 1");
        // The row reads the quantity off the recipe's primary output.
        StationAsset.Fallback fallback = StationAsset.Fallback.of(null, StationAsset.Fallback.CraftingShare.of(0.5), null);
        StationAsset.Conversion row = StationFallbackRoutes.craftingShareRow(fallback, "Fixture_Sword",
                ownRecipe(4, RecipeMaterial.item("Fixture_Bar", 8)));
        assertNotNull(row);
        assertEquals(1, row.getOutput()[0].effectiveQuantity(), "floor(8 x 0.5 / 4) = 1 per piece of a four-piece batch");
        assertNull(StationFallbackRoutes.craftingShareRow(fallback, "Fixture_Sword",
                ownRecipe(4, RecipeMaterial.item("Fixture_Bar", 1))), "the dart shape: the route does not apply");
        assertEquals(4, StationFallbackRoutes.outputQuantityOf(ownRecipe(4, RecipeMaterial.item("Fixture_Bar", 1))));
        assertEquals(1, StationFallbackRoutes.outputQuantityOf(ownRecipe(RecipeMaterial.item("Fixture_Bar", 1))));
    }

    @Test
    void craftingShare_withNoLineLeft_doesNotApply() {
        assertNull(StationFallbackRoutes.shareOf(List.of(RecipeMaterial.item("Fixture_Bar", 1)), 0.5, 1));
        assertNull(StationFallbackRoutes.shareOf(List.of(RecipeMaterial.item("Fixture_Bar", 4)), 0.0, 1));
        StationAsset.Fallback fallback = StationAsset.Fallback.of(null, StationAsset.Fallback.CraftingShare.of(0.25), null);
        assertNull(StationFallbackRoutes.craftingShareRow(fallback, "Fixture_Sword",
                ownRecipe(RecipeMaterial.item("Fixture_Bar", 3))), "floor(3 x 0.25 / 1) = 0 leaves no line");
        assertNull(StationFallbackRoutes.craftingShareRow(fallback, "Fixture_Sword", null), "no own recipe, no share");
    }

    @Test
    void craftingShareRow_isATierTwoFallbackRowConsumingOnePiece() {
        StationAsset.Fallback fallback = StationAsset.Fallback.of(null, StationAsset.Fallback.CraftingShare.of(0.5), null);
        StationAsset.Conversion row = StationFallbackRoutes.craftingShareRow(fallback, "Fixture_Sword",
                ownRecipe(RecipeMaterial.item("Fixture_Bar", 4), RecipeMaterial.item("Fixture_Grip", 2)));
        assertNotNull(row);
        assertEquals(StationAsset.Conversion.CRAFTING_SHARE_TIER, row.effectiveTier());
        assertTrue(row.isFallback());
        assertFalse(row.isEssenceOnly());
        assertTrue(row.isComplete());
        assertEquals("Fixture_Sword", row.primaryInput().getItemId());
        assertEquals(1, row.primaryInput().effectiveQuantity());
        assertEquals(2, row.getOutput().length);
        assertEquals(2, row.getOutput()[0].effectiveQuantity());
        assertEquals(1, row.getOutput()[1].effectiveQuantity());
    }

    // ==================== the essence-only row ====================

    @Test
    void essenceOnlyRow_isTheOneRowAllowedToProduceNothing() {
        StationAsset.Fallback on = StationAsset.Fallback.of(null, null, StationAsset.Fallback.EssenceOnly.of(null));
        StationAsset.Conversion row = StationFallbackRoutes.essenceOnlyRow(on, "Fixture_Sword");
        assertNotNull(row);
        assertEquals(StationAsset.Conversion.ESSENCE_ONLY_TIER, row.effectiveTier());
        assertTrue(row.isEssenceOnly());
        assertTrue(row.isFallback());
        assertEquals(0, row.getOutput().length);
        assertTrue(row.isComplete(), "an empty output completes ONLY through this route");
        assertFalse(StationAsset.Conversion.of(Ingredient.item("Fixture_Sword", 1), null).isComplete(),
                "an authored row with no output stays incomplete");
        StationAsset.Fallback off = StationAsset.Fallback.of(null, null, StationAsset.Fallback.EssenceOnly.of(false));
        assertNull(StationFallbackRoutes.essenceOnlyRow(off, "Fixture_Sword"), "a Parent child can switch the route off");
    }

    // ==================== ONE route per piece: the NO_ROOM seam ====================

    @Test
    void routeFor_offersOneRow_theShareWhenItApplies_soAFullInventoryAnswersNoRoomNotEssenceOnly() {
        // The decision behind a full inventory at the table: the piece is offered the share row
        // ALONE, so the runnable check that follows can only answer RUNNABLE or NO_ROOM for it.
        // The essence-only row, whose empty output always fits, is never offered beside it, so it
        // can never be reached instead and destroy a share-eligible piece for nothing.
        StationAsset.Fallback both = StationAsset.Fallback.of(null,
                StationAsset.Fallback.CraftingShare.of(0.5), StationAsset.Fallback.EssenceOnly.of(null));
        StationAsset.Conversion offered = StationFallbackRoutes.routeFor(both, "Fixture_Sword",
                ownRecipe(RecipeMaterial.item("Fixture_Bar", 2)));
        assertNotNull(offered);
        assertEquals(StationAsset.Conversion.CRAFTING_SHARE_TIER, offered.effectiveTier(),
                "the share applies, so the share row is the one row offered");
        assertFalse(offered.isEssenceOnly(), "essence only never stands behind an applying share row");
        assertEquals(1, offered.getOutput().length, "the share row has outputs that must fit, the essence row has none");
        assertTrue(StationAsset.Conversion.DERIVED_TIER < StationAsset.Conversion.CRAFTING_SHARE_TIER,
                "a derived row outranks the crafting share");
    }

    @Test
    void routeFor_fallsToEssenceOnly_onlyWhenTheShareDoesNotApply_andAnyRouteAppliesReadsIt() {
        StationAsset.Fallback both = StationAsset.Fallback.of(null,
                StationAsset.Fallback.CraftingShare.of(0.5), StationAsset.Fallback.EssenceOnly.of(null));
        StationAsset.Conversion noRecipe = StationFallbackRoutes.routeFor(both, "Fixture_Sword", null);
        assertNotNull(noRecipe);
        assertTrue(noRecipe.isEssenceOnly(), "no own recipe: essence only is the route");
        StationAsset.Conversion roundsToNothing = StationFallbackRoutes.routeFor(both, "Fixture_Sword",
                ownRecipe(RecipeMaterial.item("Fixture_Bar", 1)));
        assertNotNull(roundsToNothing);
        assertTrue(roundsToNothing.isEssenceOnly(), "a share that leaves no line falls through to essence only");
        assertTrue(StationFallbackRoutes.anyRouteApplies(both, "Fixture_Sword", null),
                "essence only applies with no own recipe at all");
        StationAsset.Fallback shareOnly = StationAsset.Fallback.of(null, StationAsset.Fallback.CraftingShare.of(0.5), null);
        assertNull(StationFallbackRoutes.routeFor(shareOnly, "Fixture_Sword", null));
        assertFalse(StationFallbackRoutes.anyRouteApplies(shareOnly, "Fixture_Sword", null),
                "a share with no own recipe applies to nothing");
        assertFalse(StationFallbackRoutes.anyRouteApplies(StationAsset.Fallback.of(null, null, null), "Fixture_Sword", null));
        assertNull(StationFallbackRoutes.routeFor(null, "Fixture_Sword", null), "no Fallback group, no route");
    }

    // ==================== ONE row per pile: what selection hands the scan ====================

    @Test
    void offeredRows_handTheScanExactlyOneRow_theOldestFallingBackPiecesFirstRoute() {
        StationAsset.Fallback both = StationAsset.Fallback.of(gearFilter(),
                StationAsset.Fallback.CraftingShare.of(0.5), StationAsset.Fallback.EssenceOnly.of(null));
        Map<String, Integer> pile = new LinkedHashMap<>();
        pile.put("Fixture_Arrow", 3);
        pile.put("Fixture_Sword", 1);
        pile.put("Fixture_Axe", 1);
        Predicate<String> gear = id -> !id.equals("Fixture_Arrow");
        StationAsset.Conversion[] offered = StationFallbackRoutes.offeredRows(both, pile, gear,
                id -> ownRecipe(RecipeMaterial.item("Fixture_Bar", 2)));

        assertEquals(1, offered.length, "a full inventory must meet ONE row: the share row can only answer"
                + " RUNNABLE or NO_ROOM, never fall through to the essence-only row behind it");
        assertEquals(StationAsset.Conversion.CRAFTING_SHARE_TIER, offered[0].effectiveTier(),
                "both routes apply to the sword, and the share is the one offered");
        assertEquals("Fixture_Sword", offered[0].primaryInput().getItemId(),
                "the oldest counted piece the gear filter passes (the arrow is outside it)");
    }

    @Test
    void offeredRows_skipAPieceNoRouteAppliesTo_andOfferNothingWhenNoPieceFallsBack() {
        StationAsset.Fallback shareOnly = StationAsset.Fallback.of(null, StationAsset.Fallback.CraftingShare.of(0.5), null);
        Map<String, Integer> pile = new LinkedHashMap<>();
        pile.put("Fixture_Rock", 2);
        pile.put("Fixture_Sword", 0);
        pile.put("Fixture_Axe", 1);
        StationAsset.Conversion[] offered = StationFallbackRoutes.offeredRows(shareOnly, pile, id -> true,
                id -> id.equals("Fixture_Axe") ? ownRecipe(RecipeMaterial.item("Fixture_Bar", 4)) : null);
        assertEquals(1, offered.length);
        assertEquals("Fixture_Axe", offered[0].primaryInput().getItemId(),
                "no recipe for the rock, and a zeroed sword entry is not a piece");

        assertEquals(0, StationFallbackRoutes.offeredRows(shareOnly, pile, id -> true, id -> null).length,
                "no piece has a route: the scan is handed nothing");
        assertEquals(0, StationFallbackRoutes.offeredRows(null, pile, id -> true, id -> null).length);
        assertEquals(0, StationFallbackRoutes.offeredRows(shareOnly, null, id -> true, id -> null).length);
    }

    @Test
    void selectConversion_handsTheScanTheOfferedRowsUnchanged() throws IOException {
        // The live half of the NO_ROOM decision, pinned on the source: selection builds nothing of
        // its own around the offered rows, it passes the array straight to the runnable scan.
        String service = SourcePins.read("StationService.java");
        String select = SourcePins.methodBody(service, "ConversionCheck selectConversion(", "socketOverride");
        int offered = select.indexOf("fallbackRowsFor(recipe.getFallback(), claim, sockets, socketOverride)");
        int scan = select.indexOf("firstRunnableConversionFromCustody(claim, player, offered, sockets, socketOverride)");
        assertTrue(offered >= 0 && scan > offered, "the scan runs over the offered rows, after they are built");
        assertEquals(-1, select.indexOf("new StationAsset.Conversion[]"),
                "selection never wraps a row list of its own around the fallback");
        String rows = SourcePins.methodBody(service, "private static StationAsset.Conversion[] fallbackRowsFor(");
        assertTrue(rows.contains("return StationFallbackRoutes.offeredRows(fallback, claim.items(socketId),"),
                "the live adapter answers exactly what the pure helper offers");
    }

    // ==================== the metadata guard's policy ====================

    @Test
    void metadataGuard_acceptsOnlyDeclaredKeys_overTheLibrarysList() {
        // The declared list is the library's and never retracts, so these fixture keys are
        // unique to this test and the undeclared one is never declared anywhere.
        DisposableItemMetadata.declare("Fixture_Guard_Stamps", "Fixture_Guard_Display");
        assertTrue(StationMetadataGuard.acceptsReading(DisposableItemMetadata.undeclared(Set.of())),
                "a bare stack carries no metadata at all");
        assertTrue(StationMetadataGuard.acceptsReading(
                DisposableItemMetadata.undeclared(Set.of("Fixture_Guard_Stamps", "Fixture_Guard_Display"))),
                "every key some mod declared disposable");
        assertFalse(StationMetadataGuard.acceptsReading(
                DisposableItemMetadata.undeclared(Set.of("Fixture_Guard_Stamps", "Fixture_Guard_Captured_Mob"))),
                "one key nobody declared refuses the stack, whatever else it carries");
    }

    @Test
    void metadataGuard_refusesAnUnreadableStack() {
        assertFalse(StationMetadataGuard.acceptsReading(null), "an unreadable stack is refused, never accepted by accident");
        assertFalse(StationMetadataGuard.accepts(null), "nothing to read is refused");
    }

    @Test
    void metadataGuard_readsOnlyThroughTheLibrary() throws IOException {
        // The guard is policy over the library's read and list: no engine metadata call of its
        // own, no private key list, no stamped-stack shortcut.
        String guard = SourcePins.read("StationMetadataGuard.java");
        assertTrue(guard.contains("ItemReadings.undeclaredMetadataKeys(stack)"), "the one read is the library's");
        for (String gone : new String[] {"getMetadata(", "StamperRegistry", "StackStats", "ItemDisplayMetadata",
                "public static void account("}) {
            assertEquals(-1, guard.indexOf(gone), "the guard no longer carries " + gone);
        }
    }
}
