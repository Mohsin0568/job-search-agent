package com.systa.service;

import com.systa.model.Company;
import com.systa.model.Skill;
import com.systa.model.Suggestable;
import com.systa.model.Suggestion;
import com.systa.repository.CompanyRepository;
import com.systa.repository.SkillRepository;
import com.systa.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs suggestions against the real seed lists, with the repositories faked to apply the same
 * "any search key starts with" rule as their $regex query. The query itself needs a real Mongo to verify.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuggestionServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Company> COMPANIES = SuggestionSeeder.loadSeedFile(JSON, SuggestionSeeder.COMPANIES_FILE)
            .stream().map(SuggestionSeeder::toCompany).toList();
    private static final List<Skill> SKILLS = SuggestionSeeder.loadSeedFile(JSON, SuggestionSeeder.SKILLS_FILE)
            .stream().map(SuggestionSeeder::toSkill).toList();

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private SkillRepository skillRepository;

    private SuggestionService service;

    @BeforeEach
    void setUp() {
        fakePrefixSearch(companyRepository, COMPANIES);
        fakePrefixSearch(skillRepository, SKILLS);
        service = new SuggestionService(companyRepository, skillRepository);
    }

    private static <T extends Suggestable> void fakePrefixSearch(final SuggestionRepository<T> repository,
                                                                  final List<T> entries) {
        when(repository.findBySearchKeyPrefix(anyString(), any(Limit.class))).thenAnswer(invocation -> {
            final String prefix = invocation.getArgument(0);
            final Limit limit = invocation.getArgument(1);
            return entries.stream()
                    .filter(entry -> entry.searchKeys().stream().anyMatch(key -> key.startsWith(prefix)))
                    .limit(limit.max())
                    .toList();
        });
    }

    private List<String> companies(final String query) {
        return service.suggestCompanies(query, 10).stream().map(Suggestion::name).toList();
    }

    private List<String> skills(final String query) {
        return service.suggestSkills(query, 10).stream().map(Suggestion::name).toList();
    }

    // --- companies ---

    @Test
    void companies_del_suggestsDeliveroo() {
        assertThat(companies("del")).contains("Deliveroo", "Deloitte", "Dell Technologies");
    }

    @Test
    void companies_areCaseAndAccentInsensitive() {
        assertThat(companies("DELIV")).containsExactly("Deliveroo");
        assertThat(companies("nestle")).containsExactly("Nestlé");
    }

    @Test
    void companies_matchAliases_andReturnTheCanonicalName() {
        assertThat(companies("m&s")).first().isEqualTo("Marks & Spencer");
        assertThat(companies("jp morgan")).containsExactly("JPMorgan Chase");
        assertThat(companies("facebook")).containsExactly("Meta");
    }

    @Test
    void companies_matchLaterWords_afterNamePrefixMatches() {
        final List<String> results = companies("bank");
        assertThat(results).contains("Bank of America", "Bank of England", "Lloyds Banking Group");
        assertThat(results.indexOf("Bank of England")).isLessThan(results.indexOf("Lloyds Banking Group"));
    }

    @Test
    void companies_respectLimit() {
        assertThat(service.suggestCompanies("b", 5)).hasSize(5);
    }

    // --- skills ---

    @Test
    void skills_spr_suggestsSpringFamily_shortestFirst() {
        assertThat(skills("spr")).startsWith("Spring", "Spring AI").contains("Spring Boot");
    }

    @Test
    void skills_keepCFamilyDistinct() {
        assertThat(skills("c")).startsWith("C", "C#", "C++");
        assertThat(skills("c++")).containsExactly("C++");
        assertThat(skills("c#")).containsExactly("C#");
        assertThat(skills("csharp")).containsExactly("C#");
    }

    @Test
    void skills_matchAbbreviations_andReturnTheCanonicalName() {
        assertThat(skills("k8s")).containsExactly("Kubernetes");
        assertThat(skills("postgres")).containsExactly("PostgreSQL");
        assertThat(skills("golang")).containsExactly("Go");
        assertThat(skills("gcp")).containsExactly("Google Cloud");
    }

    @Test
    void skills_handleDotsInNames() {
        assertThat(skills(".net")).first().isEqualTo(".NET");
        assertThat(skills("dotnet")).containsExactly(".NET");
        assertThat(skills("node")).first().isEqualTo("Node.js");
        assertThat(skills("nodejs")).containsExactly("Node.js");
    }

    @Test
    void skills_doNotLeakIntoCompanySuggestions() {
        assertThat(companies("kubern")).isEmpty();
        assertThat(skills("deliveroo")).isEmpty();
    }

    // --- shared ---

    @Test
    void unknownEntry_returnsNothing() {
        assertThat(companies("xyzzy")).isEmpty();
        assertThat(skills("xyzzy")).isEmpty();
    }

    @Test
    void blankOrPunctuationOnlyQuery_doesNotHitTheDatabase() {
        assertThat(companies("  ")).isEmpty();
        assertThat(skills("&&")).isEmpty();
        verifyNoInteractions(companyRepository, skillRepository);
    }
}
