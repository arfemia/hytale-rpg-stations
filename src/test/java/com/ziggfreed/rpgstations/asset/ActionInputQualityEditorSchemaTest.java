package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;

/**
 * What the Asset Editor is told about the shared matcher's {@code Quality} leaf. Each entry offers
 * the engine's own picker over the loaded ItemQuality ids, at both nesting levels (a matcher and an
 * {@code Except} entry) and through the shared matcher definition an asset's schema references, in
 * the encoded form the editor receives. The list itself, the matcher's other string leaves and the
 * engine's shared string-array codec name no asset. The leaf carries no validator, so a hand-written
 * id in any casing, or a tier a later pack ships, still loads; {@code QUALITY_UNKNOWN}, run once
 * every pack has loaded, stays the check.
 */
class ActionInputQualityEditorSchemaTest {

    private static final List<BuilderCodec<ActionInput>> BOTH_LEVELS =
            List.of(ActionInput.CODEC, ActionInput.EXCEPT_CODEC);

    @Test
    void eachQualityEntryOffersTheQualityPicker_atBothLevels() {
        for (BuilderCodec<ActionInput> codec : BOTH_LEVELS) {
            ObjectSchema matcher = codec.toSchema(new SchemaContext());
            ArraySchema quality = (ArraySchema) matcher.getProperties().get("Quality");
            assertEquals("ItemQuality", ((StringSchema) quality.getItems()).getHytaleAssetRef());
            assertNull(quality.getHytaleAssetRef(), "the list is not one reference");
            assertNull(matcher.getProperties().get("ItemId").getHytaleAssetRef(),
                    "only the Quality leaf names a quality");
        }
    }

    @Test
    void aProtectListReachesThePickerThroughTheSharedMatcherDefinition_encodedAsTheEditorReceivesIt() {
        SchemaContext context = new SchemaContext();
        ProtectListAsset.CODEC.toSchema(context);
        // The editor receives nested types as one shared definitions file (common.json).
        Schema common = new Schema();
        common.setDefinitions(context.getDefinitions());
        BsonDocument encoded = Schema.CODEC.encode(common, new ExtraInfo()).asDocument();
        assertEquals("ItemQuality", encoded.getDocument("definitions").getDocument("ActionInput")
                .getDocument("properties").getDocument("Quality").getDocument("items")
                .getString("hytaleAssetRef").getValue());
    }

    @Test
    void theEnginesSharedStringArrayCodecIsLeftUntouched() {
        ActionInput.CODEC.toSchema(new SchemaContext());
        ArraySchema plain = (ArraySchema) Codec.STRING_ARRAY.toSchema(new SchemaContext());
        assertNull(((Schema) plain.getItems()).getHytaleAssetRef(),
                "a hint on the shared codec would put a quality picker on every string list in every schema");
    }

    @Test
    void theQualityLeafCarriesNoValidator_soNoIdIsRefusedAtDecode() {
        for (BuilderCodec<ActionInput> codec : BOTH_LEVELS) {
            BuilderField<ActionInput, ?> quality = codec.getEntries().get("Quality").getLast();
            assertTrue(quality.getValidators() == null || quality.getValidators().isEmpty(),
                    "a failing engine validator drops the whole file, and with it a protect list's protections");
        }
    }
}
