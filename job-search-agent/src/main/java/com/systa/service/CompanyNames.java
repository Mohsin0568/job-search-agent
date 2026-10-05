package com.systa.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Normalisation shared by stored company search keys and incoming queries, so both match the same way. */
public final class CompanyNames {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private CompanyNames() {
    }

    /** "Nestlé", "NESTLE " and "nestle" all become "nestle"; "Marks & Spencer" becomes "marks spencer". */
    public static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        final String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
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
