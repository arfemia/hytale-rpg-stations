package com.ziggfreed.rpgstations.docs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Fails the build when the committed repo-root schema reference ({@code SCHEMA.md}) lags the
 * actual asset codecs - the {@code EnglishLangDriftTest} pattern, applied to {@link
 * SchemaDocWriter} instead of {@code EnglishLangWriter}.
 *
 * <p>{@link SchemaDocWriter#render()} is the single source of truth for both the committed file
 * (regenerated on demand via {@code gradlew generateSchemaDocs}) and this comparison, so the two
 * can never independently drift from the codecs - only from each other, which is exactly the
 * condition this test catches.
 */
class SchemaDocDriftTest {

    private static final Path SCHEMA_FILE = Path.of("SCHEMA.md");

    @Test
    void committedSchemaMdMatchesTheLiveCodecs() throws IOException {
        String expected = SchemaDocWriter.render();

        if (!Files.exists(SCHEMA_FILE)) {
            fail("Missing " + SCHEMA_FILE.toAbsolutePath()
                    + " - run `gradlew generateSchemaDocs` and commit the result.");
            return;
        }

        String actual = Files.readString(SCHEMA_FILE, StandardCharsets.UTF_8);
        assertTrue(expected.equals(actual),
                () -> SCHEMA_FILE + " is stale (drifted from the live asset codecs). "
                        + "Run `gradlew generateSchemaDocs` and commit the regenerated file.\n"
                        + "Expected length=" + expected.length() + ", committed length=" + actual.length());
    }

    @Test
    void everyRootTypeRendersAtLeastOneField() {
        var model = SchemaDocWriter.renderModel();
        @SuppressWarnings("unchecked")
        var types = (java.util.Map<String, Object>) model.get("types");

        assertTrue(types.size() == SchemaDocWriter.rootTypeNames().size(),
                "expected one rendered entry per registered root type");

        for (String name : SchemaDocWriter.rootTypeNames()) {
            Object typeDoc = types.get(name);
            assertTrue(typeDoc instanceof java.util.Map, name + " did not render");
            @SuppressWarnings("unchecked")
            var fields = (java.util.List<Object>) ((java.util.Map<String, Object>) typeDoc).get("fields");
            assertTrue(fields != null && !fields.isEmpty(), name + " rendered zero fields");
        }
    }
}
