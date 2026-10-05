package com.systa.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.regex.Pattern;

/** Prefix search over the searchKeys of a seeded suggestion collection. */
@NoRepositoryBean
public interface SuggestionRepository<T> extends MongoRepository<T, String> {

    // Normalised prefixes only ever contain these characters, so they're safe to use in a regex unescaped.
    Pattern NORMALISED_PREFIX = Pattern.compile("[a-z0-9 ]+");

    /**
     * Entries with any search key starting with the given normalised prefix. A derived
     * "SearchKeysStartingWith" query doesn't work here: on an array field Spring Data compares the
     * whole element, so only exact matches were returned.
     */
    default List<T> findBySearchKeyPrefix(final String normalisedPrefix, final Limit limit) {
        if (!NORMALISED_PREFIX.matcher(normalisedPrefix).matches()) {
            throw new IllegalArgumentException("Prefix must be normalised: " + normalisedPrefix);
        }
        return findBySearchKeysMatching("^" + normalisedPrefix, limit);
    }

    // Anchored and case-sensitive (keys are stored lowercase), so Mongo can use the searchKeys index.
    @Query("{ 'searchKeys': { $regex: ?0 } }")
    List<T> findBySearchKeysMatching(final String regex, final Limit limit);
}
