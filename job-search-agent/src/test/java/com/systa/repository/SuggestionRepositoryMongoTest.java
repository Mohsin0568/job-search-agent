package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.Company;
import com.systa.model.Sector;
import com.systa.model.Skill;
import com.systa.service.SuggestionNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.bson.Document;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The prefix query is a $regex on an array field, which only behaves like the real thing against a
 * real MongoDB: a derived "StartingWith" query passed mocked tests but matched whole keys only.
 */
@DataMongoTest
@Import(MongoTestContainer.class)
class SuggestionRepositoryMongoTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void seed() {
        companyRepository.deleteAll();
        skillRepository.deleteAll();
        companyRepository.saveAll(List.of(
                company("Lloyds Banking Group", "LBG"),
                company("Barclays"),
                company("Bank of America", "BofA"),
                company("Nestlé")));
        skillRepository.saveAll(List.of(skill("C"), skill("C++", "cpp"), skill("C#"), skill(".NET")));
    }

    @Test
    void matchesAPartialPrefix_notJustAWholeKey() {
        assertThat(companyNames("llo")).containsExactly("Lloyds Banking Group");
        assertThat(companyNames("barcl")).containsExactly("Barclays");
    }

    @Test
    void matchesTheStartOfAnyWordInTheName() {
        assertThat(companyNames("bank")).containsExactlyInAnyOrder("Lloyds Banking Group", "Bank of America");
        assertThat(companyNames("banking gr")).containsExactly("Lloyds Banking Group");
    }

    @Test
    void doesNotMatchInTheMiddleOfAWord() {
        assertThat(companyNames("oyds")).isEmpty();
        assertThat(companyNames("anking")).isEmpty();
    }

    @Test
    void matchesAliases() {
        assertThat(companyNames("lbg")).containsExactly("Lloyds Banking Group");
        assertThat(companyNames("bofa")).containsExactly("Bank of America");
    }

    @Test
    void matchesNamesWithAccentsByTheirNormalisedForm() {
        assertThat(companyNames("nestle")).containsExactly("Nestlé");
    }

    @Test
    void returnsNoMoreThanTheLimit() {
        assertThat(companyRepository.findBySearchKeyPrefix("b", Limit.of(1))).hasSize(1);
        assertThat(companyRepository.findBySearchKeyPrefix("b", Limit.of(5))).hasSize(3);
    }

    @Test
    void keepsSkillsThatDifferOnlyBySymbolsApart() {
        assertThat(skillNames(SuggestionNames.normalize("C+"))).containsExactly("C++");
        assertThat(skillNames(SuggestionNames.normalize("C#"))).containsExactly("C#");
        assertThat(skillNames(SuggestionNames.normalize(".NE"))).containsExactly(".NET");
        assertThat(skillNames("c")).containsExactlyInAnyOrder("C", "C++", "C#");
    }

    @Test
    void companiesAndSkillsAreSearchedSeparately() {
        assertThat(skillNames("barcl")).isEmpty();
        assertThat(companyNames("cpp")).isEmpty();
    }

    private List<String> companyNames(final String prefix) {
        return companyRepository.findBySearchKeyPrefix(prefix, Limit.of(10)).stream().map(Company::name).toList();
    }

    @Test
    void listsOnlyTheSeededCompanies_skippingOtherDocumentsInTheCollection() {
        // As imported by hand into a local database: keyed by companyName, with sectors of its own.
        mongoTemplate.getCollection("companies").insertOne(new Document()
                .append("companyName", "Goldman Sachs")
                .append("sector", "Banking & Capital Markets")
                .append("source", "curated-uk-java-employer-list"));
        // Seeded under a sector that has since been renamed or removed.
        mongoTemplate.getCollection("companies").insertOne(new Document()
                .append("_id", "old corp")
                .append("name", "Old Corp")
                .append("sector", "NO_LONGER_A_SECTOR"));
        // Seeded before companies had sectors.
        mongoTemplate.getCollection("companies").insertOne(new Document()
                .append("_id", "acme")
                .append("name", "Acme")
                .append("aliases", List.of())
                .append("searchKeys", List.of("acme")));

        assertThat(companyRepository.findAllListable()).extracting(Company::name, Company::sector)
                .containsExactlyInAnyOrder(
                        tuple("Lloyds Banking Group", Sector.BIG_TECH_AND_SOFTWARE),
                        tuple("Barclays", Sector.BIG_TECH_AND_SOFTWARE),
                        tuple("Bank of America", Sector.BIG_TECH_AND_SOFTWARE),
                        tuple("Nestlé", Sector.BIG_TECH_AND_SOFTWARE),
                        tuple("Acme", null));
    }

    @Test
    void keepsEachCompanysSector() {
        final Company monzo = new Company("monzo", "Monzo", List.of(), SuggestionNames.searchKeys("Monzo", List.of()),
                Sector.FINTECH);
        companyRepository.save(monzo);

        assertThat(companyRepository.findById("monzo")).contains(monzo);
    }

    private List<String> skillNames(final String prefix) {
        return skillRepository.findBySearchKeyPrefix(prefix, Limit.of(10)).stream().map(Skill::name).toList();
    }

    private static Company company(final String name, final String... aliases) {
        final List<String> aliasList = List.of(aliases);
        return new Company(SuggestionNames.normalize(name), name, aliasList,
                SuggestionNames.searchKeys(name, aliasList), Sector.BIG_TECH_AND_SOFTWARE);
    }

    private static Skill skill(final String name, final String... aliases) {
        final List<String> aliasList = List.of(aliases);
        return new Skill(SuggestionNames.normalize(name), name, aliasList,
                SuggestionNames.searchKeys(name, aliasList));
    }
}
