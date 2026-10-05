package com.systa.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionNamesTest {

    @Test
    void normalize_lowercasesStripsAccentsAndPunctuation() {
        assertThat(SuggestionNames.normalize("Nestlé")).isEqualTo("nestle");
        assertThat(SuggestionNames.normalize("  Marks & Spencer ")).isEqualTo("marks spencer");
        assertThat(SuggestionNames.normalize("J.P. Morgan")).isEqualTo("j p morgan");
        assertThat(SuggestionNames.normalize("Booking.com")).isEqualTo("booking com");
        assertThat(SuggestionNames.normalize(null)).isEmpty();
        assertThat(SuggestionNames.normalize("&&&")).isEmpty();
    }

    @Test
    void normalize_keepsSkillsThatDifferOnlyBySymbolDistinct() {
        assertThat(SuggestionNames.normalize("C")).isEqualTo("c");
        assertThat(SuggestionNames.normalize("C++")).isEqualTo("c plus plus");
        assertThat(SuggestionNames.normalize("C#")).isEqualTo("c sharp");
        assertThat(SuggestionNames.normalize("F#")).isEqualTo("f sharp");
    }

    @Test
    void normalize_spellsOutALeadingDotOnly() {
        assertThat(SuggestionNames.normalize(".NET")).isEqualTo("dot net");
        assertThat(SuggestionNames.normalize("ASP.NET")).isEqualTo("asp net");
        assertThat(SuggestionNames.normalize("Node.js")).isEqualTo("node js");
        assertThat(SuggestionNames.normalize("Warner Bros. Discovery")).isEqualTo("warner bros discovery");
    }

    @Test
    void searchKeys_coverEveryWordStartOfNameAndAliases() {
        assertThat(SuggestionNames.searchKeys("Lloyds Banking Group", List.of("Lloyds Bank")))
                .containsExactly("lloyds banking group", "banking group", "group", "lloyds bank", "bank");
        // "net" finds .NET, and so does "dotnet" via the alias.
        assertThat(SuggestionNames.searchKeys(".NET", List.of("DotNet"))).containsExactly("dot net", "net", "dotnet");
    }

    @Test
    void searchKeys_dropDuplicates() {
        assertThat(SuggestionNames.searchKeys("Deliveroo", List.of("DELIVEROO"))).containsExactly("deliveroo");
    }
}
