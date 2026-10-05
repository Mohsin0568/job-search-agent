package com.systa.service;

import com.systa.model.Suggestable;
import com.systa.model.Suggestion;
import com.systa.repository.CompanyRepository;
import com.systa.repository.SkillRepository;
import com.systa.repository.SuggestionRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Autocomplete for the profile form's company and skill lists. */
@Service
public class SuggestionService {

    // Mongo returns matches in storage order, not by relevance, so rank a generous candidate set in
    // memory - otherwise a short prefix could cut off the best match. The seeded lists are a few hundred.
    private static final int CANDIDATES = 200;

    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;

    public SuggestionService(final CompanyRepository companyRepository, final SkillRepository skillRepository) {
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
    }

    public List<Suggestion> suggestCompanies(final String query, final int limit) {
        return suggest(companyRepository, query, limit);
    }

    public List<Suggestion> suggestSkills(final String query, final int limit) {
        return suggest(skillRepository, query, limit);
    }

    private static <T extends Suggestable> List<Suggestion> suggest(final SuggestionRepository<T> repository,
                                                                     final String query, final int limit) {
        final String prefix = SuggestionNames.normalize(query);
        if (prefix.isEmpty()) {
            return List.of();
        }

        return repository.findBySearchKeyPrefix(prefix, Limit.of(CANDIDATES)).stream()
                .sorted(Comparator
                        .comparingInt((T entry) -> rank(entry, prefix))
                        .thenComparingInt(entry -> entry.name().length())
                        .thenComparing(Suggestable::name, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(entry -> new Suggestion(entry.name()))
                .toList();
    }

    // 0: the name itself starts with the query ("del" -> "Deliveroo")
    // 1: an alias starts with it ("m&s" -> "Marks & Spencer", "k8s" -> "Kubernetes")
    // 2: a later word starts with it ("bank" -> "Lloyds Banking Group")
    private static int rank(final Suggestable entry, final String prefix) {
        if (SuggestionNames.normalize(entry.name()).startsWith(prefix)) {
            return 0;
        }
        final boolean aliasMatch = entry.aliases().stream()
                .anyMatch(alias -> SuggestionNames.normalize(alias).startsWith(prefix));
        return aliasMatch ? 1 : 2;
    }
}
