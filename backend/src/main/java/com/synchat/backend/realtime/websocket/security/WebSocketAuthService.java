package com.synchat.backend.realtime.websocket.security;

import com.synchat.backend.auth.service.AccessTokenService;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import com.synchat.backend.common.security.SessionValidator;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WebSocketAuthService {

    private final AccessTokenService accessTokenService;
    private final SessionValidator sessionValidator;

    public WebSocketAuthService(AccessTokenService accessTokenService, SessionValidator sessionValidator) {
        this.accessTokenService = accessTokenService;
        this.sessionValidator = sessionValidator;
    }

    public AuthPrincipal authenticate(String accessToken, UUID requestedDeviceId) {
        AuthPrincipal principal = accessTokenService.parseAndValidate(accessToken);
        sessionValidator.validateActiveSession(principal);

        if (requestedDeviceId != null && !requestedDeviceId.equals(principal.deviceId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized.");
        }

        return principal;
    }
}
