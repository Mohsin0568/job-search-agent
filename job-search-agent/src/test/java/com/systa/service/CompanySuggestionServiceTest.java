package com.systa.service;

import com.systa.model.Company;
import com.systa.model.CompanySuggestion;
import com.systa.repository.CompanyRepository;
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
 * Runs suggestions against the real seed list, with the repository faked to apply the same
 * "any search key starts with" rule as its $regex query. The query itself needs a real Mongo to verify.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanySuggestionServiceTest {

    private static final List<Company> SEEDED = CompanySeeder.loadSeedFile(JsonMapper.builder().build()).stream()
            .map(seed -> CompanySeeder.toCompany(seed.name(), seed.aliases()))
            .toList();

    @Mock
    private CompanyRepository companyRepository;

    private CompanySuggestionService service;

    @BeforeEach
    void setUp() {
        when(companyRepository.findBySearchKeyPrefix(anyString(), any(Limit.class))).thenAnswer(invocation -> {
            final String prefix = invocation.getArgument(0);
            final Limit limit = invocation.getArgument(1);
            return SEEDED.stream()
                    .filter(company -> company.searchKeys().stream().anyMatch(key -> key.startsWith(prefix)))
                    .limit(limit.max())
                    .toList();
        });
        service = new CompanySuggestionService(companyRepository);
    }

    private List<String> suggest(final String query) {
        return service.suggest(query, 10).stream().map(CompanySuggestion::name).toList();
    }

    @Test
    void del_suggestsDeliveroo() {
        assertThat(suggest("del")).contains("Deliveroo", "Deloitte", "Dell Technologies");
    }

    @Test
    void isCaseAndAccentInsensitive() {
        assertThat(suggest("DELIV")).containsExactly("Deliveroo");
        assertThat(suggest("nestle")).containsExactly("Nestlé");
    }

    @Test
    void matchesAliases_andReturnsTheCanonicalName() {
        assertThat(suggest("m&s")).first().isEqualTo("Marks & Spencer");
        assertThat(suggest("jp morgan")).containsExactly("JPMorgan Chase");
        assertThat(suggest("facebook")).containsExactly("Meta");
    }

    @Test
    void matchesLaterWords_afterNamePrefixMatches() {
        final List<String> results = suggest("bank");
        assertThat(results).contains("Bank of America", "Bank of England", "Lloyds Banking Group");
        assertThat(results.indexOf("Bank of England")).isLessThan(results.indexOf("Lloyds Banking Group"));
    }

    @Test
    void respectsLimit() {
        assertThat(service.suggest("b", 5)).hasSize(5);
    }

    @Test
    void unknownCompany_returnsNothing() {
        assertThat(suggest("xyzzy")).isEmpty();
    }

    @Test
    void blankOrPunctuationOnlyQuery_doesNotHitTheDatabase() {
        assertThat(suggest("  ")).isEmpty();
        assertThat(suggest("&&")).isEmpty();
        verifyNoInteractions(companyRepository);
    }
}
