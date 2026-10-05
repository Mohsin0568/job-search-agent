package com.systa.controller;

import com.systa.model.Suggestion;
import com.systa.service.SuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SuggestionService suggestionService;

    public SkillController(final SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    // Autocomplete for the profile's skill list, e.g. ?q=spr -> [{"name":"Spring"},{"name":"Spring Boot"}].
    @GetMapping("/suggest")
    public List<Suggestion> suggest(@RequestParam(defaultValue = "") final String q,
                                    @RequestParam(defaultValue = "10") final int limit) {
        if (q.length() > SuggestionLimits.MAX_QUERY_LENGTH) {
            return List.of();
        }
        return suggestionService.suggestSkills(q, SuggestionLimits.clamp(limit));
    }
}
