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
import java.util.Map;

import com.hypixel.hytale.assetstore.codec.ContainedAssetCodec;
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
 * {@link BuilderCodec#getEntries()} and renders a GitHub-Markdown schema-reference document,
 * committed at the repo root as {@code SCHEMA.md} (regenerated on demand via
 * {@code gradlew generateSchemaDocs}).
 *
 * <p>ONE pure
 * {@link #render()} function shared by the Gradle {@code generateSchemaDocs} task ({@link #main})
 * and {@code SchemaDocDriftTest}, so the two can never diverge. Field documentation strings are
 * REAL (not backfilled here): every rewritten codec field in this mod's authoring surface carries
 * a {@code .documentation("...")} call by design, so {@link BuilderField#getDocumentation()}
 * already returns authored prose wherever an author wrote one; fields without one simply render an
 * empty documentation cell.
 *
 * <p><b>What {@link #render()} emits</b>: one {@code ##} section per registered root type (a table
 * of contents up top, then each section's own {@code Key | Type | Default | Documentation} table),
 * plus one further subsection per PRIVATE nested field group (a group with no independent doc
 * section of its own, e.g. {@code Custody.Display}) - a shared vocabulary type reused inline (e.g.
 * {@code Roll} inside {@code LootableAsset}) instead links straight to its own top-level section
 * rather than re-inlining the whole subtree. Every section carries an explicit {@code <a id="...">}
 * anchor so cross-references resolve regardless of how GitHub's own heading-slug algorithm treats
 * the visible heading text. The {@code Default} column reads the literal `required` marker for a
 * required field and `null` for every optional one (every RpgStations field is nullable-by-omission
 * unless marked required, so that IS its default).
 *
 * <p>Internally, {@link #renderModel()} still walks the codecs into a pure in-memory
 * {@code Map}/{@code List}/{@code String} model first (also consumed directly by
 * {@code SchemaDocDriftTest}'s structural sanity check), classifying each field's child
 * {@link Codec}:
 * <ul>
 *   <li>a nested {@link BuilderCodec} group renders {@code type:"object"} + {@code nestedType}; if
 *       that nested type is itself one of the registered top-level documented types the field
 *       carries a {@code "ref"} pointer instead of re-inlining the whole subtree (avoids both
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
 * <p>The "opportunistic" official-schema route ({@code CODEC.toSchema(SchemaContext)}, the same
 * shape the Hytale asset-pack editor consumes) was evaluated and deliberately NOT wired in here:
 * its output (editor UI metadata, {@code $Title}/{@code $Comment} synthetic properties, a
 * {@code common.json}/{@code other.json} definitions split) targets the in-editor tooling, not a
 * readable field-reference table, and the {@code key}/{@code type}/{@code nestedType}/
 * {@code required}/{@code documentation} shape {@link #render()} already produces from
 * {@code getEntries()} alone covers this document's job on its own. A future addition wanting the
 * raw editor schema can add a second output file from {@code toSchema} without touching this one.
 */
public final class SchemaDocWriter {

    /**
     * The authorable / shared-vocabulary types this schema reference documents, keyed by the name
     * used as both the section heading and the cross-reference target. {@code SettingsAsset} is
     * the doc-facing name for the actual class {@link RpgStationsSettingsAsset}.
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
     * Walk every registered root type's codec into a pure in-memory model
     * ({@code Map}/{@code List}/{@code String}/{@code Boolean} only, no external dependency - this
     * mod carries no Gson/Jackson runtime dependency), consumed by both {@link #render()} (the
     * Markdown renderer) and {@code SchemaDocDriftTest}'s structural sanity check.
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

    /** {@link #renderModel()} rendered as stable, deterministic GitHub Markdown (LF-terminated). */
    public static String render() {
        return renderMarkdown(renderModel());
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

        if (codec instanceof ContainedAssetCodec<?, ?, ?> contained) {
            // A ref-or-inline leaf: the value is EITHER an id string naming an asset of this type,
            // OR an inline anonymous body of it. Name the target type so the reference page can
            // render a typed cross-link rather than an opaque "containedAsset".
            out.put("type", "containedAsset");
            String target = contained.getAssetClass().getSimpleName();
            out.put("assetType", target);
            String rootName = ROOT_TYPE_NAMES.get(contained.getAssetClass());
            if (rootName != null) {
                out.put("ref", rootName);
            }
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

    /**
     * One pending private nested-group subsection discovered while rendering a table, queued so it
     * renders (breadth-first, in discovery order) after the table that referenced it.
     *
     * @param anchor the {@code <a id="...">} target, ASCII/hyphen only
     * @param heading the human-readable dotted path shown as the section heading text
     * @param fields  that group's own field list (the same shape {@link #renderFields} produces)
     * @param level   the Markdown heading level ({@code 1}-{@code 6}) this subsection renders at
     */
    private record PendingSection(String anchor, String heading, List<Object> fields, int level) {
    }

    @SuppressWarnings("unchecked")
    private static String renderMarkdown(Map<String, Object> model) {
        List<String> rootTypes = (List<String>) model.get("rootTypes");
        Map<String, Object> types = (Map<String, Object>) model.get("types");

        StringBuilder sb = new StringBuilder(65_536);
        sb.append("# RPG Stations Schema Reference\n\n");
        sb.append("Generated by `gradlew generateSchemaDocs` from the RpgStations asset codecs ")
                .append("(`SchemaDocWriter`, walking each authorable type's static `CODEC` via ")
                .append("`BuilderCodec#getEntries()`). Do not hand-edit; `SchemaDocDriftTest` fails ")
                .append("the build if this file drifts from the live codecs.\n\n");
        sb.append("Every field is nullable and defaults to `null` unless its Default column reads ")
                .append("*(required)*. See [Getting Started](docs/getting-started.md) and ")
                .append("[Your First Station](docs/your-first-station.md) for worked examples of the ")
                .append("shapes below.\n\n");

        sb.append("## Types\n\n");
        for (String name : rootTypes) {
            sb.append("- [").append(name).append("](#type-").append(slug(name)).append(")\n");
        }
        sb.append('\n');

        for (String name : rootTypes) {
            renderType(sb, name, (Map<String, Object>) types.get(name));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void renderType(StringBuilder sb, String rootName, Map<String, Object> typeDoc) {
        sb.append("<a id=\"type-").append(slug(rootName)).append("\"></a>\n");
        sb.append("## ").append(rootName).append("\n\n");

        String doc = (String) typeDoc.get("documentation");
        if (doc != null && !doc.isBlank()) {
            sb.append(doc.strip()).append("\n\n");
        }

        Deque<PendingSection> queue = new ArrayDeque<>();
        renderFieldsTable(sb, (List<Object>) typeDoc.get("fields"), slug(rootName), rootName, 3, queue);

        while (!queue.isEmpty()) {
            PendingSection section = queue.poll();
            sb.append("<a id=\"field-").append(section.anchor()).append("\"></a>\n");
            sb.append("#".repeat(Math.min(Math.max(section.level(), 1), 6)))
                    .append(' ').append(section.heading()).append("\n\n");
            renderFieldsTable(sb, section.fields(), section.anchor(), section.heading(), section.level() + 1, queue);
        }
    }

    @SuppressWarnings("unchecked")
    private static void renderFieldsTable(StringBuilder sb, List<Object> fields, String anchorPrefix,
            String humanPrefix, int level, Deque<PendingSection> queue) {
        if (fields.isEmpty()) {
            sb.append("_No fields._\n\n");
            return;
        }
        sb.append("| Key | Type | Default | Documentation |\n");
        sb.append("|---|---|---|---|\n");
        for (Object o : fields) {
            Map<String, Object> field = (Map<String, Object>) o;
            String key = (String) field.get("key");
            boolean required = Boolean.TRUE.equals(field.get("required"));
            String anchor = anchorPrefix + "-" + slug(key);
            String human = humanPrefix + "." + key;
            String typeLabel = describeType(field, anchor, human, level, queue);
            String def = required ? "*(required)*" : "`null`";
            Object docValue = field.get("documentation");
            String docCell = docValue != null ? escapeCell((String) docValue) : "";
            sb.append("| `").append(key).append("` | ").append(typeLabel).append(" | ").append(def)
                    .append(" | ").append(docCell).append(" |\n");
        }
        sb.append('\n');
    }

    /** Describes one {@code classify()}-shaped type map as a Markdown table-cell label. */
    @SuppressWarnings("unchecked")
    private static String describeType(Map<String, Object> typeInfo, String anchor, String human, int level,
            Deque<PendingSection> queue) {
        String type = (String) typeInfo.get("type");
        switch (type) {
            case "object" -> {
                String ref = (String) typeInfo.get("ref");
                String nestedType = (String) typeInfo.get("nestedType");
                if (ref != null) {
                    return "[" + ref + "](#type-" + slug(ref) + ")";
                }
                if (Boolean.TRUE.equals(typeInfo.get("cyclic"))) {
                    return "*(cyclic reference to " + nestedType + ")*";
                }
                queue.add(new PendingSection(anchor, human, (List<Object>) typeInfo.get("fields"), level));
                return "[" + nestedType + "](#field-" + anchor + ")";
            }
            case "array", "map", "set" -> {
                Map<String, Object> of = (Map<String, Object>) typeInfo.get("of");
                String childLabel = describeType(of, anchor + "-item", human + "[]", level, queue);
                String prefix = switch (type) {
                    case "array" -> "array of ";
                    case "map" -> "map of ";
                    default -> "set of ";
                };
                return prefix + childLabel;
            }
            case "enum" -> {
                List<String> values = (List<String>) typeInfo.get("values");
                StringBuilder joined = new StringBuilder("enum (");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        joined.append(", ");
                    }
                    joined.append('`').append(values.get(i)).append('`');
                }
                return joined.append(')').toString();
            }
            case "containedAsset" -> {
                String assetType = (String) typeInfo.get("assetType");
                String ref = (String) typeInfo.get("ref");
                return ref != null
                        ? "id, or inline [" + assetType + "](#type-" + slug(ref) + ")"
                        : "id, or inline `" + assetType + "`";
            }
            default -> {
                return "`" + type + "`";
            }
        }
    }

    /** Lowercase ASCII slug for a Markdown `<a id>` anchor: letters/digits kept, everything else collapsed to `-`. */
    private static String slug(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    /** Makes a documentation string safe inside one GitHub Markdown table cell. */
    private static String escapeCell(String s) {
        return s.replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>");
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
     * Gradle entry point: {@code args[0]} is the output file path (e.g. {@code SCHEMA.md} at the
     * repo root).
     */
    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("Usage: SchemaDocWriter <outFile> (e.g. SCHEMA.md)");
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
