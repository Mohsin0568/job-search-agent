package com.systa.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyNamesTest {

    @Test
    void normalize_lowercasesStripsAccentsAndPunctuation() {
        assertThat(CompanyNames.normalize("Nestlé")).isEqualTo("nestle");
        assertThat(CompanyNames.normalize("  Marks & Spencer ")).isEqualTo("marks spencer");
        assertThat(CompanyNames.normalize("J.P. Morgan")).isEqualTo("j p morgan");
        assertThat(CompanyNames.normalize("Booking.com")).isEqualTo("booking com");
        assertThat(CompanyNames.normalize(null)).isEmpty();
        assertThat(CompanyNames.normalize("&&&")).isEmpty();
    }

    @Test
    void searchKeys_coverEveryWordStartOfNameAndAliases() {
        assertThat(CompanyNames.searchKeys("Lloyds Banking Group", List.of("Lloyds Bank")))
                .containsExactly("lloyds banking group", "banking group", "group", "lloyds bank", "bank");
    }

    @Test
    void searchKeys_dropDuplicates() {
        assertThat(CompanyNames.searchKeys("Deliveroo", List.of("DELIVEROO"))).containsExactly("deliveroo");
    }
}
