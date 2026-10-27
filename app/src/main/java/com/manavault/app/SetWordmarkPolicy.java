package com.manavault.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative OCR interpretation for lettered Core Set expansion symbols.
 *
 * Called only from PrintingFingerprintVerifier's tightly cropped type-bar
 * wordmark probes. This does not replace the existing SVG silhouette matching,
 * collector-number verification, or manual review of ambiguous printings.
 *
 * A successful wordmark reading must also agree across two independent OCR
 * observations before the scanner promotes it to set evidence.
 */
final class SetWordmarkPolicy {
    private SetWordmarkPolicy() {}

    // MTG core sets with physically printed M+number wordmarks.
    // M16-M18 were not released under these set codes; ORI is not an "M" logo.
    private static final Set<String> LETTERED_CORE_SETS =
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "m10", "m11", "m12", "m13", "m14", "m15", "m19", "m20", "m21"
        )));

    // Allow spaces and minimal punctuation as OCR often fragments the tiny logo.
    // Disallow matches embedded inside a longer word, card number or release year.
    private static final Pattern LOGO = Pattern.compile(
        "(?<![A-Z0-9])M[\\s.:-]*([12IL])[\\s.:-]*([0-9OIL])(?![A-Z0-9])"
    );

    static boolean supported(String set) {
        return set != null && LETTERED_CORE_SETS.contains(
            set.trim().toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Extract exactly one unambiguous M-number wordmark that is also among
     * the catalog printing candidates. Return "" for no/ambiguous match.
     *
     * Limited OCR confusables: I/l -> 1, O -> 0 in numeric positions.
     * Never infer a set from release year, card name, or candidate ranking.
     */
    static String read(String text, Set<String> candidates) {
        if (text == null || candidates == null || candidates.isEmpty()) return "";

        String upper = text.toUpperCase(Locale.ROOT);
        Matcher matcher = LOGO.matcher(upper);
        String found = "";
        while (matcher.find()) {
            char tens = numeric(matcher.group(1).charAt(0));
            char ones = numeric(matcher.group(2).charAt(0));
            String set = "m" + tens + ones;
            if (!supported(set) || !candidates.contains(set)) continue;
            if (!found.isEmpty() && !found.equals(set)) return "";
            found = set;
        }
        return found;
    }

    private static char numeric(char character) {
        if (character == 'I' || character == 'L') return '1';
        if (character == 'O') return '0';
        return character;
    }
}
