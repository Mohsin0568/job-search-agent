package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A known skill offered as an autocomplete suggestion. The id is the normalised name, so re-seeding
 * the same skill replaces it rather than duplicating it.
 */
@Document(collection = "skills")
public record Skill(
        @Id String id,
        String name,
        List<String> aliases,
        List<String> searchKeys
) implements Suggestable {}
