package com.ziggfreed.rpgstations.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the JAR-bundled {@code Server/Languages/<bcp47>/*.lang} files against the
 * translation mistakes that silently break rendering at runtime. Ported from the MMO's
 * {@code i18n.LangFileIntegrityTest} (leg 7A, critique minor "no LangFileIntegrityTest
 * equivalent planned for RpgStations"), scoped to whatever locale directories currently
 * exist under {@link #LANG_ROOT} - today that is en-US only ({@code rpgstations.lang},
 * plus the native-namespace {@code items.lang}/{@code avatarCustomization.lang}); the
 * per-locale fan-out that lands the other 8 locales' files makes this test cover them
 * automatically, no test change needed.
 *
 * <ul>
 *   <li><b>Placeholder integrity</b>: a translated value must use exactly the same
 *       positional {@code {0}}/{@code {1}} placeholders as the en-US value for that
 *       key, or substitution drops arguments (or prints a literal {@code {1}}). A no-op
 *       today (only en-US exists), load-bearing the moment a second locale lands.</li>
 *   <li><b>No em-dashes</b>: banned repo-wide, including every {@code .lang} value.</li>
 *   <li><b>No duplicate keys</b>: a duplicate silently shadows the earlier value.</li>
 * </ul>
 *
 * Key COVERAGE (every en-US key present in every other language) is intentionally NOT
 * asserted here, matching the MMO's own test: a missing key falls back to English per
 * key through Hytale's I18nModule.
 */
public class LangFileIntegrityTest {

    private static final Path LANG_ROOT = Path.of("src", "main", "resources", "Server", "Languages");
    private static final String EN_US = "en-US";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    @Test
    void translationsKeepPlaceholdersBanEmDashesAndHaveNoDuplicateKeys() throws IOException {
        assertTrue(Files.isDirectory(LANG_ROOT), "missing lang root: " + LANG_ROOT.toAbsolutePath());

        Map<String, Map<String, String>> englishByFile = new LinkedHashMap<>();
        Path enDir = LANG_ROOT.resolve(EN_US);
        if (Files.isDirectory(enDir)) {
            try (Stream<Path> files = Files.list(enDir)) {
                for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".lang")).toList()) {
                    englishByFile.put(f.getFileName().toString(), parse(f, new ArrayList<>()));
                }
            }
        }

        List<String> problems = new ArrayList<>();
        try (Stream<Path> locales = Files.list(LANG_ROOT)) {
            locales.filter(Files::isDirectory).sorted().forEach(localeDir -> {
                String locale = localeDir.getFileName().toString();
                try (Stream<Path> files = Files.list(localeDir)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".lang")).sorted().forEach(langFile -> {
                        String fileName = langFile.getFileName().toString();
                        List<String> dupes = new ArrayList<>();
                        Map<String, String> entries;
                        try {
                            entries = parse(langFile, dupes);
                        } catch (IOException e) {
                            problems.add(locale + "/" + fileName + ": unreadable - " + e.getMessage());
                            return;
                        }
                        for (String dupe : dupes) {
                            problems.add(locale + "/" + fileName + ": duplicate key '" + dupe + "'");
                        }
                        Map<String, String> english = englishByFile.get(fileName);
                        for (Map.Entry<String, String> e : entries.entrySet()) {
                            if (e.getValue().indexOf('—') >= 0) {
                                problems.add(locale + "/" + fileName + ": em-dash in '" + e.getKey() + "'");
                            }
                            if (EN_US.equals(locale) || english == null) {
                                continue;
                            }
                            String en = english.get(e.getKey());
                            if (en != null && !placeholders(en).equals(placeholders(e.getValue()))) {
                                problems.add(locale + "/" + fileName + ": placeholder mismatch on '"
                                        + e.getKey() + "' (en: " + placeholders(en) + ", translated: "
                                        + placeholders(e.getValue()) + ")");
                            }
                        }
                    });
                } catch (IOException e) {
                    problems.add(locale + ": unlistable - " + e.getMessage());
                }
            });
        }
        assertTrue(problems.isEmpty(), () -> problems.size() + " lang problems:\n" + String.join("\n", problems));
    }

    /** {@code key -> value} for one .lang file; duplicate keys are reported via {@code dupesOut}. */
    private static Map<String, String> parse(Path file, List<String> dupesOut) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String s = line.strip();
            int eq = s.indexOf('=');
            if (s.isEmpty() || s.startsWith("#") || eq <= 0) {
                continue;
            }
            String key = s.substring(0, eq).strip();
            if (out.put(key, s.substring(eq + 1).strip()) != null) {
                dupesOut.add(key);
            }
        }
        return out;
    }

    /** Multiset of positional placeholders in a value ("{0}{0}{1}" -> {0=2, 1=1}). */
    private static Map<Integer, Integer> placeholders(String value) {
        Map<Integer, Integer> counts = new TreeMap<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            counts.merge(Integer.parseInt(m.group(1)), 1, Integer::sum);
        }
        return counts;
    }
}
