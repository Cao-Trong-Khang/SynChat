package com.synchat.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.auth.service.AccessTokenService;
import com.synchat.backend.common.dto.ApiError;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;
    private final SessionValidator sessionValidator;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            AccessTokenService accessTokenService,
            SessionValidator sessionValidator,
            ObjectMapper objectMapper
    ) {
        this.accessTokenService = accessTokenService;
        this.sessionValidator = sessionValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String accessToken = authorization.substring(BEARER_PREFIX.length());
            AuthPrincipal principal = accessTokenService.parseAndValidate(accessToken);
            sessionValidator.validateActiveSession(principal);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (AppException exception) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, exception);
        }
    }

    private void writeErrorResponse(HttpServletResponse response, AppException exception) throws IOException {
        HttpStatus status = exception.getCode() == ErrorCode.UNAUTHORIZED
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(new ApiError(exception.getCode().name(), exception.getMessage()))
        );
    }

    private record ErrorResponse(ApiError error) {
    }
}
