package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A known employer, offered as an autocomplete suggestion and listed by sector on the profile page.
 * The id is the normalised name, so re-seeding the same company replaces it rather than duplicating it.
 */
@Document(collection = "companies")
public record Company(
        @Id String id,
        String name,
        List<String> aliases,
        List<String> searchKeys,
        Sector sector
) implements Suggestable {}
