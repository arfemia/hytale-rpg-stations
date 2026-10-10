package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads this package's main-tree source for the tests that pin an ORDER on the source itself,
 * where the code under pin runs only against a live store and inventory that no unit JVM can
 * build (the input-consumed hook's commit order, the fallback rows selection hands the scan). A
 * method body is cut from its declaration to its matching closing brace, skipping string and
 * character literals and comments, so a pin reads exactly one method.
 */
final class SourcePins {

    private SourcePins() {
    }

    /** The station package's main source directory, from the module root (Gradle's test working dir). */
    static Path stationSourceDir() {
        Path direct = Path.of("src", "main", "java", "com", "ziggfreed", "rpgstations", "station");
        assertTrue(Files.isDirectory(direct), "run from rpg-stations' module root: no " + direct.toAbsolutePath());
        return direct;
    }

    /** One station-package source file's text. */
    static String read(String file) throws IOException {
        return Files.readString(stationSourceDir().resolve(file), StandardCharsets.UTF_8);
    }

    /** The body of the FIRST method whose declaration starts with {@code signatureStart}. */
    static String methodBody(String source, String signatureStart) {
        return methodBody(source, signatureStart, "");
    }

    /**
     * The body of the method whose declaration starts with {@code signatureStart} and whose header
     * (the text up to its opening brace) contains {@code headerMarker}, which picks one overload
     * out of several.
     */
    static String methodBody(String source, String signatureStart, String headerMarker) {
        int from = 0;
        while (true) {
            int start = source.indexOf(signatureStart, from);
            if (start < 0) {
                fail("method not found: " + signatureStart + " [" + headerMarker + "]");
            }
            int open = source.indexOf('{', start);
            assertTrue(open > start, "no body after " + signatureStart);
            if (source.substring(start, open).contains(headerMarker)) {
                return source.substring(start, closingBrace(source, open) + 1);
            }
            from = start + signatureStart.length();
        }
    }

    /**
     * The body of every {@code for} loop in {@code text} whose header iterates {@code iterated}, in
     * source order, each cut from its opening brace to the matching closing one. Every occurrence
     * of {@code iterated} must sit in a {@code for} header, so a pin never reads a loop that is not
     * there.
     */
    static List<String> loopBodies(String text, String iterated) {
        List<String> bodies = new ArrayList<>();
        for (int at = text.indexOf(iterated); at >= 0; at = text.indexOf(iterated, at + iterated.length())) {
            int lineStart = text.lastIndexOf('\n', at) + 1;
            assertTrue(text.substring(lineStart, at).contains("for ("),
                    iterated + " is read outside a for header: " + text.substring(lineStart, at));
            int open = text.indexOf('{', at);
            assertTrue(open > at, "no loop body after " + iterated);
            bodies.add(text.substring(open, closingBrace(text, open) + 1));
        }
        return bodies;
    }

    /** How many times {@code call} appears in {@code text}. */
    static int count(String text, String call) {
        int n = 0;
        for (int i = text.indexOf(call); i >= 0; i = text.indexOf(call, i + call.length())) {
            n++;
        }
        return n;
    }

    /** The index of the brace closing the one at {@code open}, literals and comments skipped. */
    private static int closingBrace(String source, int open) {
        int depth = 0;
        int i = open;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(source, i, c);
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int eol = source.indexOf('\n', i);
                i = eol < 0 ? source.length() : eol + 1;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        fail("unbalanced braces after index " + open);
        return -1;
    }

    /** The index just past the string or character literal opening at {@code start}. */
    private static int skipLiteral(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return i;
    }
}
