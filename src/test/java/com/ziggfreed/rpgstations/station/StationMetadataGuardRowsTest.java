package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.inventory.DisposableItemMetadata;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The metadata guard on EVERY row that consumes a single-item socket's real stack, not only the
 * fallback routes: a derived salvage row and an authored row refuse a piece carrying a metadata key
 * nobody declared disposable and take one carrying only declared keys
 * ({@link StationMetadataGuard#consumesRefusedPiece}, over the library's declared-key list), the
 * attended custody scan and the unattended settle both skip such a row, an authored
 * {@code Consume} phase drawing from custody refuses it too, and the ritual queue passes over a
 * socket holding one. A single-item socket refuses the piece at placement
 * ({@link StationService#socketAccepts}) whenever every action at the station that reads the socket
 * would consume it ({@link StationMetadataGuard#socketUse}), and takes it when one reads it without
 * consuming it (a {@code Stamp}); a count pile keeps its own refusal of metadata. The fixtures are
 * authored here, except the Greater Disenchanting Table's shipped ritual, read for its socket and
 * program shape alone; the declared keys are unique to this test, since the library's list never
 * retracts.
 */
class StationMetadataGuardRowsTest {

    private static final String DECLARED = "Fixture_Rows_Guard_Stamps";
    private static final String UNDECLARED = "Fixture_Rows_Guard_Foreign_Data";

    private static final Function<String, String[]> NO_FAMILIES = id -> new String[0];
    private static final Function<String, Map<String, String[]>> NO_TAGS = id -> Map.of();

    private static Custody.ResolvedSocket itemSocket(String id, int maxQuantity) {
        return new Custody.ResolvedSocket(id, true, null, null, null, maxQuantity,
                false, false, null, false, false, false, null);
    }

    private static final List<Custody.ResolvedSocket> ONE_SOCKET = List.of(itemSocket("main", 1));

    /** A derived salvage row: the sword comes apart into bars, stamped as the deriver stamps it. */
    private static StationAsset.Conversion derivedSalvageRow() {
        return StationAsset.Conversion.derivedRow(new Ingredient[] {Ingredient.item("Fixture_Sword", 1)},
                new Ingredient[] {Ingredient.item("Fixture_Bar", 2)}, null, null);
    }

    /** The same row, authored by hand. */
    private static StationAsset.Conversion authoredRow() {
        return StationAsset.Conversion.of(Ingredient.item("Fixture_Sword", 1), Ingredient.item("Fixture_Bar", 2));
    }

    /** The main socket holds a real sword stack carrying {@code keys} (null = unreadable). */
    private static Function<String, String> swordCarrying(Set<String> keys) {
        DisposableItemMetadata.declare(DECLARED);
        return StationMetadataGuard.refusedPieces(socketId -> "main".equals(socketId) ? "Fixture_Sword" : null,
                socketId -> keys == null ? null : DisposableItemMetadata.undeclared(keys));
    }

    private static boolean refuses(StationAsset.Conversion row, Function<String, String> refusedPieceOf) {
        return StationMetadataGuard.consumesRefusedPiece(row, ONE_SOCKET, null, refusedPieceOf, NO_FAMILIES, NO_TAGS);
    }

    @Test
    void aDerivedSalvageRow_refusesAnUndeclaredKey_andTakesOnlyDeclaredKeys() {
        assertTrue(derivedSalvageRow().isDerived());
        assertTrue(refuses(derivedSalvageRow(), swordCarrying(Set.of(DECLARED, UNDECLARED))),
                "one key nobody declared refuses the row, whatever else the stack carries");
        assertFalse(refuses(derivedSalvageRow(), swordCarrying(Set.of(DECLARED))),
                "a stack carrying only declared keys is consumed as any other");
        assertFalse(refuses(derivedSalvageRow(), swordCarrying(Set.of())), "a bare stack carries nothing");
        assertTrue(refuses(derivedSalvageRow(), swordCarrying(null)), "an unreadable stack is refused");
    }

    @Test
    void anAuthoredRow_isJudgedTheSameWay() {
        assertFalse(authoredRow().isDerived());
        assertTrue(refuses(authoredRow(), swordCarrying(Set.of(UNDECLARED))));
        assertFalse(refuses(authoredRow(), swordCarrying(Set.of(DECLARED))));
    }

    @Test
    void onlyARowThatTakesThePiece_isRefused() {
        Function<String, String> refused = swordCarrying(Set.of(UNDECLARED));
        StationAsset.Conversion otherPiece = StationAsset.Conversion.of(Ingredient.item("Fixture_Axe", 1),
                Ingredient.item("Fixture_Bar", 1));
        assertFalse(refuses(otherPiece, refused), "a row drawing some other item never consumes the sword");
        assertFalse(refuses(derivedSalvageRow(), socketId -> null),
                "no real stack (a count pile, an empty socket): nothing to guard");
        assertFalse(refuses(null, refused));
    }

    @Test
    void theQueuesCurrentSocket_isTheOneTheRowDrawsFrom() {
        List<Custody.ResolvedSocket> two = List.of(itemSocket("slot_a", 1), itemSocket("slot_b", 1));
        Function<String, String> swordInB = socketId -> "slot_b".equals(socketId) ? "Fixture_Sword" : null;
        assertTrue(StationMetadataGuard.consumesRefusedPiece(derivedSalvageRow(), two, "slot_b", swordInB,
                NO_FAMILIES, NO_TAGS), "the ritual queue addresses slot_b, which holds the refused piece");
        assertFalse(StationMetadataGuard.consumesRefusedPiece(derivedSalvageRow(), two, null, swordInB,
                NO_FAMILIES, NO_TAGS), "the classic addressing draws from slot_a, which holds nothing refused");
    }

    @Test
    void theUnattendedSettle_skipsARowThatWouldConsumeARefusedPiece() {
        StationCustodyClaim refusedClaim = new StationCustodyClaim(UUID.randomUUID(), "fixture_table", "unmake", 0, 64, 0);
        refusedClaim.addTo("main", refusedClaim.ownerId, "Fixture_Sword", 1);
        refusedClaim.setUnattendedLastGameTime(0L);
        StationUnattended.Settle refused = StationUnattended.settle(refusedClaim,
                List.of(itemSocket("main", 1), itemSocket("output", 100)), new StationAsset.Conversion[] {
                        StationAsset.Conversion.of(Ingredient.of("Fixture_Sword", null, 1, "main"),
                                Ingredient.of("Fixture_Bar", null, 2, "output"))},
                null, 101, StationAsset.Work.Unattended.of(null, null, null), 5_000L, 10_000L,
                NO_FAMILIES, NO_TAGS, socketId -> "main".equals(socketId) ? "Fixture_Sword" : null);
        assertFalse(refused.transformed(), "nobody being there never destroys what a worker could not");
        assertEquals(1, refusedClaim.totalQuantity("main"), "the piece stays where it was placed");

        StationCustodyClaim acceptedClaim = new StationCustodyClaim(UUID.randomUUID(), "fixture_table", "unmake", 0, 64, 0);
        acceptedClaim.addTo("main", acceptedClaim.ownerId, "Fixture_Sword", 1);
        acceptedClaim.setUnattendedLastGameTime(0L);
        StationUnattended.Settle accepted = StationUnattended.settle(acceptedClaim,
                List.of(itemSocket("main", 1), itemSocket("output", 100)), new StationAsset.Conversion[] {
                        StationAsset.Conversion.of(Ingredient.of("Fixture_Sword", null, 1, "main"),
                                Ingredient.of("Fixture_Bar", null, 2, "output"))},
                null, 101, StationAsset.Work.Unattended.of(null, null, null), 5_000L, 10_000L,
                NO_FAMILIES, NO_TAGS, socketId -> null);
        assertTrue(accepted.transformed());
        assertEquals(0, acceptedClaim.totalQuantity("main"));
    }

    @Test
    void aSingleItemSocketsDerivedPlacement_asksTheGuard_aCountPileKeepsItsOwnRule() {
        StationAsset.Conversion[] rows = {derivedSalvageRow()};
        Custody.ResolvedSocket single = Custody.of(1, null, null).effectiveSockets().get(0);
        assertFalse(StationService.socketAccepts(single, () -> true, () -> false, () -> rows, () -> true,
                "Fixture_Sword", null, null, null, null),
                "a piece the guard refuses is refused at placement, before the rows or the fallback");
        assertTrue(StationService.socketAccepts(single, () -> true, () -> true, () -> rows, () -> false,
                "Fixture_Sword", null, null, null, null), "a piece the guard accepts places as a row derives it");

        Custody.ResolvedSocket pile = Custody.of(100, null, null).effectiveSockets().get(0);
        assertTrue(StationService.socketAccepts(pile, () -> false, () -> false, () -> rows, () -> false,
                "Fixture_Sword", null, null, null, null),
                "a count pile never holds a real stack: its own instance-data rule decides, not the guard");
        assertFalse(StationService.socketAccepts(pile, () -> true, () -> true, () -> rows, () -> false,
                "Fixture_Sword", null, null, null, null), "a count pile refuses any per-instance data");
    }

    @Test
    void theAttendedScan_andTheUnattendedSettle_bothAskTheGuardOverTheLiveClaim() throws IOException {
        String service = SourcePins.read("StationService.java");
        String scan = SourcePins.methodBody(service, "private ConversionCheck firstRunnableConversionFromCustody(");
        assertTrue(scan.contains("StationMetadataGuard.refusedPieces(claim)"));
        assertTrue(scan.contains("StationMetadataGuard.consumesRefusedPiece(c, sockets, socketOverride, refusedPieceOf,"),
                "every row the custody scan is handed meets the guard: authored, derived and fallback alike");
        String settle = SourcePins.methodBody(service, "private void settleUnattendedAt(");
        assertTrue(settle.contains("StationMetadataGuard.refusedPieces(claim)"),
                "the live settle hands the guard's reading to the unattended scan");
    }

    // ==================== placement: refused up front where every reader consumes ====================

    private static final Map<String, String[]> WEAPON = Map.of("Type", new String[] {"Weapon"});

    /** One single-item socket matching {@code match} (an explicit Match, or null for a bare catch-all custody). */
    private static Custody.ResolvedSocket singleSocket(ActionInput match) {
        return Custody.of(1, match, null).effectiveSockets().get(0);
    }

    private static boolean places(Custody.ResolvedSocket socket, boolean guardAccepts, boolean everyReaderConsumes) {
        return StationService.socketAccepts(socket, () -> true, () -> guardAccepts, () -> everyReaderConsumes,
                () -> new StationAsset.Conversion[] {derivedSalvageRow()}, () -> true, "Fixture_Sword", null,
                WEAPON, "Weapon", null);
    }

    @Test
    void anExplicitMatchOrACatchAll_refusesAGuardedPiece_whenEveryReaderWouldConsumeIt() {
        Custody.ResolvedSocket explicit = singleSocket(ActionInput.of(null, null, WEAPON, null));
        Custody.ResolvedSocket catchAll = singleSocket(ActionInput.of(null, null, null, null));
        for (Custody.ResolvedSocket socket : List.of(explicit, catchAll)) {
            assertFalse(places(socket, false, true), "every reader consumes: refused at placement");
            assertTrue(places(socket, false, false),
                    "a reader that keeps the piece (a Stamp): taken, and the consuming paths refuse it themselves");
            assertTrue(places(socket, true, true), "a piece the guard accepts places as ever");
        }
        Custody.ResolvedSocket pile = Custody.of(100, ActionInput.of(null, null, WEAPON, null), null)
                .effectiveSockets().get(0);
        assertTrue(StationService.socketAccepts(pile, () -> false, () -> false, () -> true,
                () -> new StationAsset.Conversion[0], () -> false, "Fixture_Sword", null, WEAPON, "Weapon", null),
                "a count pile never reaches the guard: it holds no real stack");
    }

    @Test
    void howAnActionReadsASocket_consumingStampingOrJustHolding() {
        List<Custody.ResolvedSocket> main = ONE_SOCKET;
        StationAsset.Conversion[] rows = {authoredRow()};
        assertEquals(StationMetadataGuard.SocketUse.CONSUMES,
                StationMetadataGuard.socketUse("main", main, false, rows, false, List.of()),
                "the implicit convert loop draws the first Item socket");
        assertEquals(StationMetadataGuard.SocketUse.CONSUMES,
                StationMetadataGuard.socketUse("main", main, false, null, true, List.of()),
                "a fallback route draws it the same way");
        assertEquals(StationMetadataGuard.SocketUse.KEEPS,
                StationMetadataGuard.socketUse("main", main, false, null, false, List.of()),
                "custody no phase draws from shows the piece and never spends it");
        assertEquals(StationMetadataGuard.SocketUse.KEEPS, StationMetadataGuard.socketUse("main", main, false, rows,
                false, List.of(StationStep.of("strike").withStamp(StationStep.Stamp.of(
                        new Ingredient[] {Ingredient.item("Fixture_Dust", 1)}, null, null)))),
                "a Stamp reads the main piece and keeps it");
        assertEquals(StationMetadataGuard.SocketUse.KEEPS, StationMetadataGuard.socketUse("main", main, false, rows,
                false, List.of(StationStep.of("wait"))), "an authored program with no Convert never runs the rows");
        assertEquals(StationMetadataGuard.SocketUse.CONSUMES, StationMetadataGuard.socketUse("main", main, false,
                null, false, List.of(StationStep.of("take").withConsume(StationStep.Consume.of(
                        new Ingredient[] {Ingredient.item("Fixture_Sword", 1)}, StationStep.Consume.FROM_CUSTODY)))),
                "an authored Consume phase from custody draws the socket its entries address");
        assertEquals(StationMetadataGuard.SocketUse.NONE,
                StationMetadataGuard.socketUse("vessel", main, false, rows, false, List.of()));

        assertTrue(StationMetadataGuard.consumedByEveryReader(List.of(StationMetadataGuard.SocketUse.CONSUMES,
                StationMetadataGuard.SocketUse.NONE)));
        assertFalse(StationMetadataGuard.consumedByEveryReader(List.of(StationMetadataGuard.SocketUse.CONSUMES,
                StationMetadataGuard.SocketUse.KEEPS)), "one action keeping the piece is enough to take it");
        assertFalse(StationMetadataGuard.consumedByEveryReader(List.of(StationMetadataGuard.SocketUse.NONE)),
                "nobody reads the socket: nothing to refuse for");
    }

    /** The shipped ritual and the greater table's child action over it, decoded the way the store inherits it. */
    private static ActionDef greaterRitual() throws Exception {
        Path actions = Path.of("src", "main", "resources", "Server", "RpgStations", "Actions");
        ActionAsset base = ActionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                Files.readString(actions.resolve("Disenchant.json"), StandardCharsets.UTF_8)), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ActionAsset.class, "Disenchant", null)));
        return ActionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                Files.readString(actions.resolve("Disenchant_Greater.json"), StandardCharsets.UTF_8)), base,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ActionAsset.class, "Disenchant_Greater", "Disenchant")))
                .getBody();
    }

    @Test
    void theGreaterTablesThreeSockets_refuseAGuardedPieceAtPlacement() throws Exception {
        ActionDef ritual = greaterRitual();
        List<Custody.ResolvedSocket> sockets = ritual.getCustody().effectiveSockets();
        assertEquals(3, sockets.size());
        boolean queue = ritual.getWork() != null && ritual.getWork().effectiveQueue();
        assertTrue(queue, "the greater table works its sockets one ritual after another");
        boolean fallback = ritual.getRecipe() != null && ritual.getRecipe().getFallback() != null;
        List<StationStep> steps = Arrays.asList(ritual.getSteps());
        for (Custody.ResolvedSocket socket : sockets) {
            assertEquals(1, socket.maxQuantity(), socket.id() + " holds one real piece");
            assertTrue(socket.match() != null && socket.match().hasExcept(), socket.id() + " authors its Except holes");
            StationMetadataGuard.SocketUse use = StationMetadataGuard.socketUse(socket.id(), sockets, queue,
                    new StationAsset.Conversion[0], fallback, steps);
            assertEquals(StationMetadataGuard.SocketUse.CONSUMES, use, socket.id() + ": the ritual consumes its piece");
            boolean everyReader = StationMetadataGuard.consumedByEveryReader(List.of(use));
            assertFalse(StationService.socketAccepts(socket, () -> false, () -> false, () -> everyReader,
                    () -> new StationAsset.Conversion[0], () -> true, "Fixture_Sword", null, WEAPON, "Weapon",
                    null), socket.id() + " refuses a guarded piece at placement");
            assertTrue(StationService.socketAccepts(socket, () -> false, () -> true, () -> everyReader,
                    () -> new StationAsset.Conversion[0], () -> true, "Fixture_Sword", null, WEAPON, "Weapon",
                    null), socket.id() + " takes the same piece once nothing undeclared rides on it");
        }
    }

    @Test
    void aGuardedPieceAlreadyInOneGreaterSocket_neverStallsTheQueue() throws Exception {
        List<Custody.ResolvedSocket> sockets = greaterRitual().getCustody().effectiveSockets();
        String first = sockets.get(0).id();
        String second = sockets.get(1).id();
        String third = sockets.get(2).id();
        Function<String, String> guardedInFirst = socketId -> first.equals(socketId) ? "Fixture_Sword" : null;
        Predicate<String> workable = StationMetadataGuard.workable(socketId -> true, guardedInFirst);
        assertEquals(second, StationCustody.nextFilledSocket(sockets, workable),
                "the queue passes over the guarded piece and works the next socket");
        assertFalse(StationMetadataGuard.consumesRefusedPiece(derivedSalvageRow(), sockets, second, guardedInFirst,
                NO_FAMILIES, NO_TAGS), "the pass the queue addresses to the next socket has a runnable row");
        assertEquals(third, StationCustody.nextFilledSocket(sockets,
                StationMetadataGuard.workable(third::equals, guardedInFirst)), "and the one after it");
        assertNull(StationCustody.nextFilledSocket(sockets,
                StationMetadataGuard.workable(first::equals, guardedInFirst)),
                "only the guarded piece left: the queue is done rather than stopped on it");
    }

    @Test
    void anAuthoredConsumePhase_fromCustody_meetsTheGuard() throws IOException {
        List<Custody.ResolvedSocket> two = List.of(itemSocket("slot_a", 1), itemSocket("slot_b", 1));
        Function<String, String> swordInB = socketId -> "slot_b".equals(socketId) ? "Fixture_Sword" : null;
        Ingredient[] items = {Ingredient.item("Fixture_Sword", 1)};
        assertTrue(StationMetadataGuard.consumesRefusedPiece(items, "slot_b", two, swordInB, NO_FAMILIES, NO_TAGS),
                "the phase's own Socket addresses the guarded piece");
        assertFalse(StationMetadataGuard.consumesRefusedPiece(items, null, two, swordInB, NO_FAMILIES, NO_TAGS),
                "unaddressed, the phase draws slot_a, which holds nothing guarded");
        assertTrue(StationMetadataGuard.consumesRefusedPiece(new Ingredient[] {Ingredient.of("Fixture_Sword", null,
                1, "slot_b")}, "slot_a", two, swordInB, NO_FAMILIES, NO_TAGS), "an entry's own Socket wins");

        String handlers = SourcePins.read("StationStepHandlers.java");
        String consume = SourcePins.methodBody(handlers, "private static StationStepResult consumeFromCustody(");
        int guard = consume.indexOf("StationMetadataGuard.consumesRefusedPiece(items, groupSocket, sockets,");
        assertTrue(guard >= 0, "the custody drain asks the guard over the phase's own entries");
        assertTrue(consume.indexOf("StationService.shortInputStopReason(repeating)", guard) > guard,
                "and answers as an unrunnable row does");
        assertTrue(guard < consume.indexOf("StationCustody.drainFromPile("), "before anything drains");
    }
}
