package com.systa.model;

/** The layouts a tailored resume can be laid out in. Each is an HTML template under resume_templates/. */
public enum ResumeTemplate {
    /** Serif, centred header, ruled section headings. */
    CLASSIC,
    /** Sans-serif, left-aligned header with a coloured accent. */
    MODERN,
    /** Sans-serif in smaller type with tight spacing, to keep a long resume short. */
    COMPACT,
    /** A grey masthead, with section headings set to the left of their content (from JSON Resume's "even"). */
    EVEN,
    /** Orange section labels, a tinted summary box and skill tags (from JSON Resume's "stackoverflow"). */
    STACK_OVERFLOW,
    /** A navy band, teal headings, shaded role headers and skill pills (from JSON Resume's "kendall"). */
    KENDALL;

    public static final ResumeTemplate DEFAULT = CLASSIC;
}
