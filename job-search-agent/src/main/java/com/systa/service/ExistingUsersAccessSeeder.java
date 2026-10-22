package com.systa.service;

import com.systa.config.AccessProperties;
import com.systa.model.CandidateProfile;
import com.systa.model.UserAccess;
import com.systa.repository.CandidateProfileRepository;
import com.systa.repository.UserAccessRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * Lets in everyone who already has a profile, so turning invite-only on doesn't shut out existing users.
 * Runs on every start, in both modes and before the daily job, but only adds what's missing: a user who
 * already has an access record - waitlisted or revoked - is left as they are.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ExistingUsersAccessSeeder implements ApplicationRunner {

    private final CandidateProfileRepository candidateProfileRepository;
    private final UserAccessRepository userAccessRepository;
    private final AccessProperties properties;
    private final Clock clock;

    @Autowired
    public ExistingUsersAccessSeeder(final CandidateProfileRepository candidateProfileRepository,
                                     final UserAccessRepository userAccessRepository,
                                     final AccessProperties properties) {
        this(candidateProfileRepository, userAccessRepository, properties, Clock.systemUTC());
    }

    ExistingUsersAccessSeeder(final CandidateProfileRepository candidateProfileRepository,
                              final UserAccessRepository userAccessRepository, final AccessProperties properties,
                              final Clock clock) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.userAccessRepository = userAccessRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(final ApplicationArguments args) {
        if (!properties.inviteOnly()) {
            return;
        }
        int approved = 0;
        for (final CandidateProfile profile : candidateProfileRepository.findAll()) {
            if (approveIfNew(profile)) {
                approved++;
            }
        }
        if (approved > 0) {
            log.info("Let in {} existing users who had no access record", approved);
        }
    }

    private boolean approveIfNew(final CandidateProfile profile) {
        if (userAccessRepository.findByUserId(profile.userId()).isPresent()) {
            return false;
        }
        if (profile.email() == null) {
            log.warn("Existing user has no email to record access under; they'll join the waitlist when they "
                    + "next sign in - userId={}", profile.userId());
            return false;
        }
        final String email = UserAccess.idFor(profile.email());
        final Optional<UserAccess> forEmail = userAccessRepository.findById(email);
        if (forEmail.isPresent() && forEmail.get().userId() != null) {
            // Another account signed in with this email; leave that record alone.
            return false;
        }
        // An invite for this email is taken up as it stands; otherwise the existing user is let in.
        final UserAccess.Status status = forEmail.map(UserAccess::status).orElse(UserAccess.Status.APPROVED);
        userAccessRepository.save(new UserAccess(email, profile.userId(), status, clock.instant()));
        return status == UserAccess.Status.APPROVED;
    }
}
