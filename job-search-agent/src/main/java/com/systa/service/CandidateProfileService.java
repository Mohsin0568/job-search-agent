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
     * Creates the user's profile on first save, otherwise replaces its editable fields. The email and full
     * name are refreshed from Cognito each time, since the access token carries neither and the client
     * can't be trusted to supply them; if Cognito has none, whatever is already stored is kept.
     *
     * @param accessToken the caller's Cognito access token, used to look up their verified email and name
     */
    public CandidateProfile save(final String userId, final String accessToken, final CandidateProfileDto details) {
        final CognitoUserService.CognitoUser cognitoUser = cognitoUserService.findUser(userId, accessToken);

        final Optional<CandidateProfile> existing = candidateProfileRepository.findByUserId(userId);
        final String id = existing.map(CandidateProfile::id).orElseGet(() -> UUID.randomUUID().toString());
        final String email = cognitoUser.verifiedEmail()
                .orElseGet(() -> existing.map(CandidateProfile::email).orElse(null));
        final String fullName = cognitoUser.fullName()
                .orElseGet(() -> existing.map(CandidateProfile::fullName).orElse(null));

        return candidateProfileRepository.save(new CandidateProfile(
                id,
                userId,
                email,
                fullName,
                details.desiredRole(),
                details.skills(),
                details.currentJobDescription(),
                details.companyPreferences(),
                details.recencyWindowDays()));
    }
}
