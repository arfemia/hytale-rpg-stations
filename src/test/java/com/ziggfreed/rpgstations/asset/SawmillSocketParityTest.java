package com.ziggfreed.rpgstations.asset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE socket-model parity gate: a custody group with NO authored {@code Sockets} must synthesize
 * exactly ONE degenerate socket, reserved id {@code main}, whose EFFECTIVE leaves ARE the
 * custody-level values - so every shipped pre-socket station decodes and behaves identically with
 * the socket machinery underneath. Verified against the REAL shipped station assets, decoded
 * through the exact codec the server reads them with: this jar's Sawmill always, the companion
 * pack's Anvil when that repo is checked out beside this one (the {@code ShippedAssetDecodeTest}
 * optional-root rule), plus an anvil-shaped fixture this test authors so the Anvil's two custody
 * shapes (the bulk bars pile and the single metadata-preserving weapon slot) stay covered on a
 * standalone clone too.
 */
class SawmillSocketParityTest {

    private static final Path SAWMILL = Path.of("src", "main", "resources", "Server", "RpgStations",
            "Stations", "Sawmill.json");
    private static final Path PACK_ANVIL = Path.of("..", "..", "content-packs", "skill-stations-pack",
            "unreleased", "Server", "RpgStations", "Stations", "Anvil.json");

