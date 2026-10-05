package com.systa.controller;

import com.systa.model.CompanySuggestion;
import com.systa.service.CompanySuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private static final int MAX_LIMIT = 20;
    private static final int MAX_QUERY_LENGTH = 100;

    private final CompanySuggestionService companySuggestionService;

    public CompanyController(final CompanySuggestionService companySuggestionService) {
        this.companySuggestionService = companySuggestionService;
    }

    // Autocomplete for the profile's company list, e.g. ?q=del -> [{"name":"Deliveroo"}].
    @GetMapping("/suggest")
    public List<CompanySuggestion> suggest(@RequestParam(defaultValue = "") final String q,
                                           @RequestParam(defaultValue = "10") final int limit) {
        if (q.length() > MAX_QUERY_LENGTH) {
            return List.of();
        }
        return companySuggestionService.suggest(q, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
