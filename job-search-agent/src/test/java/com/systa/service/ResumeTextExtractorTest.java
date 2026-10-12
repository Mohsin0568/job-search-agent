package com.systa.service;

import com.systa.exception.InvalidResumeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void readsTheTextOfAPdf() {
        assertThat(extractor.extractText(TestPdfs.withText("Senior Java Developer at Acme")))
                .isEqualTo("Senior Java Developer at Acme");
    }

    @Test
    void rejectsAFileThatIsNotAPdf() {
        assertThatThrownBy(() -> extractor.extractText("Senior Java Developer".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessage("resume must be a PDF file");
        assertThatThrownBy(() -> extractor.extractText(new byte[] {'%', 'P'}))
                .isInstanceOf(InvalidResumeException.class);
    }

    @Test
    void rejectsADamagedPdf() {
        assertThatThrownBy(() -> extractor.extractText("%PDF-1.7 not really a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessageContaining("couldn't open this PDF");
    }

    @Test
    void rejectsAPdfWithNoTextInIt() {
        assertThatThrownBy(() -> extractor.extractText(TestPdfs.withoutText()))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessageContaining("couldn't read any text");
    }
}
