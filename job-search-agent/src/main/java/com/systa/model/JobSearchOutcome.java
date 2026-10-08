package com.systa.model;

/** How one user's search went: a batch is one LLM call covering a few job sources for one company. */
public record JobSearchOutcome(int batchesAttempted, int batchesFailed) {

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
