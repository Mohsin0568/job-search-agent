package com.systa.service;

import com.systa.model.CandidateProfile;
import com.systa.model.CandidateProfileDto;
import com.systa.repository.CandidateProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;

    public CandidateProfileService(final CandidateProfileRepository candidateProfileRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
    }

    public Optional<CandidateProfile> findByUserId(final String userId) {
        return candidateProfileRepository.findByUserId(userId);
    }

    /** Creates the user's profile on first save, otherwise replaces its editable fields. */
    public CandidateProfile save(final String userId, final CandidateProfileDto details) {
        final String id = candidateProfileRepository.findByUserId(userId)
                .map(CandidateProfile::id)
                .orElseGet(() -> UUID.randomUUID().toString());

        return candidateProfileRepository.save(new CandidateProfile(
                id,
                userId,
                details.desiredRole(),
                details.skills(),
                details.currentJobDescription(),
                details.companyPreferences(),
                details.recencyWindowDays()));
    }
}
