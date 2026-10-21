package com.systa.service;

import com.systa.model.LlmUsage;
import com.systa.repository.LlmUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Saves what each LLM call used. */
@Service
@Slf4j
public class LlmUsageService {

    private final LlmUsageRepository llmUsageRepository;

    public LlmUsageService(final LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    // Usage is a record of the call, not part of it: failing to save it must not fail the call.
    public void record(final LlmUsage usage) {
        try {
            llmUsageRepository.save(usage);
        } catch (final Exception e) {
            log.error("Could not record LLM usage - userId={}, operation={}, totalTokens={}",
                    usage.userId(), usage.operation(), usage.totalTokens(), e);
        }
    }
}
