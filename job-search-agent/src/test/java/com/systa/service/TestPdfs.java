package com.systa.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Small PDFs built in memory, so the resume tests need no fixture files. */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] withText(final String text) {
        try (PDDocument document = new PDDocument()) {
            final PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            return bytesOf(document);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A valid PDF with a page but no text on it, like a scanned document. */
    public static byte[] withoutText() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return bytesOf(document);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytesOf(final PDDocument document) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
