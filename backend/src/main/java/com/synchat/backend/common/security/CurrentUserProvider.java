package com.synchat.backend.common.security;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }

        if (!(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw unauthorized();
        }

        return new AuthenticatedUser(principal.userId(), principal.sessionId(), principal.deviceId());
    }

    private AppException unauthorized() {
        return new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized.");
    }
}
