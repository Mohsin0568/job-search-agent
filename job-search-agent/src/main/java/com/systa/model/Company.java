package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A known employer offered as an autocomplete suggestion. The id is the normalised name, so re-seeding
 * the same company replaces it rather than duplicating it.
 */
@Document(collection = "companies")
public record Company(
        @Id String id,
        String name,
        List<String> aliases,
        // Normalised word-start suffixes of the name and aliases - see CompanyNames.searchKeys.
        List<String> searchKeys
) {}
