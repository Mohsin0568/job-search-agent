package com.systa.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Normalisation shared by stored search keys and incoming queries, so both match the same way. */
public final class SuggestionNames {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    // A dot that starts a word, as in ".NET" - unlike the ones inside "Node.js" or "Booking.com".
    private static final Pattern LEADING_DOT = Pattern.compile("(^|\\s)\\.(?=[a-z0-9])");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private SuggestionNames() {
    }

    /**
     * Lowercases and strips accents and punctuation: "Nestlé" becomes "nestle", "Marks &amp; Spencer"
     * becomes "marks spencer". The symbols that distinguish skills are spelt out first, so "C", "C++"
     * and "C#" stay different ("c", "c plus plus", "c sharp") and ".NET" becomes "dot net".
     */
    public static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        String text = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace("+", " plus ")
                .replace("#", " sharp ");
        text = LEADING_DOT.matcher(text).replaceAll("$1dot ");
        return NON_ALPHANUMERIC.matcher(text).replaceAll(" ").trim();
    }

    /**
     * Every normalised suffix starting at a word boundary, for the name and each alias. A prefix query
     * against these matches the start of any word: "bank" finds "Lloyds Banking Group".
     */
    public static List<String> searchKeys(final String name, final List<String> aliases) {
        final Set<String> keys = new LinkedHashSet<>();
        final List<String> names = new ArrayList<>();
        names.add(name);
        if (aliases != null) {
            names.addAll(aliases);
        }
        for (final String each : names) {
            final String[] words = normalize(each).split(" ");
            for (int i = 0; i < words.length; i++) {
                final String key = String.join(" ", List.of(words).subList(i, words.length));
                if (!key.isBlank()) {
                    keys.add(key);
                }
            }
        }
        return List.copyOf(keys);
    }
}
