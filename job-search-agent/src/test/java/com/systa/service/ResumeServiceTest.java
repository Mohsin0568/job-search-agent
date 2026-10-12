package com.systa.service;

import com.systa.exception.InvalidResumeException;
import com.systa.exception.ResumeStorageException;
import com.systa.exception.ResumeUploadConflictException;
import com.systa.model.ResumeDownload;
import com.systa.model.UserResume;
import com.systa.repository.UserResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    private static final String USER_ID = "user-1";
    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private static final byte[] PDF = TestPdfs.withText("Senior Java Developer");

    @Mock
    private UserResumeRepository userResumeRepository;

    @Mock
    private ResumeStorageService resumeStorageService;

    private ResumeService service;

    @BeforeEach
    void setUp() {
        service = new ResumeService(userResumeRepository, resumeStorageService, new ResumeTextExtractor(),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "resume-id");
        lenient().when(userResumeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void storesAFirstUploadAsVersionOne_andMarksItLatest() {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID)).thenReturn(Optional.empty());

        final UserResume uploaded = service.upload(USER_ID, "jane-cv.pdf", PDF);

        assertThat(uploaded).isEqualTo(new UserResume("resume-id", USER_ID, 1, true,
                "resumes/user-1/v1/resume-id.pdf", "jane-cv.pdf", "application/pdf", PDF.length, NOW,
                "Senior Java Developer"));
        verify(resumeStorageService).store("resumes/user-1/v1/resume-id.pdf", PDF, "application/pdf");
        verify(userResumeRepository).save(uploaded);
    }

    @Test
    void numbersALaterUploadAfterTheNewestVersion_andMovesTheLatestMarkerToIt() {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID))
                .thenReturn(Optional.of(resume(4, true)));

        final UserResume uploaded = service.upload(USER_ID, "jane-cv.pdf", PDF);

        assertThat(uploaded.version()).isEqualTo(5);
        assertThat(uploaded.latest()).isTrue();
        assertThat(uploaded.s3Key()).isEqualTo("resumes/user-1/v5/resume-id.pdf");
        // File, then the new marker, then the old marker cleared.
        final InOrder inOrder = inOrder(resumeStorageService, userResumeRepository);
        inOrder.verify(resumeStorageService).store(anyString(), any(), anyString());
        inOrder.verify(userResumeRepository).save(uploaded);
        inOrder.verify(userResumeRepository).clearLatestBeforeVersion(USER_ID, 5);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsAnEmptyFile(final byte[] content) {
        assertThatThrownBy(() -> service.upload(USER_ID, "jane-cv.pdf", content))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessage("resume file is empty");

        verifyNoInteractions(resumeStorageService, userResumeRepository);
    }

    @Test
    void rejectsAFileThatIsNotAPdf_withoutStoringAnything() {
        assertThatThrownBy(() -> service.upload(USER_ID, "cv.docx", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidResumeException.class);

        verifyNoInteractions(resumeStorageService, userResumeRepository);
    }

    @Test
    void recordsNothing_whenTheFileCannotBeStored() {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID)).thenReturn(Optional.empty());
        doThrow(new ResumeStorageException("S3 unavailable", null))
                .when(resumeStorageService).store(anyString(), any(), anyString());

        assertThatThrownBy(() -> service.upload(USER_ID, "jane-cv.pdf", PDF))
                .isInstanceOf(ResumeStorageException.class);

        verify(userResumeRepository, never()).save(any());
        verify(userResumeRepository, never()).clearLatestBeforeVersion(anyString(), anyInt());
    }

    @Test
    void reportsAConflict_andLeavesTheLatestMarkerAlone_whenAnotherUploadTookTheVersion() {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID)).thenReturn(Optional.empty());
        when(userResumeRepository.save(any())).thenThrow(new DuplicateKeyException("user_version"));

        assertThatThrownBy(() -> service.upload(USER_ID, "jane-cv.pdf", PDF))
                .isInstanceOf(ResumeUploadConflictException.class);

        verify(userResumeRepository, never()).clearLatestBeforeVersion(anyString(), anyInt());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "jane-cv.pdf            | jane-cv.pdf",
            "Jane Doe (2026).pdf    | Jane Doe (2026).pdf",
            "C:\\Users\\jane\\cv.pdf | cv.pdf",
            "../../etc/cv.pdf       | cv.pdf",
            "résumé.pdf             | r_sum_.pdf",
            "NULL                   | resume.pdf",
            "''                     | resume.pdf",
            "/                      | resume.pdf"
    }, delimiter = '|', nullValues = "NULL")
    void keepsOnlyASafeFileName(final String originalFileName, final String expected) {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.upload(USER_ID, originalFileName, PDF).fileName()).isEqualTo(expected);
    }

    @Test
    void replacesCharactersThatCouldBreakOutOfADownloadHeader() {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.upload(USER_ID, "cv\"; evil=\"x\r\n.pdf", PDF).fileName()).isEqualTo("cv__ evil__x__.pdf");
    }

    @Test
    void cutsAVeryLongFileNameDown_keepingItsExtension() {
        when(userResumeRepository.findFirstByUserIdOrderByVersionDesc(USER_ID)).thenReturn(Optional.empty());

        final String fileName = service.upload(USER_ID, "a".repeat(500) + ".pdf", PDF).fileName();

        assertThat(fileName).hasSize(150).endsWith(".pdf");
    }

    @Test
    void givesADownloadLinkForOneOfTheUsersVersions() {
        final ResumeDownload link = new ResumeDownload("https://s3.example/resume", NOW.plusSeconds(300));
        when(userResumeRepository.findByUserIdAndVersion(USER_ID, 2)).thenReturn(Optional.of(resume(2, false)));
        when(resumeStorageService.downloadLink("resumes/user-1/v2/id-2.pdf", "cv-v2.pdf", "application/pdf"))
                .thenReturn(link);

        assertThat(service.downloadLink(USER_ID, 2)).contains(link);
    }

    @Test
    void givesNoDownloadLinkForAVersionTheUserDoesNotHave() {
        when(userResumeRepository.findByUserIdAndVersion(USER_ID, 9)).thenReturn(Optional.empty());

        assertThat(service.downloadLink(USER_ID, 9)).isEmpty();
        verifyNoInteractions(resumeStorageService);
    }

    @Test
    void findsTheLatestResumeByItsMarker() {
        final UserResume latest = resume(3, true);
        when(userResumeRepository.findFirstByUserIdAndLatestTrueOrderByVersionDesc(USER_ID))
                .thenReturn(Optional.of(latest));

        assertThat(service.findLatest(USER_ID)).contains(latest);
    }

    private static UserResume resume(final int version, final boolean latest) {
        return new UserResume("id-" + version, USER_ID, version, latest,
                "resumes/user-1/v%d/id-%d.pdf".formatted(version, version), "cv-v" + version + ".pdf",
                "application/pdf", 1024, NOW, "text");
    }
}
