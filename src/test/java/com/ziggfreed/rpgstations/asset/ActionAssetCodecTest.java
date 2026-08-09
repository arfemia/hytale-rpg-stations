package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * Codec layer for the standalone {@link ActionAsset}: id canonicalization, the shared
 * {@link ActionDef} body decode, the deliberate ABSENCE of {@code Ref}/{@code Id} keys on a
 * standalone action asset, native {@code Parent} per-leaf inheritance, and the inline
 * {@code Actions}-array {@link ActionDef#getRef()}+overlay decode (the shape
 * {@code station.ActionResolver} resolves).
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
    void body_decodesLabelSelectWorkAnchorsAndSteps() throws Exception {
        ActionAsset a = decode("prepfish", null, "{ \"Label\": \"action.prepfish.label\","
                + " \"Select\": { \"ResourceTypeId\": \"Fish\" },"
                + " \"Work\": { \"Looping\": true },"
                + " \"Anchors\": { \"Fire\": { \"Station\": \"CookingFire\", \"MaxRadiusMeters\": 12 } },"
                + " \"Steps\": [ { \"Id\": \"Load\", \"Consume\": { \"Items\": ["
                + "     { \"ResourceTypeId\": \"Fish\", \"Quantity\": 1 } ], \"From\": \"Custody\" } } ] }", null);
        ActionDef body = a.getBody();
        assertEquals("action.prepfish.label", body.getLabel());
        assertEquals("Fish", body.getSelect().getResourceTypeId());
        assertEquals(Boolean.TRUE, body.getWork().getLooping());
        assertNotNull(body.getAnchors());
        ActionDef.Anchor fire = body.getAnchors().get("Fire");
        assertEquals("CookingFire", fire.getStation());
        assertEquals(12.0, fire.effectiveMaxRadiusMeters());
        assertEquals("Load", body.getSteps()[0].getId());
        assertEquals("Fish", body.getSteps()[0].getConsume().getItems()[0].getResourceTypeId(),
                "Consume takes the Ingredient ARRAY shape, with the From route at group level");
        assertNull(body.getRef(), "a standalone ActionAsset never carries Ref (no Ref key in its codec)");
        assertNull(body.getId(), "a standalone ActionAsset's id IS its filename (no Id key in its codec)");
    }

    @Test
    void body_decodesTheGroupedWorkerAndMoments() throws Exception {
        ActionAsset a = decode("grouped", null, "{ \"Worker\": {"
                + " \"Hold\": { \"EffectId\": \"Fixture_Hold\" }, \"Puppet\": { \"Enabled\": true } },"
                + " \"Moments\": { \"Cycle\": { \"Sounds\": [\"Fixture_Cycle\"] } },"
                + " \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] },"
                + " \"ContributionScale\": { \"Floors\": [ { \"Min\": 4, \"Scale\": 1.5 } ] } }", null);
        ActionDef body = a.getBody();
        assertEquals("Fixture_Hold", body.getWorker().getHold().getEffectId());
        assertTrue(body.getWorker().getPuppet().effectiveEnabled());
        assertEquals("Fixture_Cycle", body.getMoments().getCycle().getSounds()[0]);
        assertNull(body.getMoments().getCompletion());
        assertEquals("FixtureFinds", body.getBonus().getLootables()[0]);
        assertEquals(1.5, body.getContributionScale().getFloors()[0].effectiveScale());
    }

    // ==================== ActionDef <-> ActionAsset field-set parity ====================

    /**
     * The two keys {@link ActionDef#CODEC} declares that {@link ActionAsset#CODEC} deliberately does
     * NOT: a {@code Ref} names ANOTHER action (a standalone action asset is itself a base), and an
     * {@code Id} identifies an entry within one station's ordered list (a standalone action's id IS
     * its filename).
     */
    private static final Set<String> ACTION_DEF_ONLY = Set.of("Ref", "Id");

    /**
     * The keys {@link ActionAsset#CODEC} declares that {@link ActionDef#CODEC} does not: the engine's
     * own asset-level fields, which an inline entry has no place for. {@code Name} is this type's own
     * no-op display field; {@code Tags} comes from {@code AssetBuilderCodec} itself, so it rides
     * every Pattern-A asset and is never declared here by hand.
     */
    private static final Set<String> ACTION_ASSET_ONLY = Set.of("Name", "Tags");

    /**
     * Both codecs are documented as ONE schema authority, but they are two hand-mirrored key lists,
     * so without this the sets drift silently - and the drift ships as a capability a standalone
     * action cannot author while an inline one can (which is exactly what happened once already).
     * Named exclusion sets carry the reason each side's extra key is legitimate.
     */
    @Test
    void actionDefAndActionAsset_declareTheSameFieldSet() {
        Set<String> defKeys = new HashSet<>(ActionDef.CODEC.getEntries().keySet());
        Set<String> assetKeys = new HashSet<>(ActionAsset.CODEC.getEntries().keySet());
        defKeys.removeAll(ACTION_DEF_ONLY);
        assetKeys.removeAll(ACTION_ASSET_ONLY);
        assertEquals(defKeys, assetKeys,
                "ActionDef and ActionAsset must declare the same authorable field set (one schema"
                        + " authority): only " + ACTION_DEF_ONLY + " is ActionDef-only and only "
                        + ACTION_ASSET_ONLY + " is ActionAsset-only.");
    }

    /**
     * The exclusion sets must name keys that genuinely EXIST on their side, or a stale entry silently
     * weakens the parity guard (removing a nonexistent key from a set is inert, so the assertion
     * above would keep passing while covering less).
     */
    @Test
    void parityExclusionSets_nameOnlyRealKeys() {
        assertTrue(ActionDef.CODEC.getEntries().keySet().containsAll(ACTION_DEF_ONLY),
                "every ACTION_DEF_ONLY key must be a real ActionDef codec key");
        assertTrue(ActionAsset.CODEC.getEntries().keySet().containsAll(ACTION_ASSET_ONLY),
                "every ACTION_ASSET_ONLY key must be a real ActionAsset codec key");
    }

    // ==================== native Parent per-leaf inheritance ====================

    @Test
    void parentInheritance_wholesaleOnOmit_ownWins_siblingLeafInherit() throws Exception {
        ActionAsset parent = decode("base_action", null, "{ \"Label\": \"base.label\","
                + " \"Work\": { \"CycleMs\": 4000, \"Looping\": true },"
                + " \"Anchors\": { \"Fire\": { \"Station\": \"CookingFire\", \"MaxRadiusMeters\": 8 } } }", null);
        assertEquals(4000L, parent.getBody().getWork().getCycleMs());

        ActionAsset childOmit = decode("child_omit", "base_action", "{}", parent);
        assertEquals("base.label", childOmit.getBody().getLabel(), "Label inherits on omit");
        assertEquals(4000L, childOmit.getBody().getWork().getCycleMs(), "Work inherits wholesale on omit");
        assertNotNull(childOmit.getBody().getAnchors(), "Anchors inherit on omit");

        ActionAsset childOwn = decode("child_own", "base_action", "{ \"Work\": { \"CycleMs\": 3000 } }", parent);
        assertEquals(3000L, childOwn.getBody().getWork().getCycleMs(), "own leaf wins");
        assertEquals(Boolean.TRUE, childOwn.getBody().getWork().getLooping(),
                "sibling leaf (Looping) inherits inside Work");
    }

    // ==================== inline Actions-array Ref + overlay ====================

    @Test
    void inlineActionRef_decodesRefAndOverlayGroupsTogether() throws Exception {
        StationAsset s = station("{ \"Actions\": ["
                + " { \"Id\": \"Prep\", \"Ref\": \"PrepFish\" },"
                + " { \"Id\": \"QuickPrep\", \"Ref\": \"PrepFish\", \"Work\": { \"Looping\": false } } ] }");
        ActionDef pure = s.getActions()[0];
        assertTrue(pure.hasRef());
        assertEquals("PrepFish", pure.getRef());
        assertNull(pure.getWork(), "a pure Ref authors no overlay groups");

        ActionDef overlaid = s.getActions()[1];
        assertTrue(overlaid.hasRef());
        assertEquals("PrepFish", overlaid.getRef());
        assertNotNull(overlaid.getWork(), "the overlay group decodes alongside Ref for ActionResolver to compose");
        assertEquals(Boolean.FALSE, overlaid.getWork().getLooping());
    }

    @Test
    void inlineAction_noRef_hasRefFalse() throws Exception {
        StationAsset s = station("{ \"Actions\": [ { \"Id\": \"Convert\", \"Work\": { \"CycleMs\": 3800 } } ] }");
        assertFalse(s.getActions()[0].hasRef());
    }
}
