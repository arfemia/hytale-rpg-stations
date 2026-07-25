package com.ziggfreed.rpgstations.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.WrappedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.codec.codecs.EnumCodec;

import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.RollPool;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * Walks every authorable RpgStations asset type's declared static {@code CODEC} field via
 * {@link BuilderCodec#getEntries()} and renders a JSON schema-reference document consumed by
 * the docs-site's Schema Reference page ({@code docs-site/rpg-stations-docs/src/data/reference/
 * schema.json}, design doc section 6.2).
 *
 * <p>Mirrors the MMO's {@code i18n.lang.EnglishLangWriter} pattern: a single pure
 * {@link #render()} function shared by the Gradle {@code generateSchemaDocs} task ({@link #main})
 * and {@code SchemaDocDriftTest}, so the two can never diverge. Field documentation strings are
 * REAL (not backfilled here): every rewritten codec field in this mod's scope-2 authoring surface
 * carries a {@code .documentation("...")} call by design (principle 1.1-4), so {@link
 * BuilderField#getDocumentation()} already returns authored prose wherever an author wrote one;
 * fields without one simply omit the {@code "documentation"} key.
 *
 * <p><b>Type shape emitted per field</b>: {@code key}, {@code required}, {@code documentation?},
 * plus a classification of the field's child {@link Codec}:
 * <ul>
 *   <li>a nested {@link BuilderCodec} group renders {@code type:"object"} + {@code nestedType}; if
 *       that nested type is itself one of the 19 top-level documented types (a shared vocabulary
 *       type like {@code Condition}/{@code Roll}/{@code Presentation} reused inline elsewhere) the
 *       field carries a {@code "ref"} pointer instead of re-inlining the whole subtree (avoids both
 *       duplicating huge shared groups and the nested-class-name collisions several private groups
 *       share, e.g. three unrelated classes are all named {@code Offset}) - otherwise its fields are
 *       inlined recursively (a private structural group with no independent doc page);
 *   <li>an array/map/set field (detected via {@link WrappedCodec} + the concrete codec class's
 *       simple name, e.g. {@code ArrayCodec}/{@code MapCodec}/{@code SetCodec}) renders
 *       {@code type:"array"|"map"|"set"} + an {@code "of"} classification of the element/value codec;
 *   <li>an {@link EnumCodec} renders {@code type:"enum"} + a {@code "values"} list;
 *   <li>anything else (the primitive codecs - {@code StringCodec}/{@code IntegerCodec}/
 *       {@code DoubleCodec}/{@code BooleanCodec}/{@code LongCodec}/...) renders a friendly lowered
 *       type token derived from the codec class's own simple name (the {@code "Codec"} suffix
 *       stripped): {@code string}/{@code integer}/{@code double}/{@code boolean}/{@code long}/...
 * </ul>
 *
 * <p>The "Opportunistic route" from the design doc ({@code CODEC.toSchema(SchemaContext)}, the
 * official Hytale editor JSON-schema shape) was evaluated: {@link
 * com.hypixel.hytale.codec.schema.SchemaContext} IS constructible outside a live server (a bare
 * no-arg constructor over pure in-memory maps), so it is not blocked the way the design doc's open
 * question worried it might be. It was NOT wired in here: its output shape (editor UI metadata,
 * {@code $Title}/{@code $Comment} synthetic properties, a {@code common.json}/{@code other.json}
 * definitions split) targets the Hytale asset-pack EDITOR, not a docs-site field table, and the
 * design doc's own field shape ({@code key}/{@code type}/{@code nestedType}/{@code required}/
 * {@code documentation}) is exactly what {@link #render()} already produces from {@code
 * getEntries()} alone - "the getEntries() walk stands alone" per section 6.2. A future docs-site
 * leg wanting the raw editor schema can add a second output file from {@code toSchema} without
 * touching this one.
 */
public final class SchemaDocWriter {

    /**
     * The 19 authorable / shared-vocabulary types this schema reference documents, keyed by the
     * name the docs-site groups them under (design doc section 6.2's list). {@code SettingsAsset}
     * is the doc-facing name for the actual class {@link RpgStationsSettingsAsset}.
     */
    private static final Map<String, BuilderCodec<?>> ROOT_CODECS = new LinkedHashMap<>();

