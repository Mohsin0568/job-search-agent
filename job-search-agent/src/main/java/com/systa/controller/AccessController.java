package com.systa.controller;

import com.systa.model.UserAccess.Status;
import com.systa.service.AccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access")
public class AccessController {

    private final AccessService accessService;

    public AccessController(final AccessService accessService) {
        this.accessService = accessService;
    }

    /**
     * Whether the signed-in user may use the app: APPROVED, PENDING (on the waitlist) or REVOKED. The first
     * call from a user without an invite puts them on the waitlist. Always 200, so the app can ask first.
     */
    @GetMapping
    public AccessResponse access(@AuthenticationPrincipal final Jwt jwt) {
        return new AccessResponse(accessService.statusOf(jwt.getSubject(), jwt.getTokenValue()),
                accessService.inviteOnly());
    }

    public record AccessResponse(Status status, boolean inviteOnly) {}
}
