package com.systa.service;

import com.systa.exception.InvalidResumeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Checks that an uploaded file is a readable PDF and returns its text. */
@Component
public class ResumeTextExtractor {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public String extractText(final byte[] content) {
        if (!startsWithPdfSignature(content)) {
            throw new InvalidResumeException("resume must be a PDF file");
        }

        final String text;
        try (PDDocument document = Loader.loadPDF(content)) {
            text = new PDFTextStripper().getText(document).strip();
        } catch (final IOException e) {
            // Covers damaged files and password-protected ones alike.
            throw new InvalidResumeException("we couldn't open this PDF - check it isn't damaged or password-protected", e);
        }

        // Nothing to tailor a resume from: typically a scan, which is pictures of text.
        if (text.isEmpty()) {
            throw new InvalidResumeException(
                    "we couldn't read any text from this PDF - if it's a scan, upload a text-based PDF instead");
        }
        return text;
    }

    private static boolean startsWithPdfSignature(final byte[] content) {
        if (content.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}
