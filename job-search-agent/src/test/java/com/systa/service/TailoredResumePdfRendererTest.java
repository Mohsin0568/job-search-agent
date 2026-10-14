package com.systa.service;

import com.systa.model.ResumeTemplate;
import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoredResumeContent.Education;
import com.systa.model.TailoredResumeContent.Experience;
import com.systa.model.TailoredResumeContent.Section;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TailoredResumePdfRendererTest {

    // Loads every font once: shared, as in the app.
    private static TailoredResumePdfRenderer renderer;

    @BeforeAll
    static void createRenderer() {
        renderer = new TailoredResumePdfRenderer();
    }

    static TailoredResumeContent sample() {
        return new TailoredResumeContent(
                "Jane Doe",
                "Senior Java Developer",
                List.of("jane.doe@example.com", "+44 7700 900123", "London, UK", "linkedin.com/in/janedoe"),
                "Backend engineer with nine years building payment and ledger systems on the JVM. "
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

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void writesEverySectionOfTheResume(final ResumeTemplate template) throws IOException {
        final String text = textOf(renderer.render(sample(), template));
        // Templates set headings (and some the name) in their own case - "SKILLS", "Skills" - and some put
        // the summary under the name with no heading at all.
        final String headings = text.toUpperCase(Locale.ROOT);

        assertThat(headings).contains("JANE DOE", "SENIOR JAVA DEVELOPER");
        assertThat(text).contains("jane.doe@example.com", "London, UK",
                "Backend engineer with nine years", "Spring Boot", "Kafka",
                "Senior Software Engineer", "Globex Payments", "Mar 2021 – Present",
                "cutting p99", "Initech", "Sep 2017 – Feb 2021",
                "BSc Computer Science (First Class)", "University of Leeds", "2014 – 2017",
                "AWS Certified Solutions Architect", "French (conversational)");
        assertThat(headings).contains("SKILLS", "EXPERIENCE", "EDUCATION", "CERTIFICATIONS", "LANGUAGES");
        // In reading order: the header, then the sections as laid out.
        assertThat(headings.indexOf("JANE DOE")).isLessThan(text.indexOf("Backend engineer with nine years"));
        assertThat(text.indexOf("Backend engineer with nine years")).isLessThan(headings.indexOf("EXPERIENCE"));
        assertThat(text.indexOf("Globex Payments")).isLessThan(text.indexOf("Initech"));
        assertThat(headings.indexOf("EXPERIENCE")).isLessThan(headings.indexOf("EDUCATION"));
    }

    // Applicant tracking systems read the PDF's text in the order it was written, not as it looks.
    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void writesEachLineInReadingOrder_withItsSeparatorsBetweenTheItems(final ResumeTemplate template)
            throws IOException {
        final String text = textOf(renderer.render(sample(), template));
        final String separator = "\\s*[·|,]?\\s*";

        assertThat(text).containsPattern("jane\\.doe@example\\.com" + separator + "\\+44 7700 900123" + separator
                + "London, UK" + separator + "linkedin\\.com/in/janedoe");
        assertThat(text).containsPattern("Java" + separator + "Spring Boot" + separator + "Kafka" + separator
                + "PostgreSQL");
        // No separator left beside another, away from the items it sits between.
        assertThat(text).doesNotContainPattern("[—·|,]\\s*[—·|,]");
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void fitsAnOrdinaryResumeOnOnePage(final ResumeTemplate template) throws IOException {
        try (PDDocument document = Loader.loadPDF(renderer.render(sample(), template))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({"CLASSIC, PTSerif", "MODERN, Lato", "COMPACT, Carlito", "EVEN, Lato", "STACK_OVERFLOW, Arimo",
            "KENDALL, OpenSans"})
    void setsEachTemplateInItsOwnTypeface(final ResumeTemplate template, final String typeface) throws IOException {
        final List<String> fonts = fontsIn(renderer.render(sample(), template));

        assertThat(fonts).anyMatch(font -> font.contains(typeface));
        // Bold headings use the real bold cut, not a thickened regular.
        assertThat(fonts).anyMatch(font -> font.contains(typeface) && font.toLowerCase(Locale.ROOT).contains("bold"));
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void runsALongResumeOntoMorePages_withoutLosingAnyOfIt(final ResumeTemplate template) throws IOException {
        // Enough to overflow even the compact template.
        final List<Experience> manyRoles = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> new Experience("Engineer " + i, "Company " + i, "London, UK", "20" + (10 + i),
                        List.of("Delivered project number " + i + " end to end across three teams and two time zones.",
                                "Second point for role " + i + ".", "Third point for role " + i + ".")))
                .toList();
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", null, List.of(), null, List.of(),
                manyRoles, List.of(), List.of(), List.of());

        final byte[] pdf = renderer.render(content, template);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        final String text = textOf(pdf);
        for (int i = 1; i <= 30; i++) {
            assertThat(text).contains("Engineer " + i, "Company " + i, "Third point for role " + i + ".");
        }
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void leavesOutSectionsWithNothingInThem(final ResumeTemplate template) throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", " ", null, "", null,
                Arrays.asList((Experience) null, new Experience(" ", null, null, "2020", List.of(" "))), null,
                List.of("", " "),
                Arrays.asList(null, new Section("Projects", List.of()), new Section(null, List.of("Orphan item"))));

        final String text = textOf(renderer.render(content, template));

        // Some templates set the name in capitals.
        assertThat(text.strip()).isEqualToIgnoringCase("Jane Doe");
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void writesNamesInOtherAlphabets_andSkipsWhatTheFontsCannotDraw(final ResumeTemplate template) throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Łukasz Żółć", "Désirée Nguyễn — Ελληνικά — Привет",
                List.of("emoji 🚀 rocket", "tab\tand\u0007bell"), "中文 Chinese is skipped, this stays.",
                List.of(), List.of(), List.of(), List.of(), List.of());

        final String text = textOf(renderer.render(content, template));

        // The name and headline may be set in capitals.
        for (final String inHeader : List.of("Łukasz Żółć", "Désirée Nguyễn", "Ελληνικά", "Привет")) {
            assertThat(text).containsIgnoringCase(inHeader);
        }
        assertThat(text).contains("emoji rocket", "tab andbell", "Chinese is skipped, this stays.");
        assertThat(text).doesNotContain("🚀", "中文", "#");
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void writesCharactersThatMeanSomethingInMarkup_asPlainText(final ResumeTemplate template) throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Jane <b>Doe</b>", null,
                List.of("R&D", "\"quoted\" & 'single'"), "Built <script>alert(1)</script> tools & APIs.",
                List.of("C++", "<T>"), List.of(), List.of(), List.of(), List.of());

        final String text = textOf(renderer.render(content, template));

        assertThat(text).containsIgnoringCase("Jane <b>Doe</b>");
        assertThat(text).contains("R&D", "\"quoted\" & 'single'",
                "Built <script>alert(1)</script> tools & APIs.", "C++", "<T>");
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void wrapsLongLinesAndBreaksWordsTooLongForALine(final ResumeTemplate template) throws IOException {
        final String longUrl = "https://example.com/" + "a".repeat(200);
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", null, List.of(longUrl),
                "word ".repeat(300).strip(), List.of(), List.of(), List.of(), List.of(), List.of());

        final byte[] pdf = renderer.render(content, template);
        final String text = textOf(pdf);

        assertThat(text.replaceAll("\\s", "")).contains(longUrl, "word".repeat(300));
        assertThat(text.lines().count()).isGreaterThan(10);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // Nothing runs off the side of the page.
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void keepsALineBreakInsideAParagraph(final ResumeTemplate template) throws IOException {
        final TailoredResumeContent content = new TailoredResumeContent("Jane Doe", null, List.of(),
                "First line.\r\nSecond line.", List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(textOf(renderer.render(content, template)).lines().map(String::strip))
                .containsSubsequence("First line.", "Second line.");
    }

    private static String textOf(final byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static List<String> fontsIn(final byte[] pdf) throws IOException {
        final List<String> fonts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (final PDPage page : document.getPages()) {
                for (final COSName name : page.getResources().getFontNames()) {
                    fonts.add(page.getResources().getFont(name).getName());
                }
            }
        }
        return fonts;
    }
}
