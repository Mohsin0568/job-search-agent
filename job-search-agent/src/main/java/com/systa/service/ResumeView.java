package com.systa.service;

import com.systa.model.TailoredResumeContent;

import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * A tailored resume as the templates see it: the LLM's content with blanks dropped, text trimmed, and
 * characters the template's fonts cannot draw (emoji, Chinese, control characters) left out - so a
 * template only has to ask whether a value or list is there, never clean it.
 * Public, with every value a method, because Thymeleaf reads it by reflection.
 */
public record ResumeView(String name, String headline, List<String> contact, String summary, List<String> skills,
                         List<Role> experience, List<Qualification> education, List<String> certifications,
                         List<Section> additionalSections) {

    public record Role(String title, String company, String location, String dates, List<String> bullets) {}

    public record Qualification(String qualification, String institution, String dates, String details) {}

    public record Section(String heading, List<String> items) {}

    static ResumeView of(final TailoredResumeContent content, final IntPredicate drawable) {
        final Cleaner clean = new Cleaner(drawable);
        return new ResumeView(
                clean.text(content.name()),
                clean.text(content.headline()),
                clean.texts(content.contact()),
                clean.text(content.summary()),
                clean.texts(content.skills()),
                orEmpty(content.experience()).stream()
                        .filter(Objects::nonNull)
                        .map(role -> new Role(clean.text(role.title()), clean.text(role.company()),
                                clean.text(role.location()), clean.text(role.dates()), clean.texts(role.bullets())))
                        .filter(role -> role.title() != null || role.company() != null || !role.bullets().isEmpty())
                        .toList(),
                orEmpty(content.education()).stream()
                        .filter(Objects::nonNull)
                        .map(entry -> new Qualification(clean.text(entry.qualification()),
                                clean.text(entry.institution()), clean.text(entry.dates()),
                                clean.text(entry.details())))
                        .filter(entry -> entry.qualification() != null || entry.institution() != null)
                        .toList(),
                clean.texts(content.certifications()),
                orEmpty(content.additionalSections()).stream()
                        .filter(Objects::nonNull)
                        .map(section -> new Section(clean.text(section.heading()), clean.texts(section.items())))
                        // A list with no heading would sit under the section before it, as if it belonged there.
                        .filter(section -> section.heading() != null && !section.items().isEmpty())
                        .toList());
    }

    private static <T> List<T> orEmpty(final List<T> values) {
        return values == null ? List.of() : values;
    }

    private record Cleaner(IntPredicate drawable) {

        /** The text as it can be drawn, or null when nothing of it is left. Line breaks are kept. */
        String text(final String value) {
            if (value == null) {
                return null;
            }
            final StringBuilder kept = new StringBuilder(value.length());
            value.codePoints().forEach(codePoint -> {
                if (codePoint == '\n') {
                    kept.append('\n');
                } else if (codePoint == '\r') {
                    // "\r\n" becomes one line break, from its "\n".
                } else if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    kept.append(' ');
                } else if (!Character.isISOControl(codePoint) && drawable.test(codePoint)) {
                    kept.appendCodePoint(codePoint);
                }
            });
            final String stripped = kept.toString().strip();
            return stripped.isEmpty() ? null : stripped;
        }

        List<String> texts(final List<String> values) {
            return orEmpty(values).stream().map(this::text).filter(Objects::nonNull).toList();
        }
    }
}
