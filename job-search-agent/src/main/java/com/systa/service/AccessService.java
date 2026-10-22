package com.systa.service;

import com.systa.config.AccessProperties;
import com.systa.model.UserAccess;
import com.systa.model.UserAccess.Status;
import com.systa.repository.UserAccessRepository;
import com.systa.security.CognitoUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Decides who may use the app while it is invite-only: users whose verified email has been invited or let
 * in from the waitlist. Anyone else who signs in is put on the waitlist. With invite-only off, everyone may.
 */
@Service
@Slf4j
public class AccessService {

    private final UserAccessRepository userAccessRepository;
    private final CognitoUserService cognitoUserService;
    private final AccessProperties properties;
    private final Clock clock;

    @Autowired
    public AccessService(final UserAccessRepository userAccessRepository, final CognitoUserService cognitoUserService,
                         final AccessProperties properties) {
        this(userAccessRepository, cognitoUserService, properties, Clock.systemUTC());
    }

    AccessService(final UserAccessRepository userAccessRepository, final CognitoUserService cognitoUserService,
                  final AccessProperties properties, final Clock clock) {
        this.userAccessRepository = userAccessRepository;
        this.cognitoUserService = cognitoUserService;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean inviteOnly() {
        return properties.inviteOnly();
    }

    /**
     * Where the signed-in user stands. Once a user is tied to an email this is one lookup by user id; the
     * first time, their verified email is read from Cognito and matched to an invite, or put on the waitlist.
     *
     * @param accessToken the user's Cognito access token, to read their verified email the first time
     */
    public Status statusOf(final String userId, final String accessToken) {
        if (!inviteOnly()) {
            return Status.APPROVED;
        }
        final Optional<UserAccess> known = userAccessRepository.findByUserId(userId);
        if (known.isPresent()) {
            return statusOf(known.get());
        }

        final Optional<String> verifiedEmail = cognitoUserService.findUser(userId, accessToken).verifiedEmail();
        if (verifiedEmail.isEmpty()) {
            // Nothing to match an invite to, or to put on the waitlist under.
            log.warn("Signed in without a verified email, not letting in - userId={}", userId);
            return Status.PENDING;
        }

        final String email = UserAccess.idFor(verifiedEmail.get());
        final Optional<UserAccess> invite = userAccessRepository.findById(email);
        // An email that moved to a new Cognito account (deleted and registered again) keeps its access.
        final UserAccess access = new UserAccess(email, userId,
                invite.map(UserAccess::status).orElse(Status.PENDING), clock.instant());
        userAccessRepository.save(access);
        if (invite.isEmpty()) {
            log.info("Signed in without an invite, added to the waitlist - userId={}, email={}", userId, email);
        } else {
            log.info("Signed in with an invite - userId={}, email={}, status={}", userId, email, access.status());
        }
        return statusOf(access);
    }

    /** Whether the daily job may search for this user. Never calls Cognito: unknown users are left out. */
    public boolean mayUseApp(final String userId) {
        return !inviteOnly() || userAccessRepository.findByUserId(userId).map(UserAccess::approved).orElse(false);
    }

    // Hand-edited documents may leave the status out; read that as not let in yet.
    private static Status statusOf(final UserAccess access) {
        return access.status() != null ? access.status() : Status.PENDING;
    }
}
