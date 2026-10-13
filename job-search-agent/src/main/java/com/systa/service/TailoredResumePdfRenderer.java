package com.systa.service;

import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoredResumeContent.Education;
import com.systa.model.TailoredResumeContent.Experience;
import com.systa.model.TailoredResumeContent.Section;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lays a tailored resume out as a PDF: one fixed, single-column template for every user. */
@Component
public class TailoredResumePdfRenderer {

    // The Unicode font PDFBox ships as its own fallback. Embedding it (rather than using a built-in
    // PDF font such as Helvetica) is what lets names like "Łukasz" or "Nguyễn" be written at all.
    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 50;
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;

    private static final float NAME_SIZE = 20;
    private static final float HEADLINE_SIZE = 12;
    private static final float HEADING_SIZE = 10.5f;
    private static final float BODY_SIZE = 10;
    private static final float SMALL_SIZE = 9;
    private static final float LINE_SPACING = 1.35f;
    private static final float BULLET_INDENT = 12;

    private static final Color TEXT = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x55, 0x60, 0x70);
    private static final Color RULE = new Color(0xB8, 0xC0, 0xCC);

    public byte[] render(final TailoredResumeContent content) {
        try (PDDocument document = new PDDocument(); InputStream fontStream = fontStream()) {
            final PDFont font = PDType0Font.load(document, fontStream);
            final Page page = new Page(document, font);

            header(page, content);
            paragraphSection(page, "Summary", content.summary());
            paragraphSection(page, "Skills", String.join("  ·  ", clean(content.skills())));
            experience(page, content.experience());
            education(page, content.education());
            bulletSection(page, "Certifications", content.certifications());
            for (final Section section : orEmpty(content.additionalSections())) {
                if (section != null) {
                    bulletSection(page, section.heading(), section.items());
                }
            }

            page.close();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not render the tailored resume as a PDF", e);
        }
    }

    private static InputStream fontStream() {
        return Objects.requireNonNull(TailoredResumePdfRenderer.class.getResourceAsStream(FONT_RESOURCE),
                "PDFBox no longer ships " + FONT_RESOURCE);
    }

    private static void header(final Page page, final TailoredResumeContent content) throws IOException {
        page.text(content.name(), NAME_SIZE, TEXT, true, 0);
        page.text(content.headline(), HEADLINE_SIZE, MUTED, false, 0);
        page.text(String.join("  |  ", clean(content.contact())), SMALL_SIZE, MUTED, false, 0);
    }

    private static void paragraphSection(final Page page, final String heading, final String body) throws IOException {
        if (isBlank(body)) {
            return;
        }
        page.heading(heading);
        page.text(body, BODY_SIZE, TEXT, false, 0);
    }

    private static void bulletSection(final Page page, final String heading, final List<String> items)
            throws IOException {
        final List<String> bullets = clean(items);
        if (bullets.isEmpty() || isBlank(heading)) {
            return;
        }
        page.heading(heading);
        for (final String bullet : bullets) {
            page.bullet(bullet);
        }
    }

    private static void experience(final Page page, final List<Experience> experience) throws IOException {
        final List<Experience> roles = orEmpty(experience).stream().filter(Objects::nonNull).toList();
        if (roles.isEmpty()) {
            return;
        }
        page.heading("Experience");
        for (final Experience role : roles) {
            page.gap(3);
            page.keepTogether(3);
            page.titleWithDates(joined(" — ", role.title(), role.company()), role.dates());
            page.text(role.location(), SMALL_SIZE, MUTED, false, 0);
            for (final String bullet : clean(role.bullets())) {
                page.bullet(bullet);
            }
        }
    }

    private static void education(final Page page, final List<Education> education) throws IOException {
        final List<Education> entries = orEmpty(education).stream().filter(Objects::nonNull).toList();
        if (entries.isEmpty()) {
            return;
        }
        page.heading("Education");
        for (final Education entry : entries) {
            page.gap(3);
            page.keepTogether(2);
            page.titleWithDates(joined(" — ", entry.qualification(), entry.institution()), entry.dates());
            page.text(entry.details(), BODY_SIZE, TEXT, false, 0);
        }
    }

    private static String joined(final String separator, final String... parts) {
        return String.join(separator, clean(List.of(parts == null ? new String[0] : nonNull(parts))));
    }

    private static String[] nonNull(final String[] parts) {
        final String[] copy = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            copy[i] = parts[i] == null ? "" : parts[i];
        }
        return copy;
    }

    private static List<String> clean(final List<String> values) {
        return orEmpty(values).stream()
                .filter(value -> !isBlank(value))
                .map(String::strip)
                .toList();
    }

    private static <T> List<T> orEmpty(final List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    /** The page being written: where the next line goes, and a new page when this one is full. */
    private static final class Page {
        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float y;

        private Page(final PDDocument document, final PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            close();
            final PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private void gap(final float points) {
            y -= points;
        }

        /** Starts a new page unless there is room for this many body lines, so a title isn't left alone. */
        private void keepTogether(final int bodyLines) throws IOException {
            if (y - bodyLines * BODY_SIZE * LINE_SPACING < MARGIN) {
                newPage();
            }
        }

        private void heading(final String heading) throws IOException {
            gap(9);
            keepTogether(3);
            text(heading.strip().toUpperCase(), HEADING_SIZE, TEXT, true, 0);
            stream.setStrokingColor(RULE);
            stream.setLineWidth(0.6f);
            stream.moveTo(MARGIN, y + 3);
            stream.lineTo(MARGIN + CONTENT_WIDTH, y + 3);
            stream.stroke();
            gap(4);
        }

        private void bullet(final String bullet) throws IOException {
            final float lineHeight = BODY_SIZE * LINE_SPACING;
            if (y - lineHeight < MARGIN) {
                newPage();
            }
            line("•", MARGIN + 2, y - BODY_SIZE, BODY_SIZE, TEXT, false);
            text(bullet, BODY_SIZE, TEXT, false, BULLET_INDENT);
        }

        /** A bold title on the left with its dates on the right of the same line, when both fit. */
        private void titleWithDates(final String title, final String dates) throws IOException {
            final String safeDates = isBlank(dates) ? "" : writable(dates.strip());
            final float datesWidth = width(safeDates, SMALL_SIZE);
            final float titleWidth = CONTENT_WIDTH - (safeDates.isEmpty() ? 0 : datesWidth + 12);

            final float firstLineY = y - BODY_SIZE;
            final boolean movedToNewPage = wrapped(title, BODY_SIZE, TEXT, true, 0, titleWidth);
            if (!safeDates.isEmpty()) {
                final float datesY = movedToNewPage ? PAGE_SIZE.getHeight() - MARGIN - BODY_SIZE : firstLineY;
                line(safeDates, MARGIN + CONTENT_WIDTH - datesWidth, datesY, SMALL_SIZE, MUTED, false);
            }
        }

        private void text(final String text, final float size, final Color color, final boolean bold,
                           final float indent) throws IOException {
            wrapped(text, size, color, bold, indent, CONTENT_WIDTH - indent);
        }

        /** Writes the text wrapped to the width; returns whether its first line started a new page. */
        private boolean wrapped(final String text, final float size, final Color color, final boolean bold,
                                 final float indent, final float maxWidth) throws IOException {
            if (isBlank(text)) {
                return false;
            }
            final float lineHeight = size * LINE_SPACING;
            boolean firstLine = true;
            boolean firstLineOnNewPage = false;
            for (final String line : wrap(writable(text.strip()), size, maxWidth)) {
                if (y - lineHeight < MARGIN) {
                    newPage();
                    firstLineOnNewPage = firstLineOnNewPage || firstLine;
                }
                line(line, MARGIN + indent, y - size, size, color, bold);
                y -= lineHeight;
                firstLine = false;
            }
            return firstLineOnNewPage;
        }

        private void line(final String line, final float x, final float baseline, final float size, final Color color,
                           final boolean bold) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            if (bold) {
                // The bundled font has no bold cut: thicken the regular one by stroking its outline.
                stream.setRenderingMode(RenderingMode.FILL_STROKE);
                stream.setStrokingColor(color);
                stream.setLineWidth(size * 0.03f);
            } else {
                stream.setRenderingMode(RenderingMode.FILL);
            }
            stream.newLineAtOffset(x, baseline);
            stream.showText(line);
            stream.endText();
        }

        private List<String> wrap(final String text, final float size, final float maxWidth) throws IOException {
            final List<String> lines = new ArrayList<>();
            // A line break in the text is kept; each paragraph is wrapped on its own.
            for (final String paragraph : text.split("\\R")) {
                StringBuilder current = new StringBuilder();
                for (final String word : paragraph.strip().split("\\s+")) {
                    for (final String piece : breakLongWord(word, size, maxWidth)) {
                        final String candidate = current.isEmpty() ? piece : current + " " + piece;
                        if (width(candidate, size) <= maxWidth || current.isEmpty()) {
                            current = new StringBuilder(candidate);
                        } else {
                            lines.add(current.toString());
                            current = new StringBuilder(piece);
                        }
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines;
        }

        // A word wider than the line (a long URL, say) is cut into pieces that fit.
        private List<String> breakLongWord(final String word, final float size, final float maxWidth)
                throws IOException {
            if (width(word, size) <= maxWidth) {
                return List.of(word);
            }
            final List<String> pieces = new ArrayList<>();
            StringBuilder piece = new StringBuilder();
            for (int offset = 0; offset < word.length(); ) {
                final int codePoint = word.codePointAt(offset);
                final String next = new String(Character.toChars(codePoint));
                if (!piece.isEmpty() && width(piece + next, size) > maxWidth) {
                    pieces.add(piece.toString());
                    piece = new StringBuilder();
                }
                piece.append(next);
                offset += Character.charCount(codePoint);
            }
            pieces.add(piece.toString());
            return pieces;
        }

        private float width(final String text, final float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        // The font can't draw everything (emoji, Chinese, ...). What it can't draw is left out, with
        // control characters, rather than failing the whole resume.
        private String writable(final String text) {
            final StringBuilder writable = new StringBuilder(text.length());
            text.codePoints().forEach(codePoint -> {
                if (codePoint == '\n' || codePoint == '\r') {
                    writable.append('\n');
                } else if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    writable.append(' ');
                } else if (!Character.isISOControl(codePoint) && canDraw(codePoint)) {
                    writable.appendCodePoint(codePoint);
                }
            });
            return writable.toString();
        }

        private boolean canDraw(final int codePoint) {
            try {
                font.encode(new String(Character.toChars(codePoint)));
                return true;
            } catch (final IOException | IllegalArgumentException | IllegalStateException e) {
                return false;
            }
        }
    }
}
