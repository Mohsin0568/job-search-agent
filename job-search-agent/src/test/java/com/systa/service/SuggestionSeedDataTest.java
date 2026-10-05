package com.systa.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the bundled seed files against typos that would break seeding or make suggestions ambiguous. */
class SuggestionSeedDataTest {

    private static List<SuggestionSeeder.Seed> load(final String file) {
        return SuggestionSeeder.loadSeedFile(JsonMapper.builder().build(), file);
    }

    @ParameterizedTest
    @ValueSource(strings = {SuggestionSeeder.COMPANIES_FILE, SuggestionSeeder.SKILLS_FILE})
    void seedFileLoads(final String file) {
        final List<SuggestionSeeder.Seed> seeds = load(file);

        assertThat(seeds).hasSizeGreaterThan(200);
        assertThat(seeds).allSatisfy(seed -> assertThat(SuggestionNames.normalize(seed.name())).isNotBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {SuggestionSeeder.COMPANIES_FILE, SuggestionSeeder.SKILLS_FILE})
    void namesAreUniqueAfterNormalisation(final String file) {
        // The normalised name is the document id - a clash would silently overwrite an entry.
        final Map<String, String> byId = new HashMap<>();
        for (final SuggestionSeeder.Seed seed : load(file)) {
            final String previous = byId.put(SuggestionNames.normalize(seed.name()), seed.name());
            assertThat(previous).as("duplicate of %s", seed.name()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {SuggestionSeeder.COMPANIES_FILE, SuggestionSeeder.SKILLS_FILE})
    void aliasesDoNotShadowAnotherEntrysName(final String file) {
        final List<SuggestionSeeder.Seed> seeds = load(file);
        final Map<String, String> names = new HashMap<>();
        seeds.forEach(seed -> names.put(SuggestionNames.normalize(seed.name()), seed.name()));

        for (final SuggestionSeeder.Seed seed : seeds) {
            for (final String alias : seed.aliases()) {
                final String owner = names.get(SuggestionNames.normalize(alias));
                assertThat(owner == null || owner.equals(seed.name()))
                        .as("alias '%s' of %s is also the entry %s", alias, seed.name(), owner)
                        .isTrue();
            }
        }
    }
}