    /** Reverse lookup: a nested field's {@link BuilderCodec#getInnerClass()} back to its root doc name, so a shared type (e.g. {@code Roll} reused inside {@code LootableAsset}) links instead of re-inlining. */
    private static final Map<Class<?>, String> ROOT_TYPE_NAMES = new LinkedHashMap<>();

    static {
        register("StationAsset", StationAsset.CODEC);
        register("ActionAsset", ActionAsset.CODEC);
        register("ActionDef", ActionDef.CODEC);
        register("StationStep", StationStep.CODEC);
        register("Ingredient", Ingredient.CODEC);
        register("FactorRef", FactorRef.CODEC);
        register("LootRef", LootRef.CODEC);
        register("Roll", Roll.CODEC);
        register("Condition", Condition.CODEC);
        register("Custody", Custody.CODEC);
        register("Puppet", Puppet.CODEC);
        register("Requires", Requires.CODEC);
        register("Presentation", Presentation.CODEC);
        register("StatRollEntry", StatRollEntry.CODEC);
        register("RollPool", RollPool.CODEC);
        register("LootableAsset", LootableAsset.CODEC);
        register("FlairAsset", FlairAsset.CODEC);
        register("ExtensionAsset", ExtensionAsset.CODEC);
        register("SettingsAsset", RpgStationsSettingsAsset.CODEC);
    }

    private SchemaDocWriter() {
    }

    private static void register(String docName, BuilderCodec<?> codec) {
        ROOT_CODECS.put(docName, codec);
        ROOT_TYPE_NAMES.put(codec.getInnerClass(), docName);
    }

    /** The root type names in registration (= docs-nav / design-doc) order. */
    public static List<String> rootTypeNames() {
        return new ArrayList<>(ROOT_CODECS.keySet());
    }

    /**
     * Render every registered root type's codec into the in-memory JSON model
     * ({@code Map}/{@code List}/{@code String}/{@code Boolean} only, so {@link #toJson} can print
     * it with zero external dependencies - this mod carries no Gson/Jackson runtime dependency).
     */
    public static Map<String, Object> renderModel() {
        Map<String, Object> types = new LinkedHashMap<>();
        for (Map.Entry<String, BuilderCodec<?>> entry : ROOT_CODECS.entrySet()) {
            BuilderCodec<?> codec = entry.getValue();
            Map<String, Object> typeDoc = new LinkedHashMap<>();
            String doc = codec.getDocumentation();
            if (doc != null) {
                typeDoc.put("documentation", doc);
            }
            typeDoc.put("fields", renderFields(codec, new ArrayDeque<>()));
            types.put(entry.getKey(), typeDoc);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedFrom",
                "SchemaDocWriter (walks each authorable type's static CODEC via BuilderCodec#getEntries())");
        root.put("rootTypes", rootTypeNames());
        root.put("types", types);
        return root;
    }

