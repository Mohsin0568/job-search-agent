package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.exception.ResumeRequiredException;
import com.systa.exception.ResumeTailoringException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeDownload;
import com.systa.model.TailoredResume;
import com.systa.model.TailoredResume.Status;
import com.systa.model.TailoredResumeContent;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserResume;
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
import java.util.Optional;

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
                                  final ResumeService resumeService,
                                  final JobDescriptionService jobDescriptionService,
                                  final ResumeTailoringLlmService resumeTailoringLlmService,
                                  final TailoredResumePdfRenderer tailoredResumePdfRenderer,
                                  final ResumeStorageService resumeStorageService,
                                  @Qualifier("applicationTaskExecutor") final TaskExecutor taskExecutor) {
        this(userJobSearchResultRepository, tailoredResumeRepository, resumeService, jobDescriptionService,
                resumeTailoringLlmService, tailoredResumePdfRenderer, resumeStorageService, taskExecutor,
                Clock.systemUTC());
    }

    TailoredResumeService(final UserJobSearchResultRepository userJobSearchResultRepository,
                           final TailoredResumeRepository tailoredResumeRepository,
                           final ResumeService resumeService,
                           final JobDescriptionService jobDescriptionService,
                           final ResumeTailoringLlmService resumeTailoringLlmService,
                           final TailoredResumePdfRenderer tailoredResumePdfRenderer,
                           final ResumeStorageService resumeStorageService,
                           final TaskExecutor taskExecutor,
                           final Clock clock) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.resumeService = resumeService;
        this.jobDescriptionService = jobDescriptionService;
        this.resumeTailoringLlmService = resumeTailoringLlmService;
        this.tailoredResumePdfRenderer = tailoredResumePdfRenderer;
        this.resumeStorageService = resumeStorageService;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    /**
     * Makes sure the job has a resume tailored from the user's latest upload: returns the one that is
     * ready or already on its way, and otherwise starts tailoring (again, after a failure or a newer upload).
     *
     * @return empty when the user has no such job
     * @throws ResumeRequiredException when the user hasn't uploaded a resume
     */
    public Optional<TailoredResume> request(final String userId, final String jobResultId) {
        final Optional<UserJobSearchResult> jobResult =
                userJobSearchResultRepository.findByIdAndUserId(jobResultId, userId);
        if (jobResult.isEmpty()) {
            return Optional.empty();
        }
        final UserResume resume = resumeService.findLatest(userId).orElseThrow(() ->
                new ResumeRequiredException("upload your resume before tailoring it to a job"));

        final Optional<TailoredResume> existing = tailoredResumeRepository.findById(jobResultId);
        if (existing.isPresent() && isCurrent(existing.get(), resume)) {
            return existing;
        }

        final TailoredResume pending = new TailoredResume(jobResultId, userId, Status.PENDING, resume.version(),
                null, null, null, clock.instant(), null);
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

    /** A download link for the tailored resume, or empty unless it is ready. */
    public Optional<ResumeDownload> downloadLink(final String userId, final String jobResultId) {
        return find(userId, jobResultId)
                .filter(tailoredResume -> tailoredResume.status() == Status.READY)
                .map(tailoredResume -> resumeStorageService.downloadLink(tailoredResume.s3Key(),
                        tailoredResume.fileName(), MediaType.APPLICATION_PDF_VALUE));
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
                    jobResult.companyName(), jobResult.job().jobTitle(), jobDescription);
            final byte[] pdf = tailoredResumePdfRenderer.render(content);

            final String s3Key = "tailored/%s/%s/v%d.pdf".formatted(userId, jobResultId, resume.version());
            resumeStorageService.store(s3Key, pdf, MediaType.APPLICATION_PDF_VALUE);
            outcome = pending.ready(s3Key, fileName(content.name(), jobResult.companyName()), clock.instant());
            log.info("Tailored resume ready - userId={}, jobResultId={}, resumeVersion={}", userId, jobResultId,
                    resume.version());
        } catch (final JobDescriptionUnavailableException | ResumeTailoringException e) {
            // Both carry a reason written for the user.
            log.warn("Tailored resume failed - userId={}, jobResultId={}, reason={}", userId, jobResultId,
                    e.getMessage());
            outcome = pending.failed(e.getMessage(), clock.instant());
        } catch (final Exception e) {
            log.error("Tailored resume failed unexpectedly - userId={}, jobResultId={}", userId, jobResultId, e);
            outcome = pending.failed(UNEXPECTED_FAILURE, clock.instant());
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

    // "Jane-Doe-Acme-Corp-resume.pdf". Ends up in a Content-Disposition header, so only plain characters.
    private static String fileName(final String candidateName, final String companyName) {
        final String base = (candidateName + " " + companyName)
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("^-+|-+$", "");
        return (base.isEmpty() ? "tailored" : base) + "-resume.pdf";
    }
}
