package com.systa.service;

import com.systa.model.ResumeTemplate;
import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoredResumeContent.Education;
import com.systa.model.TailoredResumeContent.Experience;
import com.systa.model.TailoredResumeContent.Section;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each template looks like, as a picture of its first page laid out with a made-up resume - so a
 * user can choose one before anything of theirs has been tailored. Drawn by the same renderer and
 * templates as real resumes, so a preview can't drift from what the user gets.
 */
@Service
public class ResumeTemplatePreviewService {

    // Screen resolution: an A4 page comes out about 800 pixels wide.
    private static final float DPI = 96;

    static final TailoredResumeContent SAMPLE = new TailoredResumeContent(
            "Alex Morgan",
            "Senior Software Engineer",
            List.of("alex.morgan@example.com", "+44 20 7946 0000", "Manchester, UK", "github.com/alexmorgan"),
            "Software engineer with eight years building high-traffic web services and the platforms behind them. "
                    + "Enjoys turning slow, fragile systems into fast, well-tested ones, and helping teams ship "
                    + "with confidence.",
            List.of("Java", "Spring Boot", "Kotlin", "PostgreSQL", "Kafka", "AWS", "Docker", "Kubernetes",
                    "CI/CD", "System design"),
            List.of(new Experience("Senior Software Engineer", "Northwind Logistics", "Manchester, UK",
                            "Jan 2022 – Present",
                            List.of("Rebuilt the parcel-tracking API on Spring Boot and Kafka, cutting response "
                                            + "times by 70% at 5,000 requests a second.",
                                    "Led a team of five through the move from a data centre to AWS with no downtime.",
                                    "Introduced contract testing between services, halving production incidents.")),
                    new Experience("Software Engineer", "Contoso Retail", "Leeds, UK", "Jun 2018 – Dec 2021",
                            List.of("Built the checkout service handling £40m of orders a year.",
                                    "Automated releases with GitHub Actions and Terraform, from weekly to daily."))),
            List.of(new Education("BSc Software Engineering", "University of Sheffield", "2015 – 2018",
                    "First Class Honours")),
            List.of("AWS Certified Developer – Associate"),
            List.of(new Section("Languages", List.of("English (native)", "Spanish (intermediate)"))));

    private final TailoredResumePdfRenderer renderer;
    // At most one per template, and a template never changes while the app runs.
    private final Map<ResumeTemplate, byte[]> previews = new ConcurrentHashMap<>();

    public ResumeTemplatePreviewService(final TailoredResumePdfRenderer renderer) {
        this.renderer = renderer;
    }

    /** The template's first page with the sample resume in it, as a PNG. */
    public byte[] preview(final ResumeTemplate template) {
        return previews.computeIfAbsent(template, this::draw);
    }

    private byte[] draw(final ResumeTemplate template) {
        try (PDDocument document = Loader.loadPDF(renderer.render(SAMPLE, template))) {
            final ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, DPI, ImageType.RGB), "png", png);
            return png.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not draw a preview of the " + template + " template", e);
        }
    }
}
