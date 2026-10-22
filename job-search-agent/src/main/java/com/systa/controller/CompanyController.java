package com.systa.controller;

import com.systa.model.CompanyDto;
import com.systa.model.Suggestion;
import com.systa.service.SuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final SuggestionService suggestionService;

    public CompanyController(final SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    // Every known company with its sector, for browsing them all on the profile page.
    @GetMapping
    public List<CompanyDto> all() {
        return suggestionService.allCompanies();
    }

    // Autocomplete for the profile's company list, e.g. ?q=del -> [{"name":"Deliveroo"}].
    @GetMapping("/suggest")
    public List<Suggestion> suggest(@RequestParam(defaultValue = "") final String q,
                                    @RequestParam(defaultValue = "10") final int limit) {
        if (q.length() > SuggestionLimits.MAX_QUERY_LENGTH) {
            return List.of();
        }
        return suggestionService.suggestCompanies(q, SuggestionLimits.clamp(limit));
    }
}
