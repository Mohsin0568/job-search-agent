package com.systa.model;

import java.util.List;

/**
 * What the user asked tailoring to fix from the job's resume review. An empty one - no keywords and no
 * red flags - asks for the resume to be tailored as is.
 */
public record TailoringFix(
        // Missing keywords the user ticked, confirming they really have them. Null is the same as none.
        List<String> keywords,
        // Whether to address the review's red flags.
        boolean redFlags
) {

    public static final TailoringFix AS_IS = new TailoringFix(List.of(), false);
}
