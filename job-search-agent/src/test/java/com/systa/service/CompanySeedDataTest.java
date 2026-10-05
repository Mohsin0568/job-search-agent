package com.systa.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the bundled companies.json against typos that would break seeding or make suggestions ambiguous. */
class CompanySeedDataTest {

    private final List<CompanySeeder.CompanySeed> seeds = CompanySeeder.loadSeedFile(JsonMapper.builder().build());

    @Test
    void seedFileLoads() {
        assertThat(seeds).hasSizeGreaterThan(300);
        assertThat(seeds).extracting(CompanySeeder.CompanySeed::name).contains("Deliveroo", "Marks & Spencer");
        assertThat(seeds).allSatisfy(seed -> assertThat(CompanyNames.normalize(seed.name())).isNotBlank());
    }

    @Test
    void namesAreUniqueAfterNormalisation() {
        // The normalised name is the document id - a clash would silently overwrite a company.
        final Map<String, String> byId = new HashMap<>();
        for (final CompanySeeder.CompanySeed seed : seeds) {
            final String previous = byId.put(CompanyNames.normalize(seed.name()), seed.name());
            assertThat(previous).as("duplicate of %s", seed.name()).isNull();
        }
    }

    @Test
    void aliasesDoNotShadowAnotherCompanysName() {
        final Map<String, String> names = new HashMap<>();
        seeds.forEach(seed -> names.put(CompanyNames.normalize(seed.name()), seed.name()));

        for (final CompanySeeder.CompanySeed seed : seeds) {
            if (seed.aliases() == null) {
                continue;
            }
            for (final String alias : seed.aliases()) {
                final String owner = names.get(CompanyNames.normalize(alias));
                assertThat(owner == null || owner.equals(seed.name()))
                        .as("alias '%s' of %s is also the company %s", alias, seed.name(), owner)
                        .isTrue();
            }
        }
    }
}
