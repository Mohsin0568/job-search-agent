package com.systa.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.systa.model.ResumeTemplate;
import com.systa.model.TailoredResumeContent;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lays a tailored resume out as a PDF in one of the {@link ResumeTemplate}s. Each template is an XHTML
 * page under resume_templates/, filled in by Thymeleaf and turned into a PDF by openhtmltopdf - so a
 * template's look is plain CSS, and its text stays selectable for applicant tracking systems.
 */
@Component
public class TailoredResumePdfRenderer {

    private static final String TEMPLATES = "resume_templates/";
    private static final int GSUB = ('G' << 24) | ('S' << 16) | ('U' << 8) | 'B';
    private static final String FONTS = "/resume_templates/fonts/";

    // The Unicode font PDFBox ships. Every template falls back to it for what its own typeface lacks
    // (Greek, Vietnamese, ...); templates name it last in their font-family.
    private static final Font FALLBACK = new Font("Liberation Sans", 400, FontStyle.NORMAL,
            "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");

    private static final List<Font> PT_SERIF = family("PT Serif", "ptserif/PT_Serif-Web-Regular.ttf",
            "ptserif/PT_Serif-Web-Bold.ttf", "ptserif/PT_Serif-Web-Italic.ttf");
    private static final List<Font> LATO = family("Lato", "lato/Lato-Regular.ttf", "lato/Lato-Bold.ttf",
            "lato/Lato-Italic.ttf");
    private static final List<Font> CARLITO = family("Carlito", "carlito/Carlito-Regular.ttf",
            "carlito/Carlito-Bold.ttf", "carlito/Carlito-Italic.ttf");
    private static final List<Font> ARIMO = family("Arimo", "arimo/Arimo-Regular.ttf", "arimo/Arimo-Bold.ttf",
            "arimo/Arimo-Italic.ttf");
    private static final List<Font> OPEN_SANS = family("Open Sans", "opensans/OpenSans-Regular.ttf",
            "opensans/OpenSans-Bold.ttf", "opensans/OpenSans-Italic.ttf");

    // The typeface each template names first in its font-family.
    private static final Map<ResumeTemplate, List<Font>> TYPEFACES = Map.of(
            ResumeTemplate.CLASSIC, PT_SERIF,
            ResumeTemplate.MODERN, LATO,
            ResumeTemplate.COMPACT, CARLITO,
            ResumeTemplate.EVEN, LATO,
            ResumeTemplate.STACK_OVERFLOW, ARIMO,
            ResumeTemplate.KENDALL, OPEN_SANS);

    private final TemplateEngine templateEngine;
    private final Map<String, byte[]> fontData;
    private final Map<ResumeTemplate, IntPredicate> drawable;

    public TailoredResumePdfRenderer() {
        final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATES);
        resolver.setSuffix(".html");
        // XML, not HTML: openhtmltopdf reads the page as XHTML, so the output must be well-formed.
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        // Distinct: templates may share a typeface.
        final List<Font> allFonts = Stream.concat(Stream.of(FALLBACK),
                TYPEFACES.values().stream().flatMap(List::stream)).distinct().toList();
        fontData = allFonts.stream().collect(Collectors.toUnmodifiableMap(Font::path,
                font -> withoutLigatures(read(font.path()))));

        final IntPredicate fallbackHasGlyph = hasGlyph(fontData.get(FALLBACK.path()));
        drawable = TYPEFACES.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> hasGlyph(fontData.get(entry.getValue().getFirst().path())).or(fallbackHasGlyph)));
    }

    public byte[] render(final TailoredResumeContent content, final ResumeTemplate template) {
        final Context context = new Context(Locale.UK);
        context.setVariable("resume", ResumeView.of(content, drawable.get(template)));
        final String page = templateEngine.process(template.name().toLowerCase(Locale.ROOT), context);

        final PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(page, null);
        for (final Font font : fontsFor(template)) {
            final byte[] data = fontData.get(font.path());
            builder.useFont(() -> new ByteArrayInputStream(data), font.family(), font.weight(), font.style(), true);
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.toStream(out);
        try {
            builder.run();
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not render the tailored resume as a PDF", e);
        }
        return out.toByteArray();
    }

    private static List<Font> fontsFor(final ResumeTemplate template) {
        return Stream.concat(TYPEFACES.get(template).stream(), Stream.of(FALLBACK)).toList();
    }

    private static List<Font> family(final String family, final String regular, final String bold,
                                     final String italic) {
        return List.of(new Font(family, 400, FontStyle.NORMAL, FONTS + regular),
                new Font(family, 700, FontStyle.NORMAL, FONTS + bold),
                new Font(family, 400, FontStyle.ITALIC, FONTS + italic));
    }

    private static byte[] read(final String path) {
        try (InputStream in = Objects.requireNonNull(TailoredResumePdfRenderer.class.getResourceAsStream(path),
                "Missing font " + path)) {
            return in.readAllBytes();
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read font " + path, e);
        }
    }

    /**
     * The font without its GSUB table. PDFBox applies a font's ligatures as it writes text ("ti", "ft", "tt"
     * become one glyph each in Lato and Carlito) but gives those glyphs no text, so the letters vanish when
     * the PDF is read back: "Software" comes out as "So ware" - to an applicant tracking system too.
     */
    static byte[] withoutLigatures(final byte[] font) {
        final ByteBuffer in = ByteBuffer.wrap(font);
        final int numTables = Short.toUnsignedInt(in.getShort(4));
        final List<int[]> kept = new ArrayList<>(); // {tag, checksum, offset, length}
        for (int i = 0; i < numTables; i++) {
            final int record = 12 + 16 * i;
            final int tag = in.getInt(record);
            if (tag != GSUB) {
                kept.add(new int[]{tag, in.getInt(record + 4), in.getInt(record + 8), in.getInt(record + 12)});
            }
        }
        if (kept.size() == numTables) {
            return font;
        }

        // Header and table records as before, less GSUB's; then each table's data, padded to four bytes.
        final int headerSize = 12 + 16 * kept.size();
        int size = headerSize;
        for (final int[] table : kept) {
            size += padded(table[3]);
        }
        final ByteBuffer out = ByteBuffer.allocate(size);
        final int entrySelector = 31 - Integer.numberOfLeadingZeros(kept.size());
        final int searchRange = 16 << entrySelector;
        out.putInt(in.getInt(0)).putShort((short) kept.size()).putShort((short) searchRange)
                .putShort((short) entrySelector).putShort((short) (kept.size() * 16 - searchRange));
        int offset = headerSize;
        for (final int[] table : kept) {
            out.putInt(table[0]).putInt(table[1]).putInt(offset).putInt(table[3]);
            out.put(offset, font, table[2], table[3]);
            offset += padded(table[3]);
        }
        return out.array();
    }

    private static int padded(final int length) {
        return (length + 3) & ~3;
    }

    private static IntPredicate hasGlyph(final byte[] fontData) {
        try {
            // Kept open: the lookup reads from the font. It is in memory and lives as long as the app.
            final TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(fontData));
            final var cmap = font.getUnicodeCmapLookup();
            return codePoint -> cmap.getGlyphId(codePoint) > 0;
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read a resume font", e);
        }
    }

    private record Font(String family, int weight, FontStyle style, String path) {}
}
