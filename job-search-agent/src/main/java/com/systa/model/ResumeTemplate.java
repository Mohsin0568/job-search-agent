package com.systa.model;

/** The layouts a tailored resume can be laid out in. Each is an HTML template under resume_templates/. */
public enum ResumeTemplate {
    /** Serif, centred header, ruled section headings. */
    CLASSIC,
    /** Sans-serif, left-aligned header with a coloured accent. */
    MODERN,
    /** Sans-serif in smaller type with tight spacing, to keep a long resume short. */
    COMPACT;

    public static final ResumeTemplate DEFAULT = CLASSIC;
}
