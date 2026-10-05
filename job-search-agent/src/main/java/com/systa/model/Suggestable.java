package com.systa.model;

import java.util.List;

/** A seeded entry that can be offered as an autocomplete suggestion (a company or a skill). */
public interface Suggestable {

    String name();

    List<String> aliases();

    /** Normalised word-start suffixes of the name and aliases - see SuggestionNames.searchKeys. */
    List<String> searchKeys();
}
