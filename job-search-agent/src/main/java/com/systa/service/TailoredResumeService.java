package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.exception.ResumeRequiredException;
import com.systa.exception.ResumeReviewRequiredException;
import com.systa.exception.ResumeTailoringException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeDownload;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeTemplate;
import com.systa.model.TailoredResume;
import com.systa.model.TailoredResume.Fix;
import com.systa.model.TailoredResume.Status;
import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoringFix;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserResume;
import com.systa.repository.ResumeReviewRepository;
import com.systa.repository.TailoredResumeRepository;
import com.systa.repository.UserJobSearchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The user's resume tailored to one of their saved jobs. Tailoring takes a while - the job's posting
 * may need reading, then the LLM rewrites the resume - so it runs in the background: a request
 * returns straight away as PENDING, and the caller asks again until it is READY or FAILED.
 */
@Service
@Slf4j
public class TailoredResumeService {

    // A PENDING request older than this was cut short (the app restarted, say) and may be started again.
    static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);

    private static final String UNEXPECTED_FAILURE =
            "something went wrong while tailoring your resume, please try again";

    private final UserJobSearchResultRepository userJobSearchResultRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final ResumeReviewRepository resumeReviewRepository;
    private final ResumeService resumeService;
    private final JobDescriptionService jobDescriptionService;
    private final ResumeTailoringLlmService resumeTailoringLlmService;
    private final TailoredResumePdfRenderer tailoredResumePdfRenderer;
    private final ResumeStorageService resumeStorageService;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    @Autowired
    public TailoredResumeService(final UserJobSearchResultRepository userJobSearchResultRepository,
                                  final TailoredResumeRepository tailoredResumeRepository,
                                  final ResumeReviewRepository resumeReviewRepository,
                                  final ResumeService resumeService,
                                  final JobDescriptionService jobDescriptionService,
                                  final ResumeTailoringLlmService resumeTailoringLlmService,
                                  final TailoredResumePdfRenderer tailoredResumePdfRenderer,
                                  final ResumeStorageService resumeStorageService,
                                  @Qualifier("applicationTaskExecutor") final TaskExecutor taskExecutor) {
        this(userJobSearchResultRepository, tailoredResumeRepository, resumeReviewRepository, resumeService,
                jobDescriptionService, resumeTailoringLlmService, tailoredResumePdfRenderer, resumeStorageService,
                taskExecutor, Clock.systemUTC());
    }

    TailoredResumeService(final UserJobSearchResultRepository userJobSearchResultRepository,
                           final TailoredResumeRepository tailoredResumeRepository,
                           final ResumeReviewRepository resumeReviewRepository,
                           final ResumeService resumeService,
                           final JobDescriptionService jobDescriptionService,
                           final ResumeTailoringLlmService resumeTailoringLlmService,
                           final TailoredResumePdfRenderer tailoredResumePdfRenderer,
                           final ResumeStorageService resumeStorageService,
                           final TaskExecutor taskExecutor,
                           final Clock clock) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.resumeReviewRepository = resumeReviewRepository;
        this.resumeService = resumeService;
        this.jobDescriptionService = jobDescriptionService;
        this.resumeTailoringLlmService = resumeTailoringLlmService;
        this.tailoredResumePdfRenderer = tailoredResumePdfRenderer;
        this.resumeStorageService = resumeStorageService;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    /**
     * Makes sure the job has a resume tailored from the user's latest upload, in the template and with the
     * fixes asked for: returns the one that is ready or already on its way, lays a ready one out again when
     * only the template has changed, and otherwise starts tailoring (again, after a failure, a newer upload
     * or a change in what to fix).
     *
     * @param template the layout wanted, or null to keep the job's current one (the default if it has none)
     * @param fix      what to fix from the job's resume review, or null to keep what it fixes now (nothing if
     *                 it has never been tailored); {@link TailoringFix#AS_IS} to fix nothing
     * @return empty when the user has no such job
     * @throws ResumeRequiredException       when the user hasn't uploaded a resume
     * @throws ResumeReviewRequiredException when asked to fix something but the job has no finished review
     *                                       of the user's latest resume
     */
    public Optional<TailoredResume> request(final String userId, final String jobResultId,
                                            final ResumeTemplate template, final TailoringFix fix) {
        final Optional<UserJobSearchResult> jobResult =
                userJobSearchResultRepository.findByIdAndUserId(jobResultId, userId);
        if (jobResult.isEmpty()) {
            return Optional.empty();
        }
        final UserResume resume = resumeService.findLatest(userId).orElseThrow(() ->
                new ResumeRequiredException("upload your resume before tailoring it to a job"));

        final Optional<TailoredResume> existing = tailoredResumeRepository.findById(jobResultId);
        final ResumeTemplate wanted = template != null ? template
                : existing.map(TailoredResume::templateOrDefault).orElse(ResumeTemplate.DEFAULT);
        final Fix wantedFix = fix != null ? fixFromReview(userId, jobResultId, resume, fix)
                : existing.map(TailoredResume::fix).orElse(null);
        if (existing.isPresent() && isCurrent(existing.get(), resume)) {
            final TailoredResume current = existing.get();
            // One under way keeps the template and fixes it was asked for: the user can change them once it is ready.
            if (current.status() != Status.READY) {
                return existing;
            }
            if (Objects.equals(current.fix(), wantedFix)) {
                if (current.templateOrDefault() == wanted) {
                    return existing;
                }
                if (current.content() != null) {
                    return Optional.of(layOutAgain(current, wanted));
                }
                // Tailored before its content was kept: there is nothing to lay out again, so tailor it afresh.
            }
            // Asked to fix something else: that means rewriting it.
        }

        final TailoredResume pending = new TailoredResume(jobResultId, userId, Status.PENDING, resume.version(),
                wanted, null, null, null, now(), null, null, wantedFix);
        if (existing.isPresent()) {
            tailoredResumeRepository.save(pending);
        } else {
            try {
                tailoredResumeRepository.insert(pending);
            } catch (final DuplicateKeyException e) {
                // Another request for the same job got there first, and has started the work.
                return tailoredResumeRepository.findById(jobResultId);
            }
        }

        taskExecutor.execute(() -> tailor(pending, jobResult.get(), resume));
        return Optional.of(pending);
    }

    /** The tailored resume for one of the user's jobs, in whatever state it is - empty if never requested. */
    public Optional<TailoredResume> find(final String userId, final String jobResultId) {
        return tailoredResumeRepository.findById(jobResultId)
                .filter(tailoredResume -> tailoredResume.userId().equals(userId));
    }

    /** Every tailored resume the user has asked for, in whatever state - one per job. */
    public List<TailoredResume> findAll(final String userId) {
        return tailoredResumeRepository.findByUserId(userId);
    }

    /** A download link for the tailored resume, or empty unless it is ready. */
    public Optional<ResumeDownload> downloadLink(final String userId, final String jobResultId) {
        return find(userId, jobResultId)
                .filter(tailoredResume -> tailoredResume.status() == Status.READY)
                .map(tailoredResume -> resumeStorageService.downloadLink(tailoredResume.s3Key(),
                        tailoredResume.fileName(), MediaType.APPLICATION_PDF_VALUE));
    }

    /**
     * What to fix, taken from the job's review of the user's latest resume: only the missing keywords the
     * user ticked that the review actually listed - in its spelling - and its red flags if they asked.
     * Null when that leaves nothing to fix.
     */
    private Fix fixFromReview(final String userId, final String jobResultId, final UserResume resume,
                              final TailoringFix fix) {
        final List<String> ticked = fix.keywords() == null ? List.of() : fix.keywords();
        if (ticked.isEmpty() && !fix.redFlags()) {
            return null;
        }
        final ResumeReview review = resumeReviewRepository.findById(jobResultId)
                .filter(found -> found.userId().equals(userId))
                .filter(found -> found.status() == ResumeReview.Status.READY
                        && found.resumeVersion() == resume.version())
                .orElseThrow(() -> new ResumeReviewRequiredException(
                        "review your latest resume against this job before fixing what the review finds"));

        final Set<String> wanted = ticked.stream()
                .filter(Objects::nonNull)
                .map(TailoredResumeService::normaliseKeyword)
                .collect(Collectors.toSet());
        final List<String> keywords = Objects.requireNonNullElse(review.missingKeywords(), List.<String>of()).stream()
                .filter(keyword -> wanted.contains(normaliseKeyword(keyword)))
                .toList();
        final List<RedFlag> redFlags = fix.redFlags()
                ? List.copyOf(Objects.requireNonNullElse(review.redFlags(), List.of()))
                : List.of();
        return keywords.isEmpty() && redFlags.isEmpty() ? null : new Fix(keywords, redFlags);
    }

    private static String normaliseKeyword(final String keyword) {
        return keyword.strip().toLowerCase(Locale.ROOT);
    }

    // Quick - no LLM, just the PDF - so it happens within the request rather than in the background.
    private TailoredResume layOutAgain(final TailoredResume current, final ResumeTemplate template) {
        final byte[] pdf = tailoredResumePdfRenderer.render(current.content(), template);
        final String s3Key = s3Key(current.userId(), current.jobResultId(), current.resumeVersion(), template);
        resumeStorageService.store(s3Key, pdf, MediaType.APPLICATION_PDF_VALUE);
        log.info("Tailored resume laid out again - userId={}, jobResultId={}, template={}", current.userId(),
                current.jobResultId(), template);
        return tailoredResumeRepository.save(current.relaidOut(template, s3Key, now()));
    }

    // Nothing to do when it was made from the user's latest resume, or is being made from it right now.
    private boolean isCurrent(final TailoredResume tailoredResume, final UserResume latestResume) {
        if (tailoredResume.resumeVersion() != latestResume.version()) {
            return false;
        }
        return switch (tailoredResume.status()) {
            case READY -> true;
            case PENDING -> tailoredResume.requestedAt().plus(PENDING_TIMEOUT).isAfter(clock.instant());
            case FAILED -> false;
        };
    }

    // Runs in the background. Whatever happens ends up on the record: nobody is waiting on this call.
    private void tailor(final TailoredResume pending, final UserJobSearchResult jobResult, final UserResume resume) {
        final String userId = pending.userId();
        final String jobResultId = pending.jobResultId();
        TailoredResume outcome;
        try {
            final JobDescription jobDescription = jobDescriptionService.getOrFetch(userId, jobResultId)
                    .orElseThrow(() -> new JobDescriptionUnavailableException("this job is no longer in your results"));
            final TailoredResumeContent content = resumeTailoringLlmService.tailor(userId, resume.extractedText(),
                    jobResult.companyName(), jobResult.job().jobTitle(), jobDescription, pending.fix());
            final byte[] pdf = tailoredResumePdfRenderer.render(content, pending.templateOrDefault());

            final String s3Key = s3Key(userId, jobResultId, resume.version(), pending.templateOrDefault());
            resumeStorageService.store(s3Key, pdf, MediaType.APPLICATION_PDF_VALUE);
            outcome = pending.ready(s3Key, fileName(content.name(), jobResult.companyName()), content, now());
            log.info("Tailored resume ready - userId={}, jobResultId={}, resumeVersion={}, template={}, "
                            + "fixedKeywords={}, fixedRedFlags={}", userId, jobResultId, resume.version(),
                    pending.templateOrDefault(), pending.fix() == null ? 0 : pending.fix().keywords().size(),
                    pending.fix() == null ? 0 : pending.fix().redFlags().size());
        } catch (final JobDescriptionUnavailableException | ResumeTailoringException e) {
            // Both carry a reason written for the user.
            log.warn("Tailored resume failed - userId={}, jobResultId={}, reason={}", userId, jobResultId,
                    e.getMessage());
            outcome = pending.failed(e.getMessage(), now());
        } catch (final Exception e) {
            log.error("Tailored resume failed unexpectedly - userId={}, jobResultId={}", userId, jobResultId, e);
            outcome = pending.failed(UNEXPECTED_FAILURE, now());
        }
        record(pending, outcome);
    }

    private void record(final TailoredResume pending, final TailoredResume outcome) {
        try {
            // If the user asked again meanwhile (after a timeout or a new upload), that request owns
            // the record now and this result is out of date.
            final boolean stillOurs = tailoredResumeRepository.findById(pending.jobResultId())
                    .map(current -> current.requestedAt().equals(pending.requestedAt()))
                    .orElse(false);
            if (stillOurs) {
                tailoredResumeRepository.save(outcome);
            } else {
                log.info("Discarding a superseded tailored resume - userId={}, jobResultId={}", pending.userId(),
                        pending.jobResultId());
            }
        } catch (final Exception e) {
            log.error("Could not record the tailored resume's outcome - userId={}, jobResultId={}, status={}",
                    pending.userId(), pending.jobResultId(), outcome.status(), e);
        }
    }

    // MongoDB keeps dates to the millisecond, so anything finer would not survive a save - and record()
    // compares the requestedAt it holds with the one read back to tell whether the record is still ours.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    // One PDF per resume version and template, so laying out again never overwrites the one being downloaded.
    private static String s3Key(final String userId, final String jobResultId, final int resumeVersion,
                                final ResumeTemplate template) {
        return "tailored/%s/%s/v%d-%s.pdf".formatted(userId, jobResultId, resumeVersion,
                template.name().toLowerCase(Locale.ROOT));
    }

    // "Jane-Doe-Acme-Corp-resume.pdf". Ends up in a Content-Disposition header, so only plain characters.
    private static String fileName(final String candidateName, final String companyName) {
        final String base = (candidateName + " " + companyName)
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("^-+|-+$", "");
        return (base.isEmpty() ? "tailored" : base) + "-resume.pdf";
    }
}