    /** {@link #renderModel()} pretty-printed as stable, deterministic JSON text (2-space indent, LF-terminated). */
    public static String render() {
        StringBuilder sb = new StringBuilder(16_384);
        toJson(renderModel(), sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> renderFields(BuilderCodec<?> codec, Deque<Class<?>> stack) {
        List<Object> out = new ArrayList<>();

        // Defensive: none of this mod's codecs use Java builder-chain inheritance today (every
        // group is authored standalone), but a codec whose builder DID call .inherits(parent)
        // would otherwise silently drop the parent's fields from getEntries().
        BuilderCodec<?> parent = codec.getParent();
        if (parent != null) {
            out.addAll(renderFields(parent, stack));
        }

        Map<String, List<BuilderField>> entries = (Map) codec.getEntries();
        for (Map.Entry<String, List<BuilderField>> e : entries.entrySet()) {
            List<BuilderField> versions = e.getValue();
            if (versions.isEmpty()) {
                continue;
            }
            // getEntries() sorts each key's versions ascending by minVersion; the last entry is
            // the current (highest) one. No field in this mod uses version ranges today.
            BuilderField field = versions.get(versions.size() - 1);
            KeyedCodec keyed = field.getCodec();

            Map<String, Object> fieldDoc = new LinkedHashMap<>();
            fieldDoc.put("key", keyed.getKey());
            fieldDoc.put("required", keyed.isRequired());
            String doc = field.getDocumentation();
            if (doc != null) {
                fieldDoc.put("documentation", doc);
            }
            fieldDoc.putAll(classify((Codec) keyed.getChildCodec(), stack));
            out.add(fieldDoc);
        }
        return out;
    }

    /** Classify one field's child codec into {@code {type, nestedType?, ref?, of?, values?, fields?, cyclic?}}. */
    private static Map<String, Object> classify(Codec<?> codec, Deque<Class<?>> stack) {
        Map<String, Object> out = new LinkedHashMap<>();

        if (codec instanceof BuilderCodec<?> bc) {
            Class<?> inner = bc.getInnerClass();
            out.put("type", "object");
            out.put("nestedType", inner.getSimpleName());

            String rootName = ROOT_TYPE_NAMES.get(inner);
            if (rootName != null) {
                // A shared/top-level type reused inline (e.g. Roll inside LootableAsset.Rolls):
                // link to its own top-level entry instead of duplicating the whole subtree.
                out.put("ref", rootName);
            } else if (stack.contains(inner)) {
                // A private structural group cycling back to an ancestor with no root-type
                // boundary in between. Not currently reachable by any codec in this mod (private
                // nested groups form a tree), but guarded so a future authoring change fails soft
                // (a "cyclic" marker) instead of a StackOverflowError.
                out.put("cyclic", true);
            } else {
                stack.push(inner);
                out.put("fields", renderFields(bc, stack));
                stack.pop();
            }
            return out;
        }

        if (codec instanceof WrappedCodec<?> wrapped) {
            String simple = codec.getClass().getSimpleName();
            String bucket = simple.contains("Array") ? "array"
                    : simple.contains("Map") ? "map"
                    : simple.contains("Set") ? "set"
                    : "wrapped";
            out.put("type", bucket);
            out.put("of", classify(wrapped.getChildCodec(), stack));
            return out;
        }

        if (codec instanceof EnumCodec<?> ec) {
            out.put("type", "enum");
            out.put("values", Arrays.asList(ec.getEnumKeys()));
            return out;
        }

        // A primitive leaf codec (StringCodec/IntegerCodec/DoubleCodec/BooleanCodec/LongCodec/...)
        // or any other terminal Codec this mod's fields don't currently use. Friendly name = the
        // codec class's own simple name with a trailing "Codec" stripped, first letter lowered.
        out.put("type", friendlyLeafName(codec.getClass()));
        return out;
    }

    private static String friendlyLeafName(Class<?> codecClass) {
        String simple = codecClass.getSimpleName();
        if (simple.endsWith("Codec")) {
            simple = simple.substring(0, simple.length() - "Codec".length());
        }
        if (simple.isEmpty()) {
            return codecClass.getName();
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static void toJson(Object value, StringBuilder sb, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            int n = map.size();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                indent(sb, indent + 1);
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\": ");
                toJson(e.getValue(), sb, indent + 1);
                if (++i < n) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(sb, indent + 1);
                toJson(list.get(i), sb, indent + 1);
                if (i < list.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, indent);
            sb.append(']');
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Render and write {@link #render()} to {@code outFile}, creating parent directories as needed. */
    public static Path writeTo(Path outFile) {
        try {
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, render(), StandardCharsets.UTF_8);
            return outFile;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write " + outFile, ex);
        }
    }

    /**
     * Gradle entry point: {@code args[0]} is the output file path (e.g.
     * {@code docs-site/rpg-stations-docs/src/data/reference/schema.json}).
     */
    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            throw new IllegalArgumentException(
                    "Usage: SchemaDocWriter <outFile> (e.g. docs-site/rpg-stations-docs/src/data/reference/schema.json)");
        }
        Path outFile = writeTo(Path.of(args[0]));

        int fieldCount = 0;
        Map<String, Object> model = renderModel();
        @SuppressWarnings("unchecked")
        Map<String, Object> types = (Map<String, Object>) model.get("types");
        for (Object typeDoc : types.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) typeDoc;
            @SuppressWarnings("unchecked")
            List<Object> fields = (List<Object>) t.get("fields");
            fieldCount += countFieldsDeep(fields);
        }
        System.out.println("[generateSchemaDocs] Wrote " + types.size() + " types (" + fieldCount
                + " fields, including nested groups) to " + outFile.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static int countFieldsDeep(List<Object> fields) {
        int count = 0;
        for (Object f : fields) {
            Map<String, Object> fieldDoc = (Map<String, Object>) f;
            count++;
            List<Object> nested = (List<Object>) fieldDoc.get("fields");
            if (nested != null) {
                count += countFieldsDeep(nested);
            }
        }
        return count;
    }
}
