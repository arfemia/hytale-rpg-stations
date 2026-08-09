package com.ziggfreed.rpgstations;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The enforcement half of this mod's progression-agnosticism rule: a foreign mod's name, id, type,
 * or domain concept may never appear in a schema key, an api type, an engine identifier, a
 * validator id, a lang value, or a shipped jar asset. Forwarding a value without interpreting it
 * is not a defense, and neither is a convenient comment - a leak in a comment is how the
 * vocabulary creeps back in, one "for context" sentence at a time.
 *
 * <p><b>Scanned roots</b> (everything this jar SHIPS or COMPILES):
 * <ul>
 *   <li>{@code src/main/java} - the engine.
 *   <li>{@code api/src/main/java} - the extension surface, whose javadoc ships (the api module
 *   publishes a javadoc jar).
 *   <li>{@code src/main/resources} - every shipped asset: {@code manifest.json}, all
 *   {@code Server/RpgStations/**.json}, every {@code .lang}, the {@code .ui} documents.
 * </ul>
 *
 * <p><b>The in-repo docs source IS scanned</b> ({@code docs}, the prose guide markdown pages): it
 * is the public authoring surface, and the worst realized leaks lived there (a tutorial teaching
 * the foreign vocabulary). ONE line is allowlisted, and it is the only entry the allowlist will
 * ever deliberately hold: the Add-ons &amp; Integrations page may NAME the companion progression
 * mod with an outbound link - naming a neighbor once, on the page whose whole job is naming
 * neighbors, is sanctioned; describing its vocabulary anywhere else is not.
 *
 * <p><b>Deliberately NOT scanned.</b> {@code src/test} is out of scope: fixture values are
 * author-owned, a fixture ships nothing, and a test that names a concrete foreign id while
 * proving a generic mechanism is doing its job. {@code CHANGELOG.md}, {@code CURSEFORGE.md}, and
 * the in-repo {@code CLAUDE.md} routers (which live INSIDE these source roots, hence the explicit
 * skip below) stay out too, for a structural reason rather than convenience: they are the
 * surfaces that STATE this rule and this mod's history, so they must be able to say "MMO" and
 * quote the retired vocabulary while explaining why it is retired - a scan of them would need a
 * semantic allowlist that swallows the very sentences doing the guarding.
 *
 * <p><b>The bar for a shipped example</b> (what to write when a doc string genuinely needs one):
 * name THIS engine's own ids or a native engine namespace ({@code EntityStatType},
 * {@code DamageCause}, {@code ItemDropList}), and for a third-party example use the fictitious
 * {@code yourmod:} namespace. Beyond the single Known-integrations line above, a hit is a real
 * finding, never a candidate for an exception.
 */
public class MmoAgnosticismTest {

    /** Source roots this jar ships or compiles from, plus the public in-repo docs source. */
    private static final List<Path> ROOTS = List.of(
            Path.of("src", "main", "java"),
            Path.of("api", "src", "main", "java"),
            Path.of("src", "main", "resources"),
            Path.of("docs"));

    /** File extensions worth reading (anything else under the roots is binary or generated). */
    private static final List<String> SCANNED_EXTENSIONS =
            List.of(".java", ".json", ".lang", ".ui", ".txt", ".md", ".mdx", ".tsx", ".ts");

    /** The per-package router filename: in-repo documentation, never shipped - see the class javadoc. */
    private static final String ROUTER_FILENAME = "claude.md";

    /**
     * The forbidden vocabulary. {@code MMO_[A-Za-z]} catches the stat/item id prefix;
     * {@code mmoskilltree} the namespace; the word-bounded rest catch the domain concepts that
     * leak into prose ("XP", "skill", "experience", "leveling") plus the bare product initialism.
     */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "mmoskilltree|MMO_[A-Za-z]|\\bxp\\b|\\bskills?\\b|\\bexperience\\b|\\bleveling\\b|\\bMMO\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void shippedAndCompiledSourcesNameNoForeignProgressionVocabulary() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : ROOTS) {
            assertTrue(Files.isDirectory(root), "missing scan root: " + root.toAbsolutePath());
            scan(root, hits);
        }
        assertTrue(hits.isEmpty(), () -> "Foreign progression vocabulary reached a shipped or compiled surface ("
                + hits.size() + " hit(s)). Rewrite each in this engine's own terms; use the fictitious"
                + " 'yourmod:' namespace for a third-party example.\n" + String.join("\n", hits));
    }

    private static void scan(Path root, List<String> hits) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).filter(MmoAgnosticismTest::isScanned).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = FORBIDDEN.matcher(lines.get(i));
                    if (m.find() && !isSanctioned(file, lines.get(i))) {
                        hits.add(file + ":" + (i + 1) + ": [" + m.group() + "] " + lines.get(i).trim());
                    }
                }
            }
        }
    }

    /**
     * The ONE sanctioned mention (see the class javadoc): the Add-ons &amp; Integrations page
     * naming the companion progression mod - its display name and its outbound link target,
     * nothing else. Everything else that matches is a finding.
     */
    private static boolean isSanctioned(Path file, String line) {
        return file.toString().replace('\\', '/').endsWith("docs/integrations.md")
                && (line.contains("MMO Skill Tree") || line.contains("mmo-skill-tree-docs.ziggfreed.com"));
    }

    private static boolean isScanned(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        // The excluded prose surfaces (class javadoc): CURSEFORGE.md and CHANGELOG.md state and
        // narrate this rule's own history, so they must be able to quote the retired vocabulary.
        if (ROUTER_FILENAME.equals(name) || "curseforge.md".equals(name) || "changelog.md".equals(name)) {
            return false;
        }
        return SCANNED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
