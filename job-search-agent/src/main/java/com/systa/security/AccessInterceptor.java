package com.systa.security;

import com.systa.exception.AccessNotGrantedException;
import com.systa.model.UserAccess.Status;
import com.systa.service.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Turns away signed-in users who aren't let in while the app is invite-only, before any controller runs -
 * so nothing that costs money (LLM calls, scraping) can start for them. Requests Spring Security already
 * let through without a sign-in, such as template previews, are left alone.
 */
public class AccessInterceptor implements HandlerInterceptor {

    private final AccessService accessService;

    public AccessInterceptor(final AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
                             final Object handler) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }
        final Status status = accessService.statusOf(jwt.getSubject(), jwt.getTokenValue());
        if (status != Status.APPROVED) {
            throw new AccessNotGrantedException(jwt.getSubject(), status);
        }
        return true;
    }
}
