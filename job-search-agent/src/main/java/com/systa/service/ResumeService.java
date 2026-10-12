package com.systa.service;

import com.systa.exception.InvalidResumeException;
import com.systa.exception.ResumeUploadConflictException;
import com.systa.model.ResumeDownload;
import com.systa.model.UserResume;
import com.systa.repository.UserResumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** A user's resume and its earlier versions: the files live in S3, what we know about them in Mongo. */
@Service
@Slf4j
public class ResumeService {

    private static final String DEFAULT_FILE_NAME = "resume.pdf";
    private static final int MAX_FILE_NAME_LENGTH = 150;

    private final UserResumeRepository userResumeRepository;
    private final ResumeStorageService resumeStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    public ResumeService(final UserResumeRepository userResumeRepository,
                          final ResumeStorageService resumeStorageService,
                          final ResumeTextExtractor resumeTextExtractor) {
        this(userResumeRepository, resumeStorageService, resumeTextExtractor, Clock.systemUTC(),
                () -> UUID.randomUUID().toString());
    }

    ResumeService(final UserResumeRepository userResumeRepository,
                   final ResumeStorageService resumeStorageService,
                   final ResumeTextExtractor resumeTextExtractor,
                   final Clock clock,
                   final Supplier<String> idGenerator) {
        this.userResumeRepository = userResumeRepository;
        this.resumeStorageService = resumeStorageService;
        this.resumeTextExtractor = resumeTextExtractor;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** Stores the file as the user's next resume version and makes it their latest. */
    public UserResume upload(final String userId, final String originalFileName, final byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidResumeException("resume file is empty");
        }
        final String extractedText = resumeTextExtractor.extractText(content);

        final int version = userResumeRepository.findFirstByUserIdOrderByVersionDesc(userId)
                .map(newest -> newest.version() + 1)
                .orElse(1);
        final String id = idGenerator.get();
        // The id keeps the key unique even if two uploads race for the same version number.
        final String s3Key = "resumes/%s/v%d/%s.pdf".formatted(userId, version, id);

        // File first: if recording it then fails, the leftover is an S3 object nobody can see,
        // rather than a listed version with no file behind it.
        resumeStorageService.store(s3Key, content, MediaType.APPLICATION_PDF_VALUE);

        final UserResume saved;
        try {
            saved = userResumeRepository.save(new UserResume(id, userId, version, true, s3Key,
                    safeFileName(originalFileName), MediaType.APPLICATION_PDF_VALUE, content.length, clock.instant(),
                    extractedText));
        } catch (final DuplicateKeyException e) {
            throw new ResumeUploadConflictException("Resume version already taken - userId=" + userId
                    + ", version=" + version, e);
        }
        // New marker before clearing the old one, so the user is never left without a latest resume.
        userResumeRepository.clearLatestBeforeVersion(userId, version);

        log.info("Resume uploaded - userId={}, version={}, sizeBytes={}", userId, version, content.length);
        return saved;
    }

    /** Every version the user has uploaded, newest first. */
    public List<UserResume> findVersions(final String userId) {
        return userResumeRepository.findByUserIdOrderByVersionDesc(userId);
    }

    public Optional<UserResume> findLatest(final String userId) {
        return userResumeRepository.findFirstByUserIdAndLatestTrueOrderByVersionDesc(userId);
    }

    /** A download link for one of the user's own versions, or empty if they have no such version. */
    public Optional<ResumeDownload> downloadLink(final String userId, final int version) {
        return userResumeRepository.findByUserIdAndVersion(userId, version)
                .map(resume -> resumeStorageService.downloadLink(resume.s3Key(), resume.fileName(),
                        resume.contentType()));
    }

    // The name comes from the browser and ends up in a Content-Disposition header, so keep only
    // its last path segment and drop anything that could break out of the quoted value.
    private static String safeFileName(final String originalFileName) {
        if (originalFileName == null) {
            return DEFAULT_FILE_NAME;
        }
        String name = originalFileName.substring(
                Math.max(originalFileName.lastIndexOf('/'), originalFileName.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^\\p{Alnum} ._()-]", "_").strip();
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            name = name.substring(name.length() - MAX_FILE_NAME_LENGTH);
        }
        return name.isEmpty() ? DEFAULT_FILE_NAME : name;
    }
}
