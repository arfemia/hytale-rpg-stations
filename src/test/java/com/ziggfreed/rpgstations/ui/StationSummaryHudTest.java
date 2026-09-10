package com.ziggfreed.rpgstations.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.ui.hud.card.HudCardAsset;
import com.ziggfreed.common.ui.hud.card.HudCardConfig;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.station.SettingsCatalog;

/**
 * The things about the summary panel that a unit JVM can hold and the client cannot forgive: the
 * row slots the document actually declares, the cap the panel is allowed to draw up to, and the
 * colour the card is drawn in (this mod's own leaf over the look every HUD card shares).
 *
 * <p>A HUD update only ever repaints elements the document already declares, and a command written
 * against a selector it does NOT declare crashes the client outright. So the slot run in the
 * {@code .ui} and {@link StationSummaryHud#MAX_LEDGER_ROWS} have to move together, and this is what
 * says so before a player finds out.
 */
class StationSummaryHudTest {

    private static final Path UI_FILE =
            Path.of("src/main/resources/Common/UI/Custom/Pages/RpgStationSummary.ui");

    @AfterEach
    void resetSettings() {
        SettingsCatalog.getInstance().fold(Map.of(), true);
        HudCardConfig.getInstance().mergePackLayer(Map.of());
        HudCardConfig.getInstance().mergeOwnerLayer(Map.of());
    }

    // ==================== the card colour ====================

    @Test
    void theCardWearsTheSharedLookUnlessTheSettingsStateOneOfTheirOwn() throws Exception {
        assertNull(StationSummaryHud.cardLook().cardColor(),
                "no record and no setting: the shipped look, and nothing is pushed");

        shareCardLook("#ffffffb8");
        assertEquals("#ffffffb8", StationSummaryHud.cardLook().cardColor(), "the shared record dims this card too");

        authorSummaryHud("{ \"Color\": \"#112233\" }");
        assertEquals("#112233", StationSummaryHud.cardLook().cardColor(),
                "this panel's own leaf over the shared record");

        authorSummaryHud("{ \"Color\": \"#not-a-colour\" }");
        assertEquals("#ffffffb8", StationSummaryHud.cardLook().cardColor(),
                "a value that is not a hex is ignored, and the shared record stands");

        shareCardLook("#ffffffff");
        authorSummaryHud("{ \"Enabled\": true }");
        assertNull(StationSummaryHud.cardLook().cardColor(), "the identity everywhere: nothing pushed");
    }

    @Test
    void theColourIsPushedOnTheElementWearingTheFrame() throws IOException {
        String ui = Files.readString(UI_FILE, StandardCharsets.UTF_8);
        assertTrue(ui.contains("Group " + StationSummaryHud.ROOT_SELECTOR + " {"),
                "the root the frozen contract names is still declared");
        assertTrue(ui.contains("#Content {"),
                "and the shared frame's #Content inside it, which is what the card colour retints");
        assertEquals(StationSummaryHud.ROOT_SELECTOR + " #Content", StationSummaryHud.FRAME_SEL);
    }

    /** The shared record every HUD card reads, as the library's own file would state it. */
    private static void shareCardLook(String color) throws IOException {
        HudCardAsset card = HudCardAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Color\": \"" + color + "\" }"), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(HudCardAsset.class, HudCardAsset.SHARED_ID, null)));
        HudCardConfig.getInstance().mergePackLayer(Map.of(HudCardAsset.SHARED_ID, card));
    }

    private static void authorSummaryHud(String body) throws Exception {
        RpgStationsSettingsAsset settings = RpgStationsSettingsAsset.CODEC.decodeJson(
                RawJsonReader.fromJsonString("{ \"SummaryHud\": " + body + " }"),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(
                        RpgStationsSettingsAsset.class, RpgStationsSettingsAsset.ID, null)));
        SettingsCatalog.getInstance().fold(Map.of(RpgStationsSettingsAsset.ID, settings), true);
    }

    // ==================== the slot contract ====================

    @Test
    void theDocumentDeclaresExactlyTheRowSlotsTheHudPaints() throws IOException {
        String ui = Files.readString(UI_FILE, StandardCharsets.UTF_8);

        for (int i = 0; i < StationSummaryHud.MAX_LEDGER_ROWS; i++) {
            assertTrue(ui.contains("Group #RpgStationSummaryItem" + i + " {"),
                    "the panel paints row slot " + i + ", so the document has to declare it");
        }
        assertFalse(ui.contains("Group #RpgStationSummaryItem" + StationSummaryHud.MAX_LEDGER_ROWS + " {"),
                "a slot past the ceiling is never painted, so it would only ever sit there empty");
    }

    /**
     * Every slot carries the three children the shared row renderer addresses on a visible row. A
     * slot missing one of them is not a blank row, it is a crash the moment that row is used.
     *
     * <p>Counted over the ledger group alone: the panel's crest carries an icon of its own further
     * up, and it is not a row.
     */
    @Test
    void everyRowSlotCarriesTheChildrenTheRendererAddresses() throws IOException {
        String ledger = ledgerBlock();

        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, count(ledger, "ItemGrid #Icon {"),
                "one icon per row slot");
        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, count(ledger, "Label #Name {"),
                "one headline per row slot");
        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, count(ledger, "Label #Sub {"),
                "one second line per row slot, hidden until a row has something extra to say");
    }

    /**
     * The document from the ledger group down to the overflow row: everything the row renderer ever
     * addresses, and nothing the panel draws above it.
     */
    private static String ledgerBlock() throws IOException {
        String ui = Files.readString(UI_FILE, StandardCharsets.UTF_8);
        int from = ui.indexOf("Group #RpgStationSummaryLedger {");
        int to = ui.indexOf("Label #RpgStationSummaryItemMore {");
        assertTrue(from >= 0 && to > from, "the document still declares a ledger group and an overflow row");
        return ui.substring(from, to);
    }

    // ==================== the cap ====================

    @Test
    void nothingAuthoredDrawsUpToTheCeiling() {
        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, StationSummaryHud.ledgerRowCap());
    }

    @Test
    void anAuthoredCapDrawsFewerRows() throws Exception {
        authorMaxRows("4");

        assertEquals(4, StationSummaryHud.ledgerRowCap());
    }

    /**
     * A number past the ceiling buys nothing: the panel can only draw slots that exist, so the
     * authored value is held to them rather than addressing a selector the document never declared.
     */
    @Test
    void anAuthoredCapPastTheCeilingIsHeldToIt() throws Exception {
        authorMaxRows("999");

        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, StationSummaryHud.ledgerRowCap());
    }

    /** A ledger of no rows at all is a panel with nothing to say, so the floor is one row. */
    @Test
    void anUnusableCapFallsBackRatherThanDrawingNothing() throws Exception {
        authorMaxRows("0");
        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, StationSummaryHud.ledgerRowCap());

        authorMaxRows("-3");
        assertEquals(StationSummaryHud.MAX_LEDGER_ROWS, StationSummaryHud.ledgerRowCap());
    }

    private static void authorMaxRows(String value) throws Exception {
        RpgStationsSettingsAsset settings = RpgStationsSettingsAsset.CODEC.decodeJson(
                RawJsonReader.fromJsonString("{ \"SummaryHud\": { \"MaxRows\": " + value + " } }"),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(
                        RpgStationsSettingsAsset.class, RpgStationsSettingsAsset.ID, null)));
        SettingsCatalog.getInstance().fold(Map.of(RpgStationsSettingsAsset.ID, settings), true);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
