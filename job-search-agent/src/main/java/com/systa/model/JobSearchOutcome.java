package com.systa.model;

import java.util.List;

/**
 * How one user's search went: a batch is one LLM call covering a few job sources for one company.
 * {@code newJobResultIds} are the UserJobSearchResult records the search saved, in the order it saved them.
 */
public record JobSearchOutcome(int batchesAttempted, int batchesFailed, List<String> newJobResultIds) {

    public static final JobSearchOutcome NOTHING_SEARCHED = new JobSearchOutcome(0, 0, List.of());

    public boolean nothingToSearch() {
        return batchesAttempted == 0;
    }

    public boolean allBatchesFailed() {
        return batchesAttempted > 0 && batchesFailed == batchesAttempted;
    }

    public boolean someBatchesFailed() {
        return batchesFailed > 0;
    }
}
