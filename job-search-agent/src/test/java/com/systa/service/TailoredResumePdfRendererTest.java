package com.systa.service;

import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoredResumeContent.Education;
import com.systa.model.TailoredResumeContent.Experience;
import com.systa.model.TailoredResumeContent.Section;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TailoredResumePdfRendererTest {

    private final TailoredResumePdfRenderer renderer = new TailoredResumePdfRenderer();

    static TailoredResumeContent sample() {
        return new TailoredResumeContent(
                "Jane Doe",
                "Senior Java Developer",
                List.of("jane.doe@example.com", "+44 7700 900123", "London, UK", "linkedin.com/in/janedoe"),
                "Backend engineer with nine years of experience building payment and ledger systems on the JVM. "
                        + "Leads small teams, owns services from design through to production, and has run the "
                        + "migration of a monolith to event-driven services handling 40 million requests a day.",
                List.of("Java", "Spring Boot", "Kafka", "PostgreSQL", "AWS", "Kubernetes", "Terraform", "REST APIs",
                        "Event-driven architecture", "Mentoring"),
                List.of(new Experience("Senior Software Engineer", "Globex Payments", "London, UK", "Mar 2021 – Present",
                                List.of("Led the redesign of the card authorisation service in Java and Spring Boot, cutting p99 latency from 480 ms to 120 ms.",
                                        "Introduced Kafka-based event sourcing for the ledger, removing nightly reconciliation jobs.",
                                        "Mentored four engineers and ran the team's design review process.")),
                        new Experience("Software Engineer", "Initech", "Manchester, UK", "Sep 2017 – Feb 2021",
                                List.of("Built REST APIs for merchant onboarding used by 3,000 businesses.",
                                        "Moved deployments to Kubernetes on AWS with Terraform, taking releases from monthly to daily."))),
                List.of(new Education("BSc Computer Science (First Class)", "University of Leeds", "2014 – 2017", null)),
                List.of("AWS Certified Solutions Architect – Associate (2023)"),
                List.of(new Section("Languages", List.of("English (native)", "French (conversational)"))));
    }

    @Test
    void writesEverySectionOfTheResume() throws IOException {
        final String text = textOf(renderer.render(sample()));

        assertThat(text).contains("Jane Doe", "Senior Java Developer", "jane.doe@example.com", "London, UK",
                "SUMMARY", "Backend engineer with nine years",
                "SKILLS", "Spring Boot", "Kafka",
                "EXPERIENCE", "Senior Software Engineer — Globex Payments", "Mar 2021 – Present",
                "cutting p99 latency", "Software Engineer — Initech", "Sep 2017 – Feb 2021",
                "EDUCATION", "BSc Computer Science (First Class) — University of Leeds", "2014 – 2017",
                "CERTIFICATIONS", "AWS Certified Solutions Architect",
                "LANGUAGES", "French (conversational)");
        // In reading order: the header, then the sections as laid out.
        assertThat(text.indexOf("Jane Doe")).isLessThan(text.indexOf("SUMMARY"));
        assertThat(text.indexOf("SUMMARY")).isLessThan(text.indexOf("EXPERIENCE"));
        assertThat(text.indexOf("EXPERIENCE")).isLessThan(text.indexOf("EDUCATION"));
    }

    @Test
    void fitsAnOrdinaryResumeOnOnePage() throws IOException {
        try (PDDocument document = Loader.loadPDF(renderer.render(sample()))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void runsALongResumeOntoMorePages_withoutLosingAnyOfIt() throws IOException {
        final List<Experience> manyRoles = IntStream.rangeClosed(1, 14)
                .mapToObj(i -> new Experience("Engineer " + i, "Company " + i, "London, UK", "20" + (10 + i),
                        List.of("Delivered project number " + i + " end to end across three teams and two time zones.",
                                "Second point for role " + i + ".", "Third point for role " + i + ".")))
                .toList();
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", null, List.of(), null, List.of(),
                manyRoles, List.of(), List.of(), List.of());

        final byte[] pdf = renderer.render(content);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        final String text = textOf(pdf);
        for (int i = 1; i <= 14; i++) {
            assertThat(text).contains("Engineer " + i + " — Company " + i, "Third point for role " + i + ".");
        }
    }

    @Test
    void leavesOutSectionsWithNothingInThem() throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", " ", null, "", null,
                Arrays.asList((Experience) null), null, List.of("", " "),
                Arrays.asList(null, new Section("Projects", List.of()), new Section(null, List.of("Orphan item"))));

        final String text = textOf(renderer.render(content));

        assertThat(text.strip()).isEqualTo("Jane Doe");
    }

    @Test
    void writesNamesInOtherAlphabets_andSkipsWhatTheFontCannotDraw() throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Łukasz Żółć", "Désirée Nguyễn — Ελληνικά — Привет",
                List.of("emoji 🚀 rocket", "tab\tand\u0007bell"), "中文 Chinese is skipped, this stays.",
                List.of(), List.of(), List.of(), List.of(), List.of());

        final String text = textOf(renderer.render(content));

        assertThat(text).contains("Łukasz Żółć", "Désirée Nguyễn", "Ελληνικά", "Привет", "emoji rocket", "tab andbell",
                "Chinese is skipped, this stays.");
        assertThat(text).doesNotContain("🚀", "中文");
    }

    @Test
    void wrapsLongLinesAndBreaksWordsTooLongForALine() throws IOException {
        final String longUrl = "https://example.com/" + "a".repeat(200);
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", null, List.of(longUrl),
                "word ".repeat(300).strip(), List.of(), List.of(), List.of(), List.of(), List.of());

        final String text = textOf(renderer.render(content));

        assertThat(text.replaceAll("\\s", "")).contains(longUrl, "word".repeat(300));
        assertThat(text.lines().count()).isGreaterThan(10);
    }

    @Test
    void keepsALineBreakInsideAParagraph() throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", null, List.of(),
                "First line.\nSecond line.", List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(textOf(renderer.render(content)).lines().map(String::strip))
                .containsSubsequence("First line.", "Second line.");
    }

    private static String textOf(final byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
