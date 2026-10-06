package com.systa.service;

import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobSearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class JobSearchResultFilterServiceTest {

    private static final String USER_ID = "user-1";
    private static final ZoneId ZONE = ZoneId.of("Europe/London");
    // "Today" in every test, unless the test builds its own service.
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    private final JobSearchResultFilterService service = serviceOn(TODAY);

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    @Test
    void keepsJobsPostedWithinTheWindow_includingItsFirstDayAndToday() {
        final List<String> kept = titlesKept(7,
                job("today", "18 Aug 2026"),
                job("yesterday", "17 Aug 2026"),
                job("seven days ago", "11 Aug 2026"));

        assertThat(kept).containsExactly("today", "yesterday", "seven days ago");
    }

    @Test
    void dropsJobsPostedBeforeTheWindow() {
        final List<String> kept = titlesKept(7,
                job("eight days ago", "10 Aug 2026"),
                job("last month", "18 Jul 2026"),
                job("last year", "18 Aug 2025"));

        assertThat(kept).isEmpty();
    }

    @Test
    void dropsJobsDatedInTheFuture() {
        assertThat(titlesKept(7, job("tomorrow", "19 Aug 2026"))).isEmpty();
    }

    @Test
    void usesTheProfilesWindow() {
        final JobListing twentyDaysAgo = job("twenty days ago", "29 Jul 2026");

        assertThat(titlesKept(30, twentyDaysAgo)).containsExactly("twenty days ago");
        assertThat(titlesKept(14, twentyDaysAgo)).isEmpty();
        assertThat(titlesKept(1, job("yesterday", "17 Aug 2026"), job("two days ago", "16 Aug 2026")))
                .containsExactly("yesterday");
    }

    @Test
    void usesSevenDaysWhenTheProfileHasNoWindow() {
        final List<String> kept = titlesKept(null,
                job("seven days ago", "11 Aug 2026"),
                job("eight days ago", "10 Aug 2026"));

        assertThat(kept).containsExactly("seven days ago");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Not specified", "", "2026-08-17", "17/08/2026", "17 August 2026", "3 days ago",
            "32 Aug 2026", "0 Aug 2026"})
    void dropsJobsWhoseDateCannotBeRead(final String datePosted) {
        assertThat(titlesKept(30, job("unreadable date", datePosted))).isEmpty();
    }

    @Test
    void readsADayWrittenWithoutItsLeadingZero() {
        final JobSearchResultFilterService service = serviceOn(LocalDate.of(2026, 8, 9));

        final JobSearchResponse filtered = service.filterStaleJobs(USER_ID, 7, response(company("Acme Corp",
                job("no leading zero", "7 Aug 2026"),
                job("leading zero", "07 Aug 2026"),
                job("stale, no leading zero", "1 Aug 2026"))));

        assertThat(titles(filtered)).containsExactly("no leading zero", "leading zero");
    }

    @Test
    void windowsCanSpanAMonthAndAYearBoundary() {
        final JobSearchResultFilterService newYearsDay = serviceOn(LocalDate.of(2027, 1, 1));

        final JobSearchResponse filtered = newYearsDay.filterStaleJobs(USER_ID, 7, response(company("Acme Corp",
                job("new year's eve", "31 Dec 2026"),
                job("christmas day", "25 Dec 2026"),
                job("christmas eve", "24 Dec 2026"))));

        assertThat(titles(filtered)).containsExactly("new year's eve", "christmas day");
    }

    @Test
    void readsEnglishMonthNames_whateverTheDefaultLocale() {
        // en_GB abbreviates September as "Sept" and French as "sept.", but the LLM is told to write "Sep".
        final JobSearchResultFilterService september = serviceOn(LocalDate.of(2026, 9, 8));

        for (final Locale locale : List.of(Locale.UK, Locale.US, Locale.FRANCE)) {
            Locale.setDefault(locale);

            final JobSearchResponse filtered = september.filterStaleJobs(USER_ID, 7,
                    response(company("Acme Corp", job("posted in september", "06 Sep 2026"))));

            assertThat(titles(filtered)).as("default locale %s", locale).containsExactly("posted in september");
        }
    }

    @Test
    void filtersEachCompanySeparately_keepingCompaniesLeftWithNoJobs() {
        final JobSearchResponse filtered = service.filterStaleJobs(USER_ID, 7, response(
                company("Acme Corp", job("fresh", "17 Aug 2026"), job("stale", "01 Aug 2026")),
                company("Globex", job("also stale", "01 Jul 2026")),
                company("Initech")));

        assertThat(filtered.companies()).extracting(CompanySearchResult::companyName)
                .containsExactly("Acme Corp", "Globex", "Initech");
        assertThat(filtered.companies().get(0).jobs()).extracting(JobListing::jobTitle).containsExactly("fresh");
        assertThat(filtered.companies().get(1).jobs()).isEmpty();
        assertThat(filtered.companies().get(2).jobs()).isEmpty();
    }

    @Test
    void returnsKeptJobsUnchanged() {
        final JobListing fresh = job("fresh", "17 Aug 2026");

        final JobSearchResponse filtered = service.filterStaleJobs(USER_ID, 7, response(company("Acme Corp", fresh)));

        assertThat(filtered.companies().get(0).jobs()).containsExactly(fresh);
    }

    private List<String> titlesKept(final Integer recencyWindowDays, final JobListing... jobs) {
        return titles(service.filterStaleJobs(USER_ID, recencyWindowDays, response(company("Acme Corp", jobs))));
    }

    private static List<String> titles(final JobSearchResponse response) {
        return response.companies().stream()
                .flatMap(company -> company.jobs().stream())
                .map(JobListing::jobTitle)
                .toList();
    }

    private static JobSearchResultFilterService serviceOn(final LocalDate today) {
        return new JobSearchResultFilterService(Clock.fixed(today.atTime(12, 0).atZone(ZONE).toInstant(), ZONE));
    }

    private static JobSearchResponse response(final CompanySearchResult... companies) {
        return new JobSearchResponse(List.of(companies));
    }

    private static CompanySearchResult company(final String name, final JobListing... jobs) {
        return new CompanySearchResult(name, List.of(jobs));
    }

    private static JobListing job(final String title, final String datePosted) {
        return new JobListing("J-" + title, title, "https://example.com/" + title.replace(' ', '-'), "London, UK",
                datePosted, "Not specified", "Not specified", "LinkedIn", 80);
    }
}
