package com.systa.service;

import com.systa.model.CandidateProfile;
import com.systa.model.CandidateProfileDto;
import com.systa.repository.CandidateProfileRepository;
import com.systa.security.CognitoUserService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final CognitoUserService cognitoUserService;

    public CandidateProfileService(final CandidateProfileRepository candidateProfileRepository,
                                   final CognitoUserService cognitoUserService) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.cognitoUserService = cognitoUserService;
    }

    public Optional<CandidateProfile> findByUserId(final String userId) {
        return candidateProfileRepository.findByUserId(userId);
    }

    /**
     * Creates the user's profile on first save, otherwise replaces its editable fields. The email is
     * refreshed from Cognito each time, since the access token has no email claim and the client can't be
     * trusted to supply it; if Cognito has no verified email, any email already stored is kept.
     *
     * @param accessToken the caller's Cognito access token, used to look up their verified email
     */
    public CandidateProfile save(final String userId, final String accessToken, final CandidateProfileDto details) {
        final String verifiedEmail = cognitoUserService.findVerifiedEmail(userId, accessToken).orElse(null);

        final Optional<CandidateProfile> existing = candidateProfileRepository.findByUserId(userId);
        final String id = existing.map(CandidateProfile::id).orElseGet(() -> UUID.randomUUID().toString());
        final String email = verifiedEmail != null ? verifiedEmail : existing.map(CandidateProfile::email).orElse(null);

        return candidateProfileRepository.save(new CandidateProfile(
                id,
                userId,
                email,
                details.desiredRole(),
                details.skills(),
                details.currentJobDescription(),
                details.companyPreferences(),
                details.recencyWindowDays()));
    }
}
