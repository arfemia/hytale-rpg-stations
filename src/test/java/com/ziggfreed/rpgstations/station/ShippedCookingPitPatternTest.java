package com.ziggfreed.rpgstations.station;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.world.pattern.BlockReader;
import com.ziggfreed.common.world.pattern.CellPredicate;
import com.ziggfreed.common.world.pattern.PatternMatch;
import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;
import com.ziggfreed.rpgstations.station.PatternCatalog.CellOffset;
import com.ziggfreed.rpgstations.station.PatternCatalog.CompiledPattern;
import com.ziggfreed.rpgstations.station.PatternCells.CellMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The cooking-pit SHIPPED-CONTENT parity gate: decodes the pattern and station this jar actually
 * ships through their real codecs, compiles the pattern exactly the way the runtime catalog does
 * (the shipped station's own Block-socket offsets as the HOLD exclusion set), and walks the
 * compiled forms over a stub world - so a content edit that breaks activation, the pot's
 * headroom, the break re-walk, the vessel gate or the ruled recipe-row order can never leave the
 * build green. Identity resolvers are fixture maps; no assertion reads a tuning number (paces,
 * windows and quantities are exercised through the rows' own decoded values, never repeated
 * here).
 */
class ShippedCookingPitPatternTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path PATTERN_JSON = RESOURCES.resolve(
            Path.of("Server", "RpgStations", "Patterns", "CookingPit.json"));
    private static final Path STATION_JSON = RESOURCES.resolve(
            Path.of("Server", "RpgStations", "Stations", "CookingPit.json"));
    private static final Path ITEMS_DIR = RESOURCES.resolve(Path.of("Server", "Item", "Items"));

    private static final String ANCHOR_BLOCK = "Deco_Campfire_Off";
    private static final String STATION_BLOCK = "RPG_Station_CookingPit";
    private static final String POT_BLOCK = "RPG_Station_Cooking_Pot";
    private static final String RING_BLOCK = "Fixture_Cobble";

    /** The pure Ref lookup (this station authors no Refs). */
    private static final Function<String, ActionDef> NO_REFS = id -> null;

    // Ring identity: the fixture cobble carries the Rock family, exactly what the shipped cells
    // author; everything else answers no families. Meals/ingredients carry the stew vocabulary.
    private static final UnaryOperator<String> BASE_ID = UnaryOperator.identity();
    private static final Function<String, List<String>> BLOCK_FAMILIES = id ->
            RING_BLOCK.equals(id) ? List.of("Rock") : List.of();
    private static final Function<String, Map<String, String[]>> NO_TAGS = id -> Map.of();
    private static final CellPredicate<CellMatcher> PREDICATE =
            PatternCells.predicate(BASE_ID, BLOCK_FAMILIES, NO_TAGS);

    private static final Function<String, String[]> ITEM_FAMILIES = id -> switch (id) {
        case "Fixture_Meat" -> new String[] {"Meats"};
        case "Fixture_Veg" -> new String[] {"Vegetables"};
        default -> new String[0];
    };
    private static final Function<String, Map<String, String[]>> ITEM_TAGS = id -> Map.of();

    private static StructurePatternAsset pattern;
    private static StationAsset station;
    private static CompiledPattern compiled;

    @BeforeAll
    static void decodeShippedContent() throws IOException {
        pattern = StructurePatternAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(Files.readString(PATTERN_JSON, StandardCharsets.UTF_8)),
                null, info(StructurePatternAsset.class, "CookingPit"));
        station = StationAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(Files.readString(STATION_JSON, StandardCharsets.UTF_8)),
                null, info(StationAsset.class, "CookingPit"));
        // The SAME compile the runtime catalog runs: the shipped station's own Block-socket At
        // offsets are the HOLD exclusion set (here derived from the decoded asset directly, since
        // no live catalogs exist in a unit JVM).
        compiled = PatternCatalog.compile("cookingpit", pattern, blockSocketOffsets(station), "cookingpit");
    }

    private static AssetExtraInfo<String> info(Class<? extends JsonAsset<String>> assetClass, String key) {
        return new AssetExtraInfo<>(new AssetExtraInfo.Data(assetClass, key, null));
    }

    /** Every Block-route socket At offset across the station's actions, as anchor-relative cells. */
    private static Set<CellOffset> blockSocketOffsets(StationAsset asset) {
        Set<CellOffset> out = new HashSet<>();
        for (String actionId : ActionResolver.actionIds(asset)) {
            Custody custody = ActionResolver.resolve(asset, actionId, NO_REFS).getCustody();
            if (custody == null) {
                continue;
            }
            for (Custody.ResolvedSocket socket : custody.effectiveSockets()) {
                if (socket.blockRoute() && socket.blockAt() != null) {
                    out.add(new CellOffset(socket.blockAt().effectiveX(),
                            socket.blockAt().effectiveY(), socket.blockAt().effectiveZ()));
                }
            }
        }
        return out;
    }

    /** A tiny in-memory world: unset positions read as air ({@code "Empty"}). */
    private static final class StubWorld implements BlockReader {
        private final Map<String, String> blocks = new HashMap<>();

        StubWorld put(int x, int y, int z, String blockId) {
            blocks.put(x + ":" + y + ":" + z, blockId);
            return this;
        }

        StubWorld remove(int x, int y, int z) {
            blocks.remove(x + ":" + y + ":" + z);
            return this;
        }

        @Override
        public String blockItemIdAt(int x, int y, int z) {
            return blocks.getOrDefault(x + ":" + y + ":" + z, PatternCells.EMPTY_KEY);
        }
    }

    /** The shipped shape, complete, with the campfire anchor at (ax, ay, az). */
    private static StubWorld builtPitAt(int ax, int ay, int az) {
        StubWorld world = new StubWorld();
        StructurePatternAsset.Cell[] cells = pattern.getCells();
        StructurePatternAsset.Cell anchor = cells[pattern.anchorCellIndex()];
        for (StructurePatternAsset.Cell cell : cells) {
            if (cell.isEmptyCell()) {
                continue;
            }
            int x = ax + cell.offsetX() - anchor.offsetX();
            int y = ay + cell.offsetY() - anchor.offsetY();
            int z = az + cell.offsetZ() - anchor.offsetZ();
            String block = cell.getBlock().getItemId() != null ? cell.getBlock().getItemId() : RING_BLOCK;
            world.put(x, y, z, block);
        }
        return world;
    }

    // ==================== the shipped shape's structure ====================

    @Test
    void theAnchorCell_authorsTheExactCampfireId_soThePlacementIndexCanSeedIt() {
        assertEquals(ANCHOR_BLOCK, compiled.detect().payload(compiled.detect().anchorIndex()).itemId());
        assertEquals(STATION_BLOCK, pattern.getActivate().getBlock());
        assertEquals(ANCHOR_BLOCK, pattern.effectiveRevertBlock(),
                "a broken pit reverts to the plain unlit campfire");
    }

    @Test
    void aCompletedBuild_activates_inEveryRotation() {
        StubWorld world = builtPitAt(12, 64, -7);
        int variants = compiled.detect().variants().size();
        assertTrue(variants >= 4, "Yaw90 true compiles all four orientations");
        for (int v = 0; v < variants; v++) {
            PatternMatch<CellMatcher> match = compiled.detect().variants().get(v)
                    .matchFromCell(compiled.detect().anchorIndex(), 12, 64, -7, world, PREDICATE);
            assertNotNull(match, "variant " + v + " should match the (symmetric) shipped ring");
            assertEquals(12, match.anchorX());
            assertEquals(64, match.anchorY());
            assertEquals(-7, match.anchorZ());
        }
    }

    @Test
    void theVesselCell_isExcludedFromHold_soPlacingThePotNeverBreaksTheStandingPit() {
        // The Stew action's Vessel Block socket sits one cell above the station block; the
        // pattern authors that same cell Empty. HOLD must have dropped it.
        assertEquals(compiled.detect().cellCount() - 1, compiled.hold().cellCount(),
                "exactly the vessel headroom cell is excluded from the HOLD form");
        var variant = compiled.hold().variants().get(0);
        for (int c = 0; c < variant.cellCount(); c++) {
            assertFalse(variant.dx(c) == 0 && variant.dy(c) == 1 && variant.dz(c) == 0,
                    "no HOLD cell may test the vessel cell above the anchor");
        }

        // And the walk agrees: the activated pit with the POT mounted still holds.
        StubWorld world = builtPitAt(0, 64, 0)
                .put(0, 64, 0, STATION_BLOCK)
                .put(0, 65, 0, POT_BLOCK);
        assertNotNull(compiled.hold().variants().get(0)
                        .matchFromCell(compiled.hold().anchorIndex(), 0, 64, 0, world, PREDICATE),
                "the standing pit re-walk must not read the mounted pot as a break");
    }

    @Test
    void breakingOneRingMember_failsTheHoldWalk() {
        StubWorld world = builtPitAt(0, 64, 0)
                .put(0, 64, 0, STATION_BLOCK)
                .remove(1, 64, 0);
        for (var variant : compiled.hold().variants()) {
            assertNull(variant.matchFromCell(compiled.hold().anchorIndex(), 0, 64, 0, world, PREDICATE),
                    "a broken ring member must fail HOLD in every orientation, so the revert fires");
        }
    }

    @Test
    void aSameIdBlockOutsideThePattern_neverBreaksOrActivatesAnything() {
        StubWorld world = builtPitAt(0, 64, 0)
                .put(0, 64, 0, STATION_BLOCK)
                .put(5, 64, 5, ANCHOR_BLOCK);
        assertNotNull(compiled.hold().variants().get(0)
                        .matchFromCell(compiled.hold().anchorIndex(), 0, 64, 0, world, PREDICATE),
                "an unrelated campfire nearby is not part of the standing shape");
        for (var variant : compiled.detect().variants()) {
            assertNull(variant.matchFromCell(compiled.detect().anchorIndex(), 5, 64, 5, world, PREDICATE),
                    "the lone campfire has no ring, so it must not read as a completed build");
        }
    }

    // ==================== the vessel gate (rpgstations:socket_filled) ====================

    @Test
    void theStewGate_answersOneWithThePot_zeroWithout_andSelectionLayersTheTwoActions() {
        FactorRegistryImpl.getInstance().registerBuiltins();
        List<Custody.ResolvedSocket> stewSockets =
                ActionResolver.resolve(station, "Stew", NO_REFS).getCustody().effectiveSockets();

        Map<String, Boolean> withPot = new LinkedHashMap<>();
        StationService.socketsFilledInto(withPot, stewSockets, null, socket -> true);
        Map<String, Boolean> withoutPot = new LinkedHashMap<>();
        StationService.socketsFilledInto(withoutPot, stewSockets, null, socket -> false);

        assertEquals(1.0, FactorRegistryImpl.getInstance()
                        .resolve("rpgstations:socket_filled", "Vessel", ctx(withPot)),
                "the mounted pot answers 1 through the registry, Param case-insensitive");
        assertEquals(0.0, FactorRegistryImpl.getInstance()
                        .resolve("rpgstations:socket_filled", "Vessel", ctx(withoutPot)),
                "a bare pit answers 0");

        // The two shipped actions gate on the SAME reading in opposite directions: with the pot
        // the press selects Stew, without it Grill - the layering that makes one block carry both.
        List<String> candidates = ActionResolver.selectActionsByFamily(station, "Fixture_Meat",
                new String[] {"Meats"}, Map.of("Type", new String[] {"Food"}), null);
        assertEquals(List.of("Grill", "Stew"), candidates,
                "raw meat matches both Selects, in authored order");
        assertEquals("Grill", firstGatePassing(candidates, withoutPot));
        assertEquals("Stew", firstGatePassing(candidates, withPot));
    }

    /** The engage walk's selection rule, over the real registry + the shipped Requires blocks. */
    private static String firstGatePassing(List<String> candidates, Map<String, Boolean> socketsFilled) {
        for (String actionId : candidates) {
            var action = ActionResolver.resolve(station, actionId, NO_REFS);
            FactorCondition[] conditions =
                    action.getRequires() != null ? action.getRequires().getConditions() : null;
            if (FactorRegistryImpl.getInstance().firstFailedCondition(conditions, ctx(socketsFilled)) == null) {
                return actionId;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static FactorContext ctx(Map<String, Boolean> socketsFilled) {
        return FactorContext.builder()
                .playerId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .stationId("cookingpit")
                .actionId("Stew")
                .socketsFilled(socketsFilled)
                .build();
    }

    // ==================== the ruled recipe-row order over fixture piles ====================

    /** A claim whose ingredients pile holds exactly {@code row}'s own decoded inputs. */
    private static StationCustodyClaim claimHolding(StationAsset.Conversion row) {
        StationCustodyClaim claim = new StationCustodyClaim(UUID.randomUUID(), "cookingpit", "stew", 0, 64, 0);
        for (Ingredient in : row.getInput()) {
            claim.addTo(socketOf(in), claim.ownerId, fixtureFor(in), in.effectiveQuantity());
        }
        claim.setUnattendedLastGameTime(0L);
        return claim;
    }

    private static String socketOf(Ingredient in) {
        return in.getSocket() != null ? in.getSocket().toLowerCase(Locale.ROOT) : "ingredients";
    }

    /** A fixture item satisfying one decoded input entry (fails loudly on a vocabulary this test does not know). */
    private static String fixtureFor(Ingredient in) {
        if (in.isMatchAny()) {
            return "Fixture_Odd_Bit";
        }
        String family = in.getResourceTypeId();
        if ("Meats".equalsIgnoreCase(family)) {
            return "Fixture_Meat";
        }
        if ("Vegetables".equalsIgnoreCase(family)) {
            return "Fixture_Veg";
        }
        return fail("shipped stew row names a route this test has no fixture for: " + family);
    }

    private static StationUnattended.Settle settleOver(StationCustodyClaim claim) {
        var stew = ActionResolver.resolve(station, "Stew", NO_REFS);
        Custody custody = stew.getCustody();
        return StationUnattended.settle(claim, custody.effectiveSockets(),
                stew.getRecipe().getConversions(), stew.getRecipe().getYield(),
                custody.effectiveMaxQuantity(),
                stew.getWork().getUnattended(), 6000L, 600_000L, ITEM_FAMILIES, ITEM_TAGS);
    }

    @Test
    void theExactKebabSet_beatsTheMatchAllStew_theRuledRowOrder() {
        StationAsset.Conversion[] rows =
                ActionResolver.resolve(station, "Stew", NO_REFS).getRecipe().getConversions();
        StationCustodyClaim claim = claimHolding(rows[0]);

        StationUnattended.Settle settle = settleOver(claim);

        assertTrue(settle.transformed());
        assertEquals(0, settle.conversionIndex(),
                "a pile satisfying the FIRST authored exact-set row runs it, not the later match-all");
        assertEquals(rows[0].primaryOutput().getItemId(),
                claim.items(settle.produceSocketId()).keySet().iterator().next());
    }

    @Test
    void theExactVegetableSet_selectsTheSecondRow() {
        StationAsset.Conversion[] rows =
                ActionResolver.resolve(station, "Stew", NO_REFS).getRecipe().getConversions();
        StationCustodyClaim claim = claimHolding(rows[1]);

        StationUnattended.Settle settle = settleOver(claim);

        assertTrue(settle.transformed());
        assertEquals(1, settle.conversionIndex());
        assertEquals(rows[1].primaryOutput().getItemId(),
                claim.items(settle.produceSocketId()).keySet().iterator().next());
    }

    @Test
    void anUnsortedPile_fallsThroughToTheMatchAllStewRow() {
        StationAsset.Conversion[] rows =
                ActionResolver.resolve(station, "Stew", NO_REFS).getRecipe().getConversions();
        // The kebab set PLUS one odd bit: the exact-set rows refuse the contaminated pile and the
        // route-less match-all row cooks it - the "anything else becomes stew" reading.
        StationCustodyClaim claim = claimHolding(rows[0]);
        claim.addTo("ingredients", claim.ownerId, "Fixture_Odd_Bit", 1);

        StationUnattended.Settle settle = settleOver(claim);

        assertTrue(settle.transformed());
        assertEquals(rows.length - 1, settle.conversionIndex(),
                "the LAST authored row is the match-all catch-all");
        assertEquals(rows[rows.length - 1].primaryOutput().getItemId(),
                claim.items(settle.produceSocketId()).keySet().iterator().next());
    }

    // ==================== cross-file wiring ====================

    @Test
    void theShippedFiles_referenceEachOther_byTheIdsTheyActuallyShip() throws IOException {
        // The vessel socket wants exactly the pot block this jar ships.
        List<Custody.ResolvedSocket> stewSockets =
                ActionResolver.resolve(station, "Stew", NO_REFS).getCustody().effectiveSockets();
        Custody.ResolvedSocket vessel = stewSockets.stream()
                .filter(Custody.ResolvedSocket::blockRoute).findFirst().orElseThrow();
        assertTrue(vessel.required(), "the vessel socket is Required - work needs the pot standing");
        assertEquals(POT_BLOCK, vessel.match().getItemId());
        assertTrue(Files.exists(ITEMS_DIR.resolve(POT_BLOCK + ".json")));

        // The station block ships, names its Use chain, and defines every state the actions map.
        Path pitItem = ITEMS_DIR.resolve(STATION_BLOCK + ".json");
        assertTrue(Files.exists(pitItem));
        String pitBody = Files.readString(pitItem, StandardCharsets.UTF_8);
        assertTrue(pitBody.contains("\"Use\": \"RPG_Station_CookingPit_Use\""));
        for (String actionId : ActionResolver.actionIds(station)) {
            Custody custody = ActionResolver.resolve(station, actionId, NO_REFS).getCustody();
            Custody.States states = custody != null ? custody.getStates() : null;
            assertNotNull(states, "both pit actions map custody states onto the block");
            for (String state : new String[] {states.getLoaded(), states.getWorking(),
                    states.getReady(), states.getOverdone()}) {
                assertNotNull(state);
                assertTrue(pitBody.contains("\"" + state + "\""),
                        "the pit block must define state '" + state + "'");
            }
        }

        // The Use RootInteraction routes to this station id.
        String useBody = Files.readString(RESOURCES.resolve(
                Path.of("Server", "Item", "RootInteractions", "RPG_Station_CookingPit_Use.json")),
                StandardCharsets.UTF_8);
        assertTrue(useBody.contains("\"Station\": \"cookingpit\""));

        // The match-all row's meal ships beside them.
        assertTrue(Files.exists(ITEMS_DIR.resolve("RPG_Food_Hearty_Stew.json")));
    }
}
