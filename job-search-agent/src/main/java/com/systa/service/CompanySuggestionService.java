package com.systa.service;

import com.systa.model.Company;
import com.systa.model.CompanySuggestion;
import com.systa.repository.CompanyRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class CompanySuggestionService {

    // Mongo returns matches in storage order, not by relevance, so rank a generous candidate set in
    // memory - otherwise a short prefix could cut off the best match. The seeded list is a few hundred.
    private static final int CANDIDATES = 200;

    private final CompanyRepository companyRepository;

    public CompanySuggestionService(final CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<CompanySuggestion> suggest(final String query, final int limit) {
        final String prefix = CompanyNames.normalize(query);
        if (prefix.isEmpty()) {
            return List.of();
        }

        return companyRepository.findBySearchKeyPrefix(prefix, Limit.of(CANDIDATES)).stream()
                .sorted(Comparator
                        .comparingInt((Company company) -> rank(company, prefix))
                        .thenComparingInt(company -> company.name().length())
                        .thenComparing(Company::name, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(company -> new CompanySuggestion(company.name()))
                .toList();
    }

    // 0: the name itself starts with the query ("del" -> "Deliveroo")
    // 1: an alias starts with it ("m&s" -> "Marks & Spencer")
    // 2: a later word starts with it ("bank" -> "Lloyds Banking Group")
    private static int rank(final Company company, final String prefix) {
        if (CompanyNames.normalize(company.name()).startsWith(prefix)) {
            return 0;
        }
        final boolean aliasMatch = company.aliases().stream()
                .anyMatch(alias -> CompanyNames.normalize(alias).startsWith(prefix));
        return aliasMatch ? 1 : 2;
    }
}