    private static StationAsset decode(String body, String key) throws Exception {
        return StationAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(body), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, key, null)));
    }

    /** The one degenerate-socket contract, asserted leaf by leaf against the custody-level values. */
    private static void assertDegenerateParity(Custody custody, String label) {
        assertFalse(custody.hasAuthoredSockets(), label + " authors no sockets");
        List<Custody.ResolvedSocket> sockets = custody.effectiveSockets();
        assertEquals(1, sockets.size(), label + " synthesizes exactly one socket");
        Custody.ResolvedSocket main = sockets.get(0);
        assertEquals(Custody.MAIN_SOCKET_ID, main.id(), label + " uses the reserved main id");
        assertTrue(main.itemRoute(), label + " degenerate socket takes the Item route");
        assertFalse(main.blockRoute());
        assertSame(custody.getInput(), main.match(),
                label + " match IS the custody-level Input (null = derived acceptance)");
        assertNull(main.placePerPress(), label + " places the whole held stack per press");
        assertNull(main.blockAt());
        assertEquals(custody.effectiveMaxQuantity(), main.maxQuantity(),
                label + " capacity IS the custody-level cap");
        assertEquals(custody.effectiveSingleFamily(), main.singleFamily());
        assertFalse(main.required(), label + " degenerate socket never gates engage");
        assertSame(custody.getDisplay(), main.display(),
                label + " display IS the custody-level Display group");
        assertFalse(main.sharePlace(), label + " stays owner-only (Share defaults false)");
        assertFalse(main.shareUse());
        assertFalse(main.shareReclaim());
        assertNull(main.label());
    }

    @Test
    void shippedSawmill_decodesToTheOneDegenerateMainSocket() throws Exception {
        StationAsset sawmill = decode(Files.readString(SAWMILL, StandardCharsets.UTF_8), "Sawmill");
        assertNotNull(sawmill.getActions());
        Custody custody = sawmill.getActions()[0].getCustody();
        assertNotNull(custody, "the shipped Sawmill's Mill action carries a Custody group");
        // No absolute cap number pinned here - the parity contract is RELATIVE (the socket's cap
        // IS the custody-level cap, whatever a balancing pass sets it to).
        assertDegenerateParity(custody, "Sawmill");
    }

    @Test
    void packAnvil_whenCheckedOutBeside_decodesToDegenerateSocketsPerAction() throws Exception {
        if (!Files.isRegularFile(PACK_ANVIL)) {
            return; // a standalone clone: the fixture test below still covers the anvil shapes.
        }
        StationAsset anvil = decode(Files.readString(PACK_ANVIL, StandardCharsets.UTF_8), "Anvil");
        assertNotNull(anvil.getActions());
        int custodyActions = 0;
        for (ActionDef action : anvil.getActions()) {
            Custody custody = action.getCustody();
            if (custody == null) {
                continue;
            }
            custodyActions++;
            assertDegenerateParity(custody, "Anvil action '" + action.getId() + "'");
        }
        assertTrue(custodyActions >= 2, "the pack Anvil ships two custody-governed actions");
    }

    @Test
    void anvilShapedFixture_bothCustodyShapes_stayDegenerate() throws Exception {
        // The Anvil's two custody shapes, authored here as a fixture so a standalone clone still
        // pins them: the bulk bars pile (cap 100) and the single metadata-preserving weapon slot
        // (cap 1 + a Function matcher + a rotated display).
        String body = """
                {
                  "Identity": { "NameKey": "rpgstations.station.sawmill.name" },
                  "Actions": [
                    { "Id": "Convert",
                      "Custody": { "MaxQuantity": 100,
                                   "States": { "Empty": "Default", "Loaded": "BarsPlaced" },
                                   "Display": { "Offset": { "Y": 0.52 }, "Scale": 1.0 } } },
                    { "Id": "Enhance",
                      "Custody": { "MaxQuantity": 1,
                                   "Input": { "Function": "Weapon" },
                                   "States": { "Empty": "Default", "Loaded": "WeaponPlaced" },
                                   "Display": { "Offset": { "X": 0.4, "Y": 0.55 }, "Scale": 1.0,
                                                "Rotation": { "Yaw": 0.0, "Roll": 90.0 } } } }
                  ]
                }
                """;
        StationAsset fixture = decode(body, "AnvilShaped");
        Custody convert = fixture.getActions()[0].getCustody();
        Custody enhance = fixture.getActions()[1].getCustody();
        assertDegenerateParity(convert, "fixture Convert");
        assertDegenerateParity(enhance, "fixture Enhance");
        assertEquals(1, enhance.effectiveSockets().get(0).maxQuantity(),
                "the single-item slot keeps its metadata-preserving capacity of 1");
        assertEquals("Weapon", enhance.effectiveSockets().get(0).match().getFunction(),
                "the weapon matcher rides the degenerate socket unchanged");
    }

    @Test
    void authoredSockets_resolveInOrder_perLeaf_overCustodyDefaults() throws Exception {
        // The authored-socket resolution rules in one fixture: authored order survives, ids
        // lowercase, an Item socket's capacity is the min of its own cap and the custody cap,
        // Share leaves fall back custody-first then false, a Block socket carries its offset and
        // no press knobs, and a both-routes socket is IGNORED (deny-nothing).
        String body = """
                {
                  "Identity": { "NameKey": "rpgstations.station.sawmill.name" },
                  "Actions": [
                    { "Id": "Stew",
                      "Custody": {
                        "MaxQuantity": 40,
                        "Share": { "Place": true },
                        "Sockets": {
                          "Vessel": { "Block": { "At": { "Y": 1 }, "Match": { "ItemId": "RPG_Station_Cooking_Pot" } },
                                      "Required": true, "Label": "rpgstations.socket.pot" },
                          "Ingredients": { "Item": { "PlacePerPress": 1 }, "MaxQuantity": 9,
                                           "SingleFamily": false, "Share": { "Use": true } },
                          "Output": { "Item": { "Match": { "ResourceTypeId": "Food" } }, "MaxQuantity": 90,
                                      "Share": { "Place": false, "Reclaim": true } },
                          "Broken": { "Item": {}, "Block": {} }
                        }
                      } }
                  ]
                }
                """;
        Custody custody = decode(body, "SocketFixture").getActions()[0].getCustody();
        assertTrue(custody.hasAuthoredSockets());
        List<Custody.ResolvedSocket> sockets = custody.effectiveSockets();
        assertEquals(3, sockets.size(), "the both-routes socket is ignored, the other three resolve");

        Custody.ResolvedSocket vessel = sockets.get(0);
        assertEquals("vessel", vessel.id(), "socket ids resolve lowercase, authored order first");
        assertTrue(vessel.blockRoute());
        assertTrue(vessel.required());
        assertEquals(1, vessel.blockAt().effectiveY());
        assertEquals("RPG_Station_Cooking_Pot", vessel.match().getItemId());
        assertEquals("rpgstations.socket.pot", vessel.label());

        Custody.ResolvedSocket ingredients = sockets.get(1);
        assertTrue(ingredients.itemRoute());
        assertEquals(1, ingredients.placePerPress());
        assertEquals(9, ingredients.maxQuantity(), "its own cap is under the custody cap and holds");
        assertNull(ingredients.match(), "no Match = the derived-from-recipe acceptance");
        assertTrue(ingredients.sharePlace(), "an unauthored Share leaf inherits the custody-level value");
        assertTrue(ingredients.shareUse(), "its own authored leaf wins");
        assertFalse(ingredients.shareReclaim(), "an unauthored leaf with no custody value stays false");

        Custody.ResolvedSocket output = sockets.get(2);
        assertEquals(40, output.maxQuantity(),
                "a socket cap above the custody cap clips to it (the min-of-caps rule)");
        assertFalse(output.sharePlace(), "a socket's own false overrides the custody-level true");
        assertTrue(output.shareReclaim());
    }
}
