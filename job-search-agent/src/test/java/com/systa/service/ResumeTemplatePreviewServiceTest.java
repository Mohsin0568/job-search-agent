package com.systa.service;

import com.systa.model.ResumeTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ResumeTemplatePreviewServiceTest {

    // Loads every font once: shared, as in the app.
    private static TailoredResumePdfRenderer renderer;

    @BeforeAll
    static void createRenderer() {
        renderer = new TailoredResumePdfRenderer();
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplate.class)
    void drawsTheTemplatesFirstPage_asAnA4Picture(final ResumeTemplate template) throws IOException {
        final BufferedImage picture = ImageIO.read(new ByteArrayInputStream(
                new ResumeTemplatePreviewService(renderer).preview(template)));

        assertThat(picture).isNotNull();
        assertThat(picture.getWidth()).isBetween(780, 800);
        assertThat((double) picture.getHeight() / picture.getWidth()).isCloseTo(297.0 / 210, within(0.01));
    }

    @Test
    void drawsEachTemplateOnlyOnce() {
        final TailoredResumePdfRenderer watched = spy(renderer);
        final ResumeTemplatePreviewService service = new ResumeTemplatePreviewService(watched);

        final byte[] first = service.preview(ResumeTemplate.EVEN);
        assertThat(service.preview(ResumeTemplate.EVEN)).isSameAs(first);
        service.preview(ResumeTemplate.MODERN);

        verify(watched, times(2)).render(any(), any());
    }

    @Test
    void theSampleFillsEveryPartOfATemplate() {
        assertThat(ResumeTemplatePreviewService.SAMPLE).hasNoNullFieldsOrProperties();
        assertThat(ResumeTemplatePreviewService.SAMPLE.experience()).hasSizeGreaterThan(1);
    }
}
