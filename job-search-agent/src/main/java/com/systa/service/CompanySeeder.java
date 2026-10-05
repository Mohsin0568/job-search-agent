package com.systa.service;

import com.systa.model.Company;
import com.systa.repository.CompanyRepository;
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
 * Loads the bundled company list into Mongo on startup. Idempotent: ids are derived from the name,
 * so re-running replaces existing entries and picks up any added to the seed file.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "companies.seed-on-startup", havingValue = "true", matchIfMissing = true)
public class CompanySeeder implements ApplicationRunner {

    static final String SEED_FILE = "companies/companies.json";

    private final CompanyRepository companyRepository;
    private final MongoTemplate mongoTemplate;
    private final JsonMapper jsonMapper;

    public CompanySeeder(final CompanyRepository companyRepository, final MongoTemplate mongoTemplate,
                         final JsonMapper jsonMapper) {
        this.companyRepository = companyRepository;
        this.mongoTemplate = mongoTemplate;
        this.jsonMapper = jsonMapper;
    }

    record CompanySeed(String name, List<String> aliases) {}

    @Override
    public void run(final ApplicationArguments args) {
        try {
            final List<Company> companies = loadSeedFile(jsonMapper).stream()
                    .map(seed -> toCompany(seed.name(), seed.aliases()))
                    .toList();
            mongoTemplate.indexOps(Company.class).createIndex(new Index().on("searchKeys", Sort.Direction.ASC));
            companyRepository.saveAll(companies);
            log.info("Seeded {} companies for autocomplete", companies.size());
        } catch (final RuntimeException e) {
            // Suggestions are a convenience - don't stop the app starting if Mongo is briefly unavailable.
            log.error("Failed to seed companies for autocomplete; suggestions may be incomplete", e);
        }
    }

    static Company toCompany(final String name, final List<String> aliases) {
        final List<String> safeAliases = aliases == null ? List.of() : List.copyOf(aliases);
        return new Company(CompanyNames.normalize(name), name, safeAliases, CompanyNames.searchKeys(name, safeAliases));
    }

    static List<CompanySeed> loadSeedFile(final JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return jsonMapper.readValue(in, new TypeReference<List<CompanySeed>>() {});
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read " + SEED_FILE, e);
        }
    }
}
