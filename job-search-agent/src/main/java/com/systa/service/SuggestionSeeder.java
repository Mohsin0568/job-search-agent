package com.systa.service;

import com.systa.model.Company;
import com.systa.model.Skill;
import com.systa.repository.CompanyRepository;
import com.systa.repository.SkillRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the bundled company and skill lists into Mongo on startup. Idempotent: ids are derived from
 * the name, so re-running replaces existing entries and picks up any added to the seed files.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "suggestions.seed-on-startup", havingValue = "true", matchIfMissing = true)
public class SuggestionSeeder implements ApplicationRunner {

    static final String COMPANIES_FILE = "companies/companies.json";
    static final String SKILLS_FILE = "skills/skills.json";

    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final MongoTemplate mongoTemplate;
    private final JsonMapper jsonMapper;

    public SuggestionSeeder(final CompanyRepository companyRepository, final SkillRepository skillRepository,
                            final MongoTemplate mongoTemplate, final JsonMapper jsonMapper) {
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
        this.mongoTemplate = mongoTemplate;
        this.jsonMapper = jsonMapper;
    }

    record Seed(String name, List<String> aliases) {

        // Jackson leaves a missing "aliases" as null.
        Seed {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    @Override
    public void run(final ApplicationArguments args) {
        try {
            final List<Company> companies = loadSeedFile(jsonMapper, COMPANIES_FILE).stream()
                    .map(SuggestionSeeder::toCompany)
                    .toList();
            createSearchKeysIndex(Company.class);
            companyRepository.saveAll(companies);

            final List<Skill> skills = loadSeedFile(jsonMapper, SKILLS_FILE).stream()
                    .map(SuggestionSeeder::toSkill)
                    .toList();
            createSearchKeysIndex(Skill.class);
            skillRepository.saveAll(skills);

            log.info("Seeded {} companies and {} skills for autocomplete", companies.size(), skills.size());
        } catch (final RuntimeException e) {
            // Suggestions are a convenience - don't stop the app starting if Mongo is briefly unavailable.
            log.error("Failed to seed autocomplete data; suggestions may be incomplete", e);
        }
    }

    private void createSearchKeysIndex(final Class<?> entityClass) {
        mongoTemplate.indexOps(entityClass).createIndex(new Index().on("searchKeys", Sort.Direction.ASC));
    }

    static Company toCompany(final Seed seed) {
        return new Company(SuggestionNames.normalize(seed.name()), seed.name(), seed.aliases(),
                SuggestionNames.searchKeys(seed.name(), seed.aliases()));
    }

    static Skill toSkill(final Seed seed) {
        return new Skill(SuggestionNames.normalize(seed.name()), seed.name(), seed.aliases(),
                SuggestionNames.searchKeys(seed.name(), seed.aliases()));
    }

    static List<Seed> loadSeedFile(final JsonMapper jsonMapper, final String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return jsonMapper.readValue(in, new TypeReference<List<Seed>>() {});
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
