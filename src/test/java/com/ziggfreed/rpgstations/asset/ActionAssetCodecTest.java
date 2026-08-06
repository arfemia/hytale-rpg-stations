package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * Codec layer for the scope-2 {@link ActionAsset} (design 1.5): id canonicalization, the shared
 * {@link ActionDef} body decode (Label/Input/Steps/Anchors), the deliberate ABSENCE of a {@code Ref}
 * key on a standalone action asset, native {@code Parent} per-leaf inheritance, and the inline
 * {@code Actions}-map {@link ActionDef#getRef()}+overlay decode both groups (the shape the engine's
 * {@code ActionResolver} resolves - leg A3).
 */
public class ActionAssetCodecTest {

    private static ActionAsset decode(String id, String parentId, String body, ActionAsset parent) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ActionAsset.class, id, parentId);
        return ActionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), parent, new AssetExtraInfo<>(data));
    }

    private static StationAsset station(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    // ==================== id + body decode ====================

    @Test
    void id_isLowercasedAtDecode() throws Exception {
        ActionAsset a = decode("PrepFish", null, "{}", null);
        assertEquals("prepfish", a.getId());
    }

    @Test
    void body_decodesLabelInputStepsAnchors() throws Exception {
        ActionAsset a = decode("prepfish", null, "{ \"Label\": \"action.prepfish.label\","
                + " \"Input\": { \"ResourceTypeId\": \"Fish\" },"
                + " \"Work\": { \"Looping\": true },"
                + " \"Anchors\": { \"fire\": { \"Station\": \"cookingfire\", \"MaxRadiusMeters\": 12 } },"
                + " \"Steps\": [ { \"Id\": \"load\", \"Consume\": { \"ResourceTypeId\": \"Fish\", \"Quantity\": 1, \"From\": \"Custody\" } } ] }", null);
        ActionDef body = a.getBody();
        assertEquals("action.prepfish.label", body.getLabel());
        assertEquals("Fish", body.getInput().getResourceTypeId());
        assertEquals(Boolean.TRUE, body.getWork().getLooping());
        assertNotNull(body.getAnchors());
        ActionDef.Anchor fire = body.getAnchors().get("fire");
        assertEquals("cookingfire", fire.getStation());
        assertEquals(12.0, fire.effectiveMaxRadiusMeters());
        assertEquals("load", body.getSteps()[0].getId());
        assertNull(body.getRef(), "a standalone ActionAsset never carries Ref (no Ref key in its codec)");
    }

    // ==================== native Parent per-leaf inheritance ====================

    @Test
    void parentInheritance_wholesaleOnOmit_ownWins_siblingLeafInherit() throws Exception {
        ActionAsset parent = decode("base_action", null, "{ \"Label\": \"base.label\","
                + " \"Work\": { \"CycleMs\": 4000, \"Looping\": true },"
                + " \"Anchors\": { \"fire\": { \"Station\": \"cookingfire\", \"MaxRadiusMeters\": 8 } } }", null);
        assertEquals(4000L, parent.getBody().getWork().getCycleMs());

        ActionAsset childOmit = decode("child_omit", "base_action", "{}", parent);
        assertEquals("base.label", childOmit.getBody().getLabel(), "Label inherits on omit");
        assertEquals(4000L, childOmit.getBody().getWork().getCycleMs(), "Work inherits wholesale on omit");
        assertNotNull(childOmit.getBody().getAnchors(), "Anchors inherit on omit");

        ActionAsset childOwn = decode("child_own", "base_action", "{ \"Work\": { \"CycleMs\": 3000 } }", parent);
        assertEquals(3000L, childOwn.getBody().getWork().getCycleMs(), "own leaf wins");
        assertEquals(Boolean.TRUE, childOwn.getBody().getWork().getLooping(), "sibling leaf (Looping) inherits inside Work");
    }

    // ==================== inline Actions-map Ref + overlay (the shape ActionResolver resolves) ====================

    @Test
    void inlineActionRef_decodesRefAndOverlayGroupsTogether() throws Exception {
        StationAsset s = station("{ \"Actions\": {"
                + " \"prepfish\": { \"Ref\": \"prepfish\" },"
                + " \"quickfix\": { \"Ref\": \"prepfish\", \"Work\": { \"Looping\": false } } } }");
        ActionDef pure = s.getActions().get("prepfish");
        assertTrue(pure.hasRef());
        assertEquals("prepfish", pure.getRef());
        assertNull(pure.getWork(), "a pure Ref authors no overlay groups");

        ActionDef overlaid = s.getActions().get("quickfix");
        assertTrue(overlaid.hasRef());
        assertEquals("prepfish", overlaid.getRef());
        assertNotNull(overlaid.getWork(), "the overlay group decodes alongside Ref for ActionResolver to compose");
        assertEquals(Boolean.FALSE, overlaid.getWork().getLooping());
    }

    @Test
    void inlineAction_noRef_hasRefFalse() throws Exception {
        StationAsset s = station("{ \"Actions\": { \"convert\": { \"Work\": { \"CycleMs\": 3800 } } } }");
        assertFalse(s.getActions().get("convert").hasRef());
    }
}
