package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * Codec layer for {@link ProtectListAsset}: the id canonicalization, the {@code Protects} leaf in
 * both shapes (one matcher, or an array), each entry keeping its {@code Except} hole, and the
 * {@code Stations} / {@code Actions} scope rule (absent admits everything, an authored list only
 * what it names without regard to case, both authored must both match). Fixtures are authored here.
 */
class ProtectListAssetCodecTest {

    private static ProtectListAsset decode(String key, String body) throws Exception {
        return ProtectListAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(body), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ProtectListAsset.class, key, null)));
    }

    @Test
    void theIdIsTheLowercasedFileName() throws Exception {
        assertEquals("fixture_trophies", decode("Fixture_Trophies", "{ }").getId());
    }

    @Test
    void protects_decodesOneMatcherOrAnArray_eachWithItsOwnHole() throws Exception {
        ProtectListAsset one = decode("One", "{ \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }");
        assertEquals(1, one.getProtects().length);
        assertEquals("Fixture_Grimoire", one.getProtects()[0].getItemId());

        ProtectListAsset several = decode("Several", "{ \"Protects\": [ { \"ItemId\": \"Fixture_Grimoire\" },"
                + " { \"Tags\": { \"Type\": [\"Trophy\"] }, \"Except\": { \"ItemId\": \"Fixture_Plaque\" } } ] }");
        assertEquals(2, several.getProtects().length);
        assertArrayEquals(new String[] {"Trophy"}, several.getProtects()[1].getTags().get("Type"));
        assertTrue(several.getProtects()[1].hasExcept());
        assertEquals("Fixture_Plaque", several.getProtects()[1].getExcepts()[0].getItemId());

        assertNull(decode("Empty", "{ }").getProtects(), "absent protects nothing");
    }

    @Test
    void anUnscopedFile_appliesAtEveryStationAndAction() throws Exception {
        ProtectListAsset list = decode("Everywhere", "{ \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }");
        assertNull(list.getStations());
        assertNull(list.getActions());
        assertTrue(list.appliesTo("fixture_table", "Unmake"));
        assertTrue(list.appliesTo("fixture_mill", "Mill"));
    }

    @Test
    void aStationScope_admitsOnlyItsStations_withoutRegardToCase() throws Exception {
        ProtectListAsset list = decode("Tables", "{ \"Stations\": [\"Fixture_Table\"],"
                + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }");
        assertTrue(list.appliesTo("fixture_table", "Unmake"));
        assertTrue(list.appliesTo("FIXTURE_TABLE", "Anything"), "any action at a scoped station");
        assertFalse(list.appliesTo("fixture_mill", "Unmake"));
        assertFalse(list.appliesTo(null, "Unmake"), "a scoped file needs a station to admit");
    }

    @Test
    void anActionScope_admitsOnlyItsActions_atAnyStation() throws Exception {
        ProtectListAsset list = decode("Unmaking", "{ \"Actions\": [\"Unmake\"],"
                + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }");
        assertTrue(list.appliesTo("fixture_table", "unmake"));
        assertTrue(list.appliesTo("fixture_other_table", "Unmake"));
        assertFalse(list.appliesTo("fixture_table", "Mill"));
    }

    @Test
    void bothScopesAuthored_bothMustMatch() throws Exception {
        ProtectListAsset list = decode("Both", "{ \"Stations\": [\"Fixture_Table\"], \"Actions\": [\"Unmake\"],"
                + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }");
        assertTrue(list.appliesTo("fixture_table", "Unmake"));
        assertFalse(list.appliesTo("fixture_table", "Mill"), "the right station, another action");
        assertFalse(list.appliesTo("fixture_mill", "Unmake"), "the right action, another station");
    }

    @Test
    void anEmptyScopeList_readsAsUnscoped() throws Exception {
        ProtectListAsset list = decode("Blank", "{ \"Stations\": [], \"Actions\": [],"
                + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }");
        assertTrue(list.appliesTo("fixture_table", "Unmake"));
    }
}
